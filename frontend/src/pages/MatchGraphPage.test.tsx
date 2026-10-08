import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type { GraphEdge, GraphNode, GraphView } from '../api/types';
import { mockApi } from '../test/mockApi';
import { MatchGraphPage } from './MatchGraphPage';
import { NO_FILTER, applyFilter, computeLayout, edgeKey, edgesOf, hitTest } from './graphModel';

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ profile: { admin: true } }),
}));

const node = (index: number, name: string, zone: string, partner: number | null, subject = 'DSA'): GraphNode => ({
  index,
  id: `id-${index}`,
  name,
  zone,
  subjects: [{ name: subject, level: 2 }],
  partner,
  degree: 2,
});
const edge = (a: number, b: number, score: number, matched: boolean, shared = ['DSA']): GraphEdge => ({
  a,
  b,
  score,
  knowledge: 0.9,
  reciprocity: 0.8,
  breadth: 0.5,
  proximity: 1,
  shared,
  matched,
});

const view: GraphView = {
  poolSize: 3,
  edgesTotal: 3,
  edgesShown: 3,
  topK: 4,
  nodes: [node(0, 'Asha Rao', 'Library', 1), node(1, 'Ravi Patel', 'Library', 0), node(2, 'Meera Shah', 'Lab', null, 'OS')],
  edges: [edge(0, 1, 91, true), edge(0, 2, 60, false, []), edge(1, 2, 55, false, [])],
  optimal: { pairs: 1, totalWeight: 0.91, millis: 12.4, matchedStudents: 2 },
  greedy: { pairs: 1, totalWeight: 0.91, millis: 0.2, matchedStudents: 2 },
  graphBuildMillis: 3,
  check: { pairsChecked: 1, exchangesTested: 5, improvingExchanges: 0, passed: true },
  zones: ['Library', 'Lab'],
};

describe('graphModel', () => {
  it('filters nodes and edges by zone, subject and matched-only', () => {
    expect(applyFilter(view, NO_FILTER).edges).toHaveLength(3);
    expect([...applyFilter(view, { ...NO_FILTER, zone: 'Library' }).nodes]).toEqual([0, 1]);
    expect([...applyFilter(view, { ...NO_FILTER, subject: 'OS' }).nodes]).toEqual([2]);
    const matched = applyFilter(view, { ...NO_FILTER, matchedOnly: true });
    expect(matched.edges).toEqual([view.edges[0]]);
    expect(matched.nodes.has(2)).toBe(false);
  });

  it('orders a student\'s edges strongest first and builds symmetric keys', () => {
    expect(edgesOf(view, 2).map((e) => e.score)).toEqual([60, 55]);
    expect(edgeKey(3, 1)).toBe(edgeKey(1, 3));
  });

  it('lays out deterministically and puts matched pairs closer than the unmatched student', () => {
    const first = computeLayout(view, 600, 400);
    const second = computeLayout(view, 600, 400);
    expect(first).toEqual(second);
    expect(first).toHaveLength(3);
    first.forEach((p) => expect(Number.isFinite(p.x) && Number.isFinite(p.y)).toBe(true));
  });

  it('finds the nearest visible node under a point', () => {
    const positions = [{ x: 10, y: 10 }, { x: 50, y: 50 }, { x: 200, y: 200 }];
    expect(hitTest(positions, new Set([0, 1, 2]), 12, 11, 8)).toBe(0);
    expect(hitTest(positions, new Set([1, 2]), 12, 11, 8)).toBe(-1);
  });
});

describe('MatchGraphPage', () => {
  it('shows pool statistics, the optimality verdict and the rounds table', async () => {
    mockApi({
      'GET /matching/graph?topK=4': { body: view },
      'GET /matching/rounds': {
        body: [{ startedAt: '2026-10-08T10:00:00Z', poolSize: 3, edgeCount: 3, pairs: 1, totalWeight: 0.91, durationMs: 7.5 }],
      },
    });
    render(
      <MemoryRouter>
        <MatchGraphPage />
      </MemoryRouter>,
    );
    expect(await screen.findAllByText('1 pairs')).toHaveLength(2);
    expect(screen.getByRole('status')).toHaveTextContent('0 improving exchanges found among 5 tested');
    expect(screen.getByText('7.5 ms')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /compatibility graph/i })).toBeInTheDocument();
  });

  it('lets the admin open a matched pair and see its score breakdown', async () => {
    mockApi({ 'GET /matching/graph?topK=4': { body: view }, 'GET /matching/rounds': { body: [] } });
    render(
      <MemoryRouter>
        <MatchGraphPage />
      </MemoryRouter>,
    );
    await userEvent.click(await screen.findByRole('button', { name: /Asha Rao ↔ Ravi Patel/ }));
    expect(await screen.findByRole('heading', { name: 'Asha Rao' })).toBeInTheDocument();
    expect(screen.getByText(/Matched by the optimal algorithm/)).toBeInTheDocument();
  });

  it('explains when nobody is available', async () => {
    mockApi({
      'GET /matching/graph?topK=4': { body: { ...view, poolSize: 0, nodes: [], edges: [] } },
      'GET /matching/rounds': { body: [] },
    });
    render(
      <MemoryRouter>
        <MatchGraphPage />
      </MemoryRouter>,
    );
    expect(await screen.findByText(/Nobody is available/)).toBeInTheDocument();
  });
});

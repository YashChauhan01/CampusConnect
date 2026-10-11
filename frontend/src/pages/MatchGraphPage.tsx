import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { matchGraph } from '../api/endpoints';
import type { GraphEdge, GraphView } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Alert, EmptyState, PageHeader, ScoreBadge, ScoreBreakdown, Spinner } from '../components/ui';
import { useLoad } from '../hooks';
import { cssVar, useTheme } from '../theme';
import {
  NO_FILTER,
  allSubjects,
  applyFilter,
  computeLayout,
  edgeKey,
  edgesOf,
  hitTest,
  otherEnd,
  zoneColor,
  type Filter,
  type Position,
} from './graphModel';

const WIDTH = 960;
const HEIGHT = 600;
const NODE_RADIUS = 5;
const LEVELS = ['', 'Beginner', 'Intermediate', 'Advanced'];

type Mode = 'graph' | 'matching';

interface ViewTransform {
  k: number;
  x: number;
  y: number;
}

interface CanvasProps {
  view: GraphView;
  positions: Position[];
  filter: Filter;
  mode: Mode;
  selected: number | null;
  onSelect: (index: number | null) => void;
}

/** Canvas rendering of the compatibility graph with pan (drag), zoom (wheel) and hover/click selection. */
function GraphCanvas({ view, positions, filter, mode, selected, onSelect }: CanvasProps) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const [transform, setTransform] = useState<ViewTransform>({ k: 1, x: 0, y: 0 });
  const [hover, setHover] = useState<number | null>(null);
  const drag = useRef<{ x: number; y: number; moved: number } | null>(null);
  const visible = useMemo(() => applyFilter(view, filter), [view, filter]);
  const { theme } = useTheme();

  const toGraph = useCallback(
    (clientX: number, clientY: number) => {
      const rect = canvas.current!.getBoundingClientRect();
      const sx = ((clientX - rect.left) / rect.width) * WIDTH;
      const sy = ((clientY - rect.top) / rect.height) * HEIGHT;
      return { x: (sx - transform.x) / transform.k, y: (sy - transform.y) / transform.k };
    },
    [transform],
  );

  useEffect(() => {
    const el = canvas.current;
    const ctx = el?.getContext('2d');
    if (!el || !ctx) return;
    const dpr = window.devicePixelRatio || 1;
    el.width = WIDTH * dpr;
    el.height = HEIGHT * dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, WIDTH, HEIGHT);
    ctx.translate(transform.x, transform.y);
    ctx.scale(transform.k, transform.k);
    const colors = {
      matched: cssVar('--graph-matched'),
      focusEdge: cssVar('--graph-focus-edge'),
      edge: cssVar('--graph-edge'),
      hollow: cssVar('--graph-node-hollow'),
      ring: cssVar('--graph-focus-ring'),
    };

    const focus = selected ?? hover;
    for (const e of visible.edges) {
      const a = positions[e.a];
      const b = positions[e.b];
      if (!a || !b) continue;
      const touchesFocus = focus !== null && (e.a === focus || e.b === focus);
      ctx.beginPath();
      ctx.moveTo(a.x, a.y);
      ctx.lineTo(b.x, b.y);
      if (e.matched) {
        ctx.strokeStyle = colors.matched;
        ctx.globalAlpha = 1;
        ctx.lineWidth = 3.2 / Math.sqrt(transform.k);
      } else if (touchesFocus) {
        ctx.strokeStyle = colors.focusEdge;
        ctx.globalAlpha = 0.9;
        ctx.lineWidth = 1.8 / Math.sqrt(transform.k);
      } else {
        ctx.strokeStyle = colors.edge;
        ctx.globalAlpha = mode === 'graph' ? 0.22 : 0.05;
        ctx.lineWidth = (0.5 + e.score / 100) / Math.sqrt(transform.k);
      }
      ctx.stroke();
    }
    ctx.globalAlpha = 1;
    for (const n of view.nodes) {
      if (!visible.nodes.has(n.index)) continue;
      const p = positions[n.index];
      if (!p) continue;
      const color = cssVar(zoneColor(n.zone, view.zones));
      ctx.beginPath();
      ctx.arc(p.x, p.y, NODE_RADIUS / Math.sqrt(transform.k), 0, Math.PI * 2);
      if (n.partner !== null) {
        ctx.fillStyle = color;
        ctx.fill();
      } else {
        ctx.fillStyle = colors.hollow;
        ctx.fill();
        ctx.strokeStyle = color;
        ctx.lineWidth = 1.6 / Math.sqrt(transform.k);
        ctx.stroke();
      }
      if (n.index === selected || n.index === hover) {
        ctx.beginPath();
        ctx.arc(p.x, p.y, (NODE_RADIUS + 4) / Math.sqrt(transform.k), 0, Math.PI * 2);
        ctx.strokeStyle = colors.ring;
        ctx.lineWidth = 2 / Math.sqrt(transform.k);
        ctx.stroke();
      }
    }
  }, [view, positions, visible, mode, selected, hover, transform, theme]);

  useEffect(() => {
    const el = canvas.current;
    if (!el) return;
    const wheel = (event: WheelEvent) => {
      event.preventDefault();
      const rect = el.getBoundingClientRect();
      const sx = ((event.clientX - rect.left) / rect.width) * WIDTH;
      const sy = ((event.clientY - rect.top) / rect.height) * HEIGHT;
      setTransform((t) => {
        const k = Math.min(8, Math.max(0.4, t.k * (event.deltaY < 0 ? 1.15 : 1 / 1.15)));
        return { k, x: sx - ((sx - t.x) / t.k) * k, y: sy - ((sy - t.y) / t.k) * k };
      });
    };
    el.addEventListener('wheel', wheel, { passive: false });
    return () => el.removeEventListener('wheel', wheel);
  }, []);

  const nodeAt = (clientX: number, clientY: number) => {
    const p = toGraph(clientX, clientY);
    return hitTest(positions, visible.nodes, p.x, p.y, (NODE_RADIUS + 3) / Math.sqrt(transform.k));
  };

  return (
    <canvas
      ref={canvas}
      className="graph-canvas"
      style={{ width: '100%', aspectRatio: `${WIDTH} / ${HEIGHT}` }}
      role="img"
      aria-label="Compatibility graph of students. Green lines are matched pairs."
      onMouseDown={(e) => {
        drag.current = { x: e.clientX, y: e.clientY, moved: 0 };
      }}
      onMouseMove={(e) => {
        const d = drag.current;
        if (d) {
          const rect = canvas.current!.getBoundingClientRect();
          const dx = ((e.clientX - d.x) / rect.width) * WIDTH;
          const dy = ((e.clientY - d.y) / rect.height) * HEIGHT;
          d.moved += Math.abs(dx) + Math.abs(dy);
          d.x = e.clientX;
          d.y = e.clientY;
          setTransform((t) => ({ ...t, x: t.x + dx, y: t.y + dy }));
        } else {
          const index = nodeAt(e.clientX, e.clientY);
          setHover(index < 0 ? null : index);
        }
      }}
      onMouseUp={(e) => {
        const wasClick = (drag.current?.moved ?? 0) < 4;
        drag.current = null;
        if (wasClick) {
          const index = nodeAt(e.clientX, e.clientY);
          onSelect(index < 0 ? null : index);
        }
      }}
      onMouseLeave={() => {
        drag.current = null;
        setHover(null);
      }}
      onDoubleClick={() => setTransform({ k: 1, x: 0, y: 0 })}
    />
  );
}

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="stat">
      <dt>{label}</dt>
      <dd>{value}</dd>
      {hint && <small className="muted">{hint}</small>}
    </div>
  );
}

function EdgeDetails({ view, edge }: { view: GraphView; edge: GraphEdge }) {
  const a = view.nodes[edge.a];
  const b = view.nodes[edge.b];
  return (
    <div className="edge-details">
      <div className="row spread">
        <strong>
          {a.name} ↔ {b.name}
        </strong>
        <ScoreBadge score={edge.score} />
      </div>
      <p className="muted small">
        {edge.matched ? 'Matched by the optimal algorithm. ' : 'Candidate pair, not selected. '}
        Shared: {edge.shared.join(', ') || 'none'} · {a.zone}
        {a.zone === b.zone ? '' : ` / ${b.zone}`}
      </p>
      <ScoreBreakdown breakdown={edge} />
    </div>
  );
}

export function MatchGraphPage() {
  const { profile } = useAuth();
  const [topK, setTopK] = useState(4);
  const graph = useLoad(() => matchGraph.graph(topK), [topK]);
  const rounds = useLoad(() => matchGraph.rounds(), []);
  const [filter, setFilter] = useState<Filter>(NO_FILTER);
  const [mode, setMode] = useState<Mode>('graph');
  const [selected, setSelected] = useState<number | null>(null);
  const [selectedEdge, setSelectedEdge] = useState<GraphEdge | null>(null);

  const view = graph.data;
  const positions = useMemo(() => (view ? computeLayout(view, WIDTH, HEIGHT) : []), [view]);
  const subjects = useMemo(() => (view ? allSubjects(view) : []), [view]);

  if (profile && !profile.admin) {
    return <Navigate to="/" replace />;
  }

  const node = view && selected !== null ? view.nodes[selected] : null;
  const nodeEdges = view && selected !== null ? edgesOf(view, selected) : [];
  const improvement =
    view && view.greedy.totalWeight > 0 ? (view.optimal.totalWeight / view.greedy.totalWeight - 1) * 100 : 0;

  return (
    <>
      <PageHeader
        title="Matching graph"
        subtitle="Every available student is a node; a line means two students are academically compatible. Green lines are the pairs chosen by the maximum-weight matching."
        actions={
          <button className="button secondary" onClick={() => { graph.reload(); rounds.reload(); }}>
            Re-run on live pool
          </button>
        }
      />
      <Alert>{graph.error}</Alert>
      {graph.loading && !view ? (
        <Spinner label="Building the graph…" />
      ) : !view || view.poolSize === 0 ? (
        <section className="card">
          <EmptyState>Nobody is available right now. Check students in first.</EmptyState>
        </section>
      ) : (
        <>
          <section className="card">
            <dl className="stats">
              <Stat label="Students in pool" value={String(view.poolSize)} />
              <Stat label="Compatible pairs (edges)" value={view.edgesTotal.toLocaleString()} hint={`${view.edgesShown.toLocaleString()} drawn`} />
              <Stat
                label="Optimal matching"
                value={`${view.optimal.pairs} pairs`}
                hint={`total weight ${view.optimal.totalWeight.toFixed(2)} · ${view.optimal.matchedStudents} students`}
              />
              <Stat
                label="Greedy baseline"
                value={`${view.greedy.pairs} pairs`}
                hint={`total weight ${view.greedy.totalWeight.toFixed(2)} · optimal is ${improvement >= 0 ? '+' : ''}${improvement.toFixed(1)}%`}
              />
              <Stat
                label="Graph + matching time"
                value={`${view.optimal.millis.toFixed(0)} ms`}
                hint={`graph ${view.graphBuildMillis.toFixed(0)} ms, greedy ${view.greedy.millis.toFixed(0)} ms`}
              />
            </dl>
            <p className={view.check.passed ? 'check-pass' : 'check-fail'} role="status">
              {view.check.passed ? '✓ ' : '✗ '}
              Optimality check: {view.check.improvingExchanges} improving exchanges found among{' '}
              {view.check.exchangesTested.toLocaleString()} tested ({view.check.pairsChecked} matched pairs re-paired with
              each other and with unmatched students).{' '}
              <span className="muted">Any matching that fails this check cannot be optimal.</span>
            </p>
          </section>

          <div className="graph-layout">
            <section className="card graph-card">
              <div className="row graph-controls">
                <div className="segmented" role="group" aria-label="View">
                  <button className={mode === 'graph' ? 'on' : ''} onClick={() => setMode('graph')}>
                    Compatibility graph
                  </button>
                  <button className={mode === 'matching' ? 'on' : ''} onClick={() => setMode('matching')}>
                    Matching result
                  </button>
                </div>
                <select aria-label="Zone" value={filter.zone} onChange={(e) => setFilter({ ...filter, zone: e.target.value })}>
                  <option value="">All zones</option>
                  {view.zones.map((z) => (
                    <option key={z}>{z}</option>
                  ))}
                </select>
                <select aria-label="Subject" value={filter.subject} onChange={(e) => setFilter({ ...filter, subject: e.target.value })}>
                  <option value="">All subjects</option>
                  {subjects.map((s) => (
                    <option key={s}>{s}</option>
                  ))}
                </select>
                <label className="check">
                  <input
                    type="checkbox"
                    checked={filter.matchedOnly}
                    onChange={(e) => setFilter({ ...filter, matchedOnly: e.target.checked })}
                  />
                  Matched only
                </label>
                <label className="check">
                  Edges per student
                  <select aria-label="Edges per student" value={topK} onChange={(e) => setTopK(Number(e.target.value))}>
                    {[2, 4, 8, 12].map((k) => (
                      <option key={k}>{k}</option>
                    ))}
                  </select>
                </label>
              </div>
              <GraphCanvas
                view={view}
                positions={positions}
                filter={filter}
                mode={mode}
                selected={selected}
                onSelect={(i) => {
                  setSelected(i);
                  setSelectedEdge(null);
                }}
              />
              <ul className="legend" aria-label="Legend">
                {view.zones.map((z) => (
                  <li key={z}>
                    <span className="swatch" style={{ background: `var(${zoneColor(z, view.zones)})` }} /> {z}
                  </li>
                ))}
                <li>
                  <span className="swatch filled" /> matched
                </li>
                <li>
                  <span className="swatch hollow" /> not matched
                </li>
                <li>
                  <span className="line-sample" /> matched pair
                </li>
              </ul>
              <p className="muted small">Scroll to zoom, drag to pan, click a student to inspect, double-click to reset.</p>
            </section>

            <aside className="card detail-card" aria-label="Details">
              {node ? (
                <>
                  <h2>{node.name}</h2>
                  <p className="muted">
                    {node.zone} · {node.degree} compatible students
                    {node.partner !== null ? ` · matched with ${view.nodes[node.partner].name}` : ' · not matched this round'}
                  </p>
                  <ul className="chips-inline">
                    {node.subjects.map((s) => (
                      <li key={s.name}>
                        <span className={`pill level-${LEVELS[s.level].toLowerCase()}`}>
                          {s.name}
                          <small>{LEVELS[s.level]}</small>
                        </span>
                      </li>
                    ))}
                  </ul>
                  <h3>Strongest candidates</h3>
                  {nodeEdges.length === 0 ? (
                    <p className="muted">No compatible students were found.</p>
                  ) : (
                    <ul className="list compact">
                      {nodeEdges.slice(0, 8).map((e) => {
                        const other = view.nodes[otherEnd(e, node.index)];
                        return (
                          <li key={edgeKey(e.a, e.b)}>
                            <button className={`link-row ${e === selectedEdge ? 'on' : ''}`} onClick={() => setSelectedEdge(e)}>
                              <ScoreBadge score={e.score} />
                              <span>
                                <strong>{other.name}</strong>
                                {e.matched && <span className="tag">matched</span>}
                                <small className="muted"> {e.shared.join(', ')}</small>
                              </span>
                            </button>
                          </li>
                        );
                      })}
                    </ul>
                  )}
                  {selectedEdge && <EdgeDetails view={view} edge={selectedEdge} />}
                </>
              ) : (
                <>
                  <h2>Inspect a match</h2>
                  <p className="muted">
                    Click any student to see why they were (or were not) paired, and which other students they are
                    compatible with. Click a candidate to see the score breakdown.
                  </p>
                  <h3>Strongest matched pairs</h3>
                  <ul className="list compact">
                    {view.edges
                      .filter((e) => e.matched)
                      .sort((x, y) => y.score - x.score)
                      .slice(0, 6)
                      .map((e) => (
                        <li key={edgeKey(e.a, e.b)}>
                          <button className="link-row" onClick={() => { setSelected(e.a); setSelectedEdge(e); }}>
                            <ScoreBadge score={e.score} />
                            <span>
                              <strong>
                                {view.nodes[e.a].name} ↔ {view.nodes[e.b].name}
                              </strong>
                              <small className="muted"> {e.shared.join(', ')}</small>
                            </span>
                          </button>
                        </li>
                      ))}
                  </ul>
                </>
              )}
            </aside>
          </div>
        </>
      )}

      <section className="card">
        <h2>Recent matching rounds</h2>
        {rounds.data?.length ? (
          <table>
            <thead>
              <tr>
                <th>Started</th>
                <th>Students</th>
                <th>Edges</th>
                <th>Pairs</th>
                <th>Total weight</th>
                <th>Graph + matching</th>
              </tr>
            </thead>
            <tbody>
              {rounds.data.map((r) => (
                <tr key={r.startedAt}>
                  <td>{new Date(r.startedAt).toLocaleTimeString()}</td>
                  <td>{r.poolSize}</td>
                  <td>{r.edgeCount.toLocaleString()}</td>
                  <td>{r.pairs}</td>
                  <td>{r.totalWeight.toFixed(2)}</td>
                  <td>{r.durationMs.toFixed(1)} ms</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <EmptyState>No rounds have run yet. Use “Find me a study partner” as a student, or wait for the scheduler.</EmptyState>
        )}
      </section>
    </>
  );
}

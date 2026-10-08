import { forceCollide, forceLink, forceManyBody, forceSimulation, forceX, forceY, type SimulationLinkDatum } from 'd3-force';
import type { GraphEdge, GraphNode, GraphView } from '../api/types';

export interface Filter {
  zone: string;
  subject: string;
  /** Hide students who were not paired. */
  matchedOnly: boolean;
}

export interface Position {
  x: number;
  y: number;
}

export const NO_FILTER: Filter = { zone: '', subject: '', matchedOnly: false };

/** Categorical colours, one per campus zone (colour-blind friendly order). */
const PALETTE = ['#4f46e5', '#0891b2', '#d97706', '#be185d', '#15803d', '#7c3aed', '#64748b', '#b91c1c'];

export function zoneColor(zone: string, zones: string[]): string {
  const index = zones.indexOf(zone);
  return PALETTE[(index < 0 ? 0 : index) % PALETTE.length];
}

export function edgeKey(a: number, b: number): string {
  return a < b ? `${a}-${b}` : `${b}-${a}`;
}

export function allSubjects(view: GraphView): string[] {
  const names = new Set<string>();
  view.nodes.forEach((n) => n.subjects.forEach((s) => names.add(s.name)));
  return [...names].sort();
}

/** Which students and edges remain after applying the zone / subject / matched-only filters. */
export function applyFilter(view: GraphView, filter: Filter): { nodes: Set<number>; edges: GraphEdge[] } {
  const nodes = new Set<number>();
  for (const n of view.nodes) {
    if (filter.zone && n.zone !== filter.zone) continue;
    if (filter.subject && !n.subjects.some((s) => s.name === filter.subject)) continue;
    if (filter.matchedOnly && n.partner === null) continue;
    nodes.add(n.index);
  }
  const edges = view.edges.filter(
    (e) =>
      nodes.has(e.a) &&
      nodes.has(e.b) &&
      (!filter.subject || e.shared.includes(filter.subject)) &&
      (!filter.matchedOnly || e.matched),
  );
  return { nodes, edges };
}

/** Edges touching one student, strongest first. */
export function edgesOf(view: GraphView, index: number): GraphEdge[] {
  return view.edges.filter((e) => e.a === index || e.b === index).sort((x, y) => y.score - x.score);
}

export function otherEnd(edge: GraphEdge, index: number): number {
  return edge.a === index ? edge.b : edge.a;
}

/**
 * Force-directed layout: strongly compatible students end up close together, so clusters of mutual interest are
 * visible. Runs to convergence synchronously (a few hundred ticks) and returns fixed positions.
 */
export function computeLayout(view: GraphView, width: number, height: number, ticks = 320): Position[] {
  interface SimNode {
    index: number;
    x?: number;
    y?: number;
  }
  const nodes: SimNode[] = view.nodes.map((n) => ({ index: n.index }));
  const links: SimulationLinkDatum<SimNode>[] = view.edges.map((e) => ({ source: e.a, target: e.b }));
  const strength = new Map(view.edges.map((e) => [edgeKey(e.a, e.b), e.matched ? 1 : e.score / 100]));

  const simulation = forceSimulation(nodes)
    .randomSource(seededRandom(7))
    .force(
      'link',
      forceLink<SimNode, SimulationLinkDatum<SimNode>>(links)
        .id((n) => n.index)
        .distance((l) => 70 - 40 * (strength.get(edgeKey((l.source as SimNode).index, (l.target as SimNode).index)) ?? 0.3))
        .strength((l) => 0.15 + 0.5 * (strength.get(edgeKey((l.source as SimNode).index, (l.target as SimNode).index)) ?? 0.3)),
    )
    .force('charge', forceManyBody().strength(-38).distanceMax(220))
    .force('x', forceX(width / 2).strength(0.06))
    .force('y', forceY(height / 2).strength(0.06))
    .force('collide', forceCollide(7))
    .stop();
  for (let i = 0; i < ticks; i++) simulation.tick();
  return nodes.map((n) => ({ x: n.x ?? width / 2, y: n.y ?? height / 2 }));
}

/** Deterministic random source so the same data always produces the same picture. */
function seededRandom(seed: number): () => number {
  let state = seed;
  return () => {
    state = (state * 1664525 + 1013904223) % 4294967296;
    return state / 4294967296;
  };
}

/** Index of the node under (x, y) in graph coordinates, or -1. */
export function hitTest(positions: Position[], visible: Set<number>, x: number, y: number, radius: number): number {
  let best = -1;
  let bestDistance = radius * radius;
  for (const index of visible) {
    const p = positions[index];
    if (!p) continue;
    const d = (p.x - x) ** 2 + (p.y - y) ** 2;
    if (d <= bestDistance) {
      best = index;
      bestDistance = d;
    }
  }
  return best;
}

export function nodeLabel(n: GraphNode): string {
  return `${n.name} · ${n.zone}`;
}

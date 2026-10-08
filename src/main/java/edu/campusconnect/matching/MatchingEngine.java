package edu.campusconnect.matching;

import edu.campusconnect.matching.algo.MaxWeightMatching;
import edu.campusconnect.matching.algo.MaxWeightMatching.Edge;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Builds the weighted compatibility graph of the pool and solves it. Pure computation (no I/O) so that it can be
 * unit tested and benchmarked in isolation.
 */
public class MatchingEngine {

    public record Pair(Candidate a, Candidate b, CompatibilityScorer.Score score) {}

    /** The graph plus the solutions found on it. {@code nanos} covers building the graph and solving it. */
    public record Result(int poolSize, int edgeCount, List<Pair> pairs, long totalWeightScaled, long nanos) {

        public double totalWeight() {
            return totalWeightScaled / (double) MaxWeightMatching.SCALE;
        }
    }

    /** Edge of the compatibility graph between pool indices {@code i < j}. */
    public record GraphEdge(int i, int j, CompatibilityScorer.Score score) {
        public long weight() {
            return MaxWeightMatching.scale(score.weight());
        }
    }

    /** Everything needed to explain a matching round: the graph and both solutions, from a single graph build. */
    public record Analysis(List<GraphEdge> graph, Result optimal, Result greedy, long buildNanos) {}

    private final CompatibilityScorer scorer;

    public MatchingEngine(CompatibilityScorer scorer) {
        this.scorer = scorer;
    }

    /**
     * @param pool     students open to being matched right now
     * @param excluded pairs that must not be proposed (recent declines); may be {@code null}
     */
    public Result match(List<Candidate> pool, BiPredicate<UUID, UUID> excluded) {
        long start = System.nanoTime();
        List<GraphEdge> graph = buildGraph(pool, excluded);
        return solveOptimal(pool, graph, System.nanoTime() - start);
    }

    /**
     * Baseline used for evaluation: repeatedly pair the two free students with the heaviest remaining edge.
     * Fast but can be arbitrarily suboptimal.
     */
    public Result greedy(List<Candidate> pool, BiPredicate<UUID, UUID> excluded) {
        long start = System.nanoTime();
        List<GraphEdge> graph = buildGraph(pool, excluded);
        return solveGreedy(pool, graph, System.nanoTime() - start);
    }

    /** Builds the graph once and solves it with both the exact algorithm and the greedy baseline. */
    public Analysis analyze(List<Candidate> pool, BiPredicate<UUID, UUID> excluded) {
        long start = System.nanoTime();
        List<GraphEdge> graph = buildGraph(pool, excluded);
        long buildNanos = System.nanoTime() - start;
        return new Analysis(graph, solveOptimal(pool, graph, buildNanos), solveGreedy(pool, graph, buildNanos), buildNanos);
    }

    private Result solveOptimal(List<Candidate> pool, List<GraphEdge> graph, long buildNanos) {
        long start = System.nanoTime();
        List<Edge> edges = new ArrayList<>(graph.size());
        for (GraphEdge e : graph) {
            edges.add(new Edge(e.i(), e.j(), e.weight()));
        }
        int[] mate = MaxWeightMatching.solve(pool.size(), edges);

        List<Pair> pairs = new ArrayList<>();
        long total = 0;
        for (GraphEdge e : graph) {
            if (mate[e.i()] == e.j()) {
                pairs.add(new Pair(pool.get(e.i()), pool.get(e.j()), e.score()));
                total += e.weight();
            }
        }
        pairs.sort(Comparator.comparingDouble((Pair p) -> p.score().weight()).reversed());
        return new Result(pool.size(), graph.size(), pairs, total, buildNanos + (System.nanoTime() - start));
    }

    private Result solveGreedy(List<Candidate> pool, List<GraphEdge> graph, long buildNanos) {
        long start = System.nanoTime();
        List<GraphEdge> sorted = new ArrayList<>(graph);
        sorted.sort(Comparator.comparingLong(GraphEdge::weight).reversed());
        boolean[] taken = new boolean[pool.size()];
        List<Pair> pairs = new ArrayList<>();
        long total = 0;
        for (GraphEdge e : sorted) {
            if (!taken[e.i()] && !taken[e.j()]) {
                taken[e.i()] = true;
                taken[e.j()] = true;
                pairs.add(new Pair(pool.get(e.i()), pool.get(e.j()), e.score()));
                total += e.weight();
            }
        }
        return new Result(pool.size(), graph.size(), pairs, total, buildNanos + (System.nanoTime() - start));
    }

    private List<GraphEdge> buildGraph(List<Candidate> pool, BiPredicate<UUID, UUID> excluded) {
        List<GraphEdge> graph = new ArrayList<>();
        for (int i = 0; i < pool.size(); i++) {
            for (int j = i + 1; j < pool.size(); j++) {
                Candidate a = pool.get(i);
                Candidate b = pool.get(j);
                if (excluded != null && excluded.test(a.studentId(), b.studentId())) {
                    continue;
                }
                int ii = i;
                int jj = j;
                scorer.edge(a, b).ifPresent(score -> graph.add(new GraphEdge(ii, jj, score)));
            }
        }
        return graph;
    }
}

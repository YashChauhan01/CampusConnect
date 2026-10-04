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

    /** The graph plus the solutions found on it. */
    public record Result(int poolSize, int edgeCount, List<Pair> pairs, long totalWeightScaled, long nanos) {

        public double totalWeight() {
            return totalWeightScaled / (double) MaxWeightMatching.SCALE;
        }
    }

    private final CompatibilityScorer scorer;

    public MatchingEngine(CompatibilityScorer scorer) {
        this.scorer = scorer;
    }

    /** Edge of the compatibility graph between pool indices {@code i < j}. */
    private record ScoredEdge(int i, int j, CompatibilityScorer.Score score) {
        long weight() {
            return MaxWeightMatching.scale(score.weight());
        }
    }

    /**
     * @param pool     students open to being matched right now
     * @param excluded pairs that must not be proposed (recent declines); may be {@code null}
     */
    public Result match(List<Candidate> pool, BiPredicate<UUID, UUID> excluded) {
        long start = System.nanoTime();
        List<ScoredEdge> graph = buildGraph(pool, excluded);

        List<Edge> edges = new ArrayList<>(graph.size());
        for (ScoredEdge e : graph) {
            edges.add(new Edge(e.i(), e.j(), e.weight()));
        }
        int[] mate = MaxWeightMatching.solve(pool.size(), edges);

        List<Pair> pairs = new ArrayList<>();
        long total = 0;
        for (ScoredEdge e : graph) {
            if (mate[e.i()] == e.j()) {
                pairs.add(new Pair(pool.get(e.i()), pool.get(e.j()), e.score()));
                total += e.weight();
            }
        }
        pairs.sort(Comparator.comparingDouble((Pair p) -> p.score().weight()).reversed());
        return new Result(pool.size(), graph.size(), pairs, total, System.nanoTime() - start);
    }

    /**
     * Baseline used for evaluation: repeatedly pair the two free students with the heaviest remaining edge.
     * Fast but can be arbitrarily suboptimal.
     */
    public Result greedy(List<Candidate> pool, BiPredicate<UUID, UUID> excluded) {
        long start = System.nanoTime();
        List<ScoredEdge> graph = new ArrayList<>(buildGraph(pool, excluded));
        graph.sort(Comparator.comparingLong(ScoredEdge::weight).reversed());
        boolean[] taken = new boolean[pool.size()];
        List<Pair> pairs = new ArrayList<>();
        long total = 0;
        for (ScoredEdge e : graph) {
            if (!taken[e.i()] && !taken[e.j()]) {
                taken[e.i()] = true;
                taken[e.j()] = true;
                pairs.add(new Pair(pool.get(e.i()), pool.get(e.j()), e.score()));
                total += e.weight();
            }
        }
        return new Result(pool.size(), graph.size(), pairs, total, System.nanoTime() - start);
    }

    private List<ScoredEdge> buildGraph(List<Candidate> pool, BiPredicate<UUID, UUID> excluded) {
        List<ScoredEdge> graph = new ArrayList<>();
        for (int i = 0; i < pool.size(); i++) {
            for (int j = i + 1; j < pool.size(); j++) {
                Candidate a = pool.get(i);
                Candidate b = pool.get(j);
                if (excluded != null && excluded.test(a.studentId(), b.studentId())) {
                    continue;
                }
                int ii = i;
                int jj = j;
                scorer.edge(a, b).ifPresent(score -> graph.add(new ScoredEdge(ii, jj, score)));
            }
        }
        return graph;
    }
}

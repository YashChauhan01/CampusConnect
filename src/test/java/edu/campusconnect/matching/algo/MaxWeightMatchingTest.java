package edu.campusconnect.matching.algo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusconnect.matching.algo.MaxWeightMatching.Edge;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MaxWeightMatchingTest {

    private static List<Edge> edges(long[][] spec) {
        List<Edge> list = new ArrayList<>();
        for (long[] e : spec) {
            list.add(new Edge((int) e[0], (int) e[1], e[2]));
        }
        return list;
    }

    /** Exact optimum by dynamic programming over vertex subsets (n <= ~20). */
    private static long optimum(int n, List<Edge> edges) {
        long[][] w = new long[n][n];
        for (long[] row : w) {
            java.util.Arrays.fill(row, -1);
        }
        for (Edge e : edges) {
            w[e.u()][e.v()] = Math.max(w[e.u()][e.v()], e.weight());
            w[e.v()][e.u()] = w[e.u()][e.v()];
        }
        long[] best = new long[1 << n];
        for (int mask = 1; mask < (1 << n); mask++) {
            int i = Integer.numberOfTrailingZeros(mask);
            int rest = mask & ~(1 << i);
            long value = best[rest]; // leave i unmatched
            for (int j = i + 1; j < n; j++) {
                if ((rest & (1 << j)) != 0 && w[i][j] >= 0) {
                    value = Math.max(value, w[i][j] + best[rest & ~(1 << j)]);
                }
            }
            best[mask] = value;
        }
        return best[(1 << n) - 1];
    }

    private static void assertValidMatching(int[] mate, int n, List<Edge> edges) {
        assertEquals(n, mate.length);
        for (int v = 0; v < n; v++) {
            if (mate[v] != -1) {
                assertTrue(mate[v] >= 0 && mate[v] < n && mate[v] != v, "bad partner");
                assertEquals(v, mate[mate[v]], "matching not symmetric at " + v);
                final int vv = v;
                assertTrue(edges.stream().anyMatch(e ->
                                (e.u() == vv && e.v() == mate[vv]) || (e.v() == vv && e.u() == mate[vv])),
                        "matched pair is not an edge");
            }
        }
    }

    /** Weight of the heaviest parallel edge for each matched pair. */
    private static long weightOf(int[] mate, List<Edge> edges) {
        long total = 0;
        for (int v = 0; v < mate.length; v++) {
            if (mate[v] > v) {
                long best = 0;
                for (Edge e : edges) {
                    if ((e.u() == v && e.v() == mate[v]) || (e.v() == v && e.u() == mate[v])) {
                        best = Math.max(best, e.weight());
                    }
                }
                total += best;
            }
        }
        return total;
    }

    @Test
    void emptyInputs() {
        assertArrayEquals(new int[0], MaxWeightMatching.solve(0, List.of()));
        assertArrayEquals(new int[]{-1, -1, -1}, MaxWeightMatching.solve(3, List.of()));
    }

    @Test
    void singleEdge() {
        assertArrayEquals(new int[]{1, 0}, MaxWeightMatching.solve(2, edges(new long[][]{{0, 1, 5}})));
    }

    @Test
    void prefersHeavierPairOverMoreEdges() {
        // Path 0-1-2-3: matching {1,2} (weight 10) beats {0,1}+{2,3} (weight 4+4).
        List<Edge> g = edges(new long[][]{{0, 1, 4}, {1, 2, 10}, {2, 3, 4}});
        int[] mate = MaxWeightMatching.solve(4, g);
        assertEquals(10, MaxWeightMatching.totalWeight(mate, g));
        assertArrayEquals(new int[]{-1, 2, 1, -1}, mate);
    }

    @Test
    void takesBothEdgesWhenTheyAreWorthMore() {
        List<Edge> g = edges(new long[][]{{0, 1, 6}, {1, 2, 10}, {2, 3, 6}});
        assertEquals(12, MaxWeightMatching.totalWeight(MaxWeightMatching.solve(4, g), g));
    }

    @Test
    void oddCycleRequiresABlossom() {
        // Triangle with pendants: 0-1-2 triangle, 3 hangs off 0, 4 hangs off 1.
        List<Edge> g = edges(new long[][]{{0, 1, 8}, {1, 2, 8}, {0, 2, 8}, {0, 3, 7}, {1, 4, 7}});
        int[] mate = MaxWeightMatching.solve(5, g);
        assertValidMatching(mate, 5, g);
        assertEquals(optimum(5, g), MaxWeightMatching.totalWeight(mate, g));
        // {0,3}+{1,2} (or {1,4}+{0,2}) = 15 beats {0,3}+{1,4} = 14 and any single triangle edge.
        assertEquals(15, MaxWeightMatching.totalWeight(mate, g));
    }

    @Test
    void nestedBlossoms() {
        // Classic test graphs from the reference implementation of this algorithm.
        List<Edge> g = edges(new long[][]{{1, 2, 9}, {1, 3, 9}, {2, 3, 10}, {2, 4, 8}, {3, 5, 8}, {4, 5, 10}, {5, 6, 6}});
        int[] mate = MaxWeightMatching.solve(7, g);
        assertValidMatching(mate, 7, g);
        assertEquals(optimum(7, g), MaxWeightMatching.totalWeight(mate, g));
    }

    @Test
    void zeroWeightEdgesAreHarmless() {
        List<Edge> g = edges(new long[][]{{0, 1, 0}, {1, 2, 0}});
        int[] mate = MaxWeightMatching.solve(3, g);
        assertValidMatching(mate, 3, g);
        assertEquals(0, MaxWeightMatching.totalWeight(mate, g));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> MaxWeightMatching.solve(2, edges(new long[][]{{0, 0, 1}})));
        assertThrows(IllegalArgumentException.class, () -> MaxWeightMatching.solve(2, edges(new long[][]{{0, 5, 1}})));
        assertThrows(IllegalArgumentException.class, () -> MaxWeightMatching.solve(2, edges(new long[][]{{0, 1, -1}})));
    }

    @Test
    void matchesExactOptimumOnRandomGraphs() {
        Random random = new Random(42);
        for (int round = 0; round < 3000; round++) {
            int n = 2 + random.nextInt(13);
            double density = 0.15 + random.nextDouble() * 0.85;
            int maxWeight = random.nextBoolean() ? 3 : 1000; // small ranges create many ties
            List<Edge> g = new ArrayList<>();
            for (int u = 0; u < n; u++) {
                for (int v = u + 1; v < n; v++) {
                    if (random.nextDouble() < density) {
                        g.add(new Edge(u, v, random.nextInt(maxWeight + 1)));
                    }
                }
            }
            int[] mate = MaxWeightMatching.solve(n, g);
            assertValidMatching(mate, n, g);
            assertEquals(optimum(n, g), weightOf(mate, g), "round " + round + " n=" + n + " edges=" + g);
        }
    }

    @Test
    void handlesDenseGraphWithOddVertexCounts() {
        Random random = new Random(7);
        for (int n = 3; n <= 17; n += 2) {
            List<Edge> g = new ArrayList<>();
            for (int u = 0; u < n; u++) {
                for (int v = u + 1; v < n; v++) {
                    g.add(new Edge(u, v, 1 + random.nextInt(100)));
                }
            }
            int[] mate = MaxWeightMatching.solve(n, g);
            assertEquals(optimum(n, g), MaxWeightMatching.totalWeight(mate, g), "n=" + n);
        }
    }

    @Test
    void scalesFractionalScores() {
        assertEquals(500_000, MaxWeightMatching.scale(0.5));
        assertEquals(0, MaxWeightMatching.scale(-1));
    }

    @Test
    void solvesLargeInstanceQuickly() {
        Random random = new Random(1);
        int n = 300;
        List<Edge> g = new ArrayList<>();
        for (int u = 0; u < n; u++) {
            for (int v = u + 1; v < n; v++) {
                if (random.nextDouble() < 0.3) {
                    g.add(new Edge(u, v, 1 + random.nextInt(1_000_000)));
                }
            }
        }
        int[] mate = assertTimeoutPreemptively(Duration.ofSeconds(20), () -> MaxWeightMatching.solve(n, g));
        assertValidMatching(mate, n, g);
    }
}

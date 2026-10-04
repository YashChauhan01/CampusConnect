package edu.campusconnect.matching.algo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HungarianAlgorithmTest {

    /** Exhaustive minimum over all injective assignments of the smaller side. */
    private static long bruteForce(long[][] cost) {
        int rows = cost.length;
        int cols = cost[0].length;
        if (rows <= cols) {
            return searchRows(cost, 0, new boolean[cols]);
        }
        long[][] t = new long[cols][rows];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                t[j][i] = cost[i][j];
            }
        }
        return searchRows(t, 0, new boolean[rows]);
    }

    private static long searchRows(long[][] cost, int row, boolean[] usedCols) {
        if (row == cost.length) {
            return 0;
        }
        long best = Long.MAX_VALUE;
        for (int j = 0; j < usedCols.length; j++) {
            if (!usedCols[j]) {
                usedCols[j] = true;
                best = Math.min(best, cost[row][j] + searchRows(cost, row + 1, usedCols));
                usedCols[j] = false;
            }
        }
        return best;
    }

    private static void assertInjective(int[] assignment, int cols) {
        Set<Integer> seen = new HashSet<>();
        for (int c : assignment) {
            if (c >= 0) {
                assertTrue(c < cols);
                assertTrue(seen.add(c), "column used twice: " + c);
            }
        }
    }

    @Test
    void classicThreeByThree() {
        long[][] cost = {{4, 1, 3}, {2, 0, 5}, {3, 2, 2}};
        int[] a = HungarianAlgorithm.solve(cost);
        assertEquals(5, HungarianAlgorithm.totalCost(cost, a));
        assertArrayEquals(new int[]{1, 0, 2}, a);
    }

    @Test
    void emptyAndDegenerateShapes() {
        assertArrayEquals(new int[0], HungarianAlgorithm.solve(new long[0][0]));
        assertArrayEquals(new int[]{-1, -1}, HungarianAlgorithm.solve(new long[][]{{}, {}}));
        assertArrayEquals(new int[]{0}, HungarianAlgorithm.solve(new long[][]{{7}}));
    }

    @Test
    void wideMatrixAssignsEveryRow() {
        long[][] cost = {{9, 2, 7, 8}, {6, 4, 3, 7}};
        int[] a = HungarianAlgorithm.solve(cost);
        assertEquals(2 + 3, HungarianAlgorithm.totalCost(cost, a));
        assertTrue(a[0] >= 0 && a[1] >= 0);
    }

    @Test
    void tallMatrixLeavesSurplusRowsUnassigned() {
        long[][] cost = {{5, 9}, {1, 8}, {7, 2}};
        int[] a = HungarianAlgorithm.solve(cost);
        assertEquals(3, HungarianAlgorithm.totalCost(cost, a));
        assertEquals(2, java.util.Arrays.stream(a).filter(x -> x >= 0).count());
    }

    @Test
    void handlesNegativeCosts() {
        long[][] cost = {{-5, 3}, {2, -7}};
        assertEquals(-12, HungarianAlgorithm.totalCost(cost, HungarianAlgorithm.solve(cost)));
    }

    @Test
    void rejectsRaggedMatrix() {
        assertThrows(IllegalArgumentException.class, () -> HungarianAlgorithm.solve(new long[][]{{1, 2}, {3}}));
    }

    @Test
    void matchesBruteForceOnRandomRectangularMatrices() {
        Random random = new Random(99);
        for (int round = 0; round < 2000; round++) {
            int rows = 1 + random.nextInt(7);
            int cols = 1 + random.nextInt(7);
            int range = random.nextBoolean() ? 3 : 1000;
            long[][] cost = new long[rows][cols];
            for (long[] r : cost) {
                for (int j = 0; j < cols; j++) {
                    r[j] = random.nextInt(range) - range / 4;
                }
            }
            int[] a = HungarianAlgorithm.solve(cost);
            assertInjective(a, cols);
            assertEquals(Math.min(rows, cols), java.util.Arrays.stream(a).filter(x -> x >= 0).count());
            assertEquals(bruteForce(cost), HungarianAlgorithm.totalCost(cost, a), "round " + round);
        }
    }

    @Test
    void solvesLargeInstanceQuickly() {
        Random random = new Random(5);
        int n = 400;
        long[][] cost = new long[n][n];
        for (long[] r : cost) {
            for (int j = 0; j < n; j++) {
                r[j] = random.nextInt(1_000_000);
            }
        }
        int[] a = assertTimeoutPreemptively(Duration.ofSeconds(20), () -> HungarianAlgorithm.solve(cost));
        assertInjective(a, n);
    }
}

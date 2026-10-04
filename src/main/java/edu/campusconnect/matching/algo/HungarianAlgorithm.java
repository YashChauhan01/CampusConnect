package edu.campusconnect.matching.algo;

import java.util.Arrays;

/**
 * Minimum-cost assignment (Kuhn–Munkres / Hungarian algorithm) using row/column potentials, O(n^2 m).
 *
 * <p>Works on rectangular matrices: with {@code n} rows and {@code m} columns every row is assigned a distinct
 * column when {@code n <= m}; when {@code n > m} every column receives a distinct row and the surplus rows are left
 * unassigned. Costs are exact {@code long}s (callers scale fractional costs, see {@link #scale(double)}).
 */
public final class HungarianAlgorithm {

    /** Fixed-point factor used by {@link #scale(double)}. */
    public static final long SCALE = 1_000_000L;

    private HungarianAlgorithm() {}

    public static long scale(double cost) {
        return Math.round(cost * SCALE);
    }

    /**
     * @param cost {@code cost[i][j]} = cost of giving column {@code j} to row {@code i}; all rows equally long
     * @return {@code assignment[i]} = column of row {@code i}, or {@code -1} if the row is unassigned
     */
    public static int[] solve(long[][] cost) {
        int rows = cost.length;
        if (rows == 0) {
            return new int[0];
        }
        int cols = cost[0].length;
        for (long[] row : cost) {
            if (row.length != cols) {
                throw new IllegalArgumentException("Cost matrix must be rectangular");
            }
        }
        if (cols == 0) {
            int[] none = new int[rows];
            Arrays.fill(none, -1);
            return none;
        }
        if (rows <= cols) {
            return solveWide(cost, rows, cols);
        }
        // More rows than columns: solve the transpose and map the result back.
        long[][] transposed = new long[cols][rows];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                transposed[j][i] = cost[i][j];
            }
        }
        int[] colToRow = solveWide(transposed, cols, rows);
        int[] assignment = new int[rows];
        Arrays.fill(assignment, -1);
        for (int j = 0; j < cols; j++) {
            assignment[colToRow[j]] = j;
        }
        return assignment;
    }

    /** Sum of the costs picked by {@code assignment}; unassigned rows contribute nothing. */
    public static long totalCost(long[][] cost, int[] assignment) {
        long total = 0;
        for (int i = 0; i < assignment.length; i++) {
            if (assignment[i] >= 0) {
                total += cost[i][assignment[i]];
            }
        }
        return total;
    }

    /** Requires {@code n <= m}. Indices are 1-based internally; index 0 is a sentinel. */
    private static int[] solveWide(long[][] a, int n, int m) {
        final long inf = Long.MAX_VALUE / 4;
        long[] u = new long[n + 1];
        long[] v = new long[m + 1];
        int[] matchOfCol = new int[m + 1];
        int[] way = new int[m + 1];

        for (int i = 1; i <= n; i++) {
            matchOfCol[0] = i;
            int j0 = 0;
            long[] minv = new long[m + 1];
            Arrays.fill(minv, inf);
            boolean[] used = new boolean[m + 1];
            do {
                used[j0] = true;
                int i0 = matchOfCol[j0];
                long delta = inf;
                int j1 = 0;
                for (int j = 1; j <= m; j++) {
                    if (!used[j]) {
                        long cur = a[i0 - 1][j - 1] - u[i0] - v[j];
                        if (cur < minv[j]) {
                            minv[j] = cur;
                            way[j] = j0;
                        }
                        if (minv[j] < delta) {
                            delta = minv[j];
                            j1 = j;
                        }
                    }
                }
                for (int j = 0; j <= m; j++) {
                    if (used[j]) {
                        u[matchOfCol[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
            } while (matchOfCol[j0] != 0);
            do {
                int j1 = way[j0];
                matchOfCol[j0] = matchOfCol[j1];
                j0 = j1;
            } while (j0 != 0);
        }

        int[] assignment = new int[n];
        for (int j = 1; j <= m; j++) {
            if (matchOfCol[j] != 0) {
                assignment[matchOfCol[j] - 1] = j - 1;
            }
        }
        return assignment;
    }
}

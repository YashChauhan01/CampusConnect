package edu.campusconnect.matching.algo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Maximum-weight matching in a general (non-bipartite) graph using Edmonds' blossom algorithm with
 * primal-dual updates, O(V^3).
 *
 * <p>The matching maximises total edge weight and is <em>not</em> forced to be of maximum cardinality: an edge is
 * only used if it increases the total. Weights must be non-negative integers; callers with fractional scores
 * should scale them first (see {@link #scale(double)}). All arithmetic is exact, so ties and optimality are not
 * subject to floating point error.
 *
 * <p>Notation (after Galil, "Efficient algorithms for finding maximum matching in graphs", 1986): a vertex or
 * blossom is labelled S (even), T (odd) or unlabelled; "endpoints" index the two ends of each edge, with edge
 * {@code k} having endpoints {@code 2k} and {@code 2k+1}.
 */
public final class MaxWeightMatching {

    /** Undirected weighted edge between vertices {@code u} and {@code v}. */
    public record Edge(int u, int v, long weight) {}

    /** Fixed-point factor used by {@link #scale(double)}. */
    public static final long SCALE = 1_000_000L;

    /** Converts a score in [0, 1] (or any non-negative double) to the integer weight used by the solver. */
    public static long scale(double score) {
        return Math.round(Math.max(0.0, score) * SCALE);
    }

    private final int nVertex;
    private final int nEdge;
    private final int[] edgeU;
    private final int[] edgeV;
    private final long[] edgeW;
    private final int[] endpoint;
    private final List<List<Integer>> neighbEnd;

    private final int[] mate;
    /** 0 = unlabelled, 1 = S, 2 = T; bit 4 is used transiently while scanning for blossoms. */
    private final int[] label;
    private final int[] labelEnd;
    private final int[] inBlossom;
    private final int[] blossomParent;
    private final List<List<Integer>> blossomChilds;
    private final int[] blossomBase;
    private final List<List<Integer>> blossomEndps;
    private final int[] bestEdge;
    private final List<List<Integer>> blossomBestEdges;
    private final Deque<Integer> unusedBlossoms = new ArrayDeque<>();
    private final long[] dualVar;
    private final boolean[] allowEdge;
    private final Deque<Integer> queue = new ArrayDeque<>();

    private MaxWeightMatching(int n, List<Edge> edges) {
        this.nVertex = n;
        this.nEdge = edges.size();
        edgeU = new int[nEdge];
        edgeV = new int[nEdge];
        edgeW = new long[nEdge];
        long maxWeight = 0;
        for (int k = 0; k < nEdge; k++) {
            Edge e = edges.get(k);
            if (e.u() < 0 || e.v() < 0 || e.u() >= n || e.v() >= n || e.u() == e.v()) {
                throw new IllegalArgumentException("Invalid edge " + e);
            }
            if (e.weight() < 0) {
                throw new IllegalArgumentException("Negative weight on edge " + e);
            }
            edgeU[k] = e.u();
            edgeV[k] = e.v();
            // Doubling keeps every slack between two S-blossoms even, so halving it below is exact.
            edgeW[k] = 2 * e.weight();
            maxWeight = Math.max(maxWeight, edgeW[k]);
        }
        endpoint = new int[2 * nEdge];
        neighbEnd = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            neighbEnd.add(new ArrayList<>());
        }
        for (int k = 0; k < nEdge; k++) {
            endpoint[2 * k] = edgeU[k];
            endpoint[2 * k + 1] = edgeV[k];
            neighbEnd.get(edgeU[k]).add(2 * k + 1);
            neighbEnd.get(edgeV[k]).add(2 * k);
        }
        mate = new int[n];
        Arrays.fill(mate, -1);
        label = new int[2 * n];
        labelEnd = new int[2 * n];
        Arrays.fill(labelEnd, -1);
        inBlossom = new int[n];
        for (int i = 0; i < n; i++) {
            inBlossom[i] = i;
        }
        blossomParent = new int[2 * n];
        Arrays.fill(blossomParent, -1);
        blossomChilds = nullLists(2 * n);
        blossomBase = new int[2 * n];
        for (int i = 0; i < 2 * n; i++) {
            blossomBase[i] = i < n ? i : -1;
        }
        blossomEndps = nullLists(2 * n);
        bestEdge = new int[2 * n];
        Arrays.fill(bestEdge, -1);
        blossomBestEdges = nullLists(2 * n);
        for (int b = n; b < 2 * n; b++) {
            unusedBlossoms.push(b);
        }
        dualVar = new long[2 * n];
        for (int i = 0; i < n; i++) {
            dualVar[i] = maxWeight;
        }
        allowEdge = new boolean[nEdge];
    }

    private static List<List<Integer>> nullLists(int size) {
        List<List<Integer>> lists = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            lists.add(null);
        }
        return lists;
    }

    /**
     * Computes a maximum-weight matching.
     *
     * @param n     number of vertices, labelled {@code 0..n-1}
     * @param edges undirected edges with non-negative integer weights
     * @return {@code mate[v]} = partner of {@code v}, or {@code -1} if {@code v} is unmatched
     */
    public static int[] solve(int n, List<Edge> edges) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be non-negative");
        }
        if (n == 0 || edges.isEmpty()) {
            int[] none = new int[n];
            Arrays.fill(none, -1);
            return none;
        }
        return new MaxWeightMatching(n, edges).run();
    }

    /** Total weight of the matching described by {@code mate} (each matched edge counted once). */
    public static long totalWeight(int[] mate, List<Edge> edges) {
        long total = 0;
        for (Edge e : edges) {
            if (mate[e.u()] == e.v() && mate[e.v()] == e.u()) {
                total += e.weight();
            }
        }
        return total;
    }

    // ---------------------------------------------------------------------------------------------------------------

    private long slack(int k) {
        return dualVar[edgeU[k]] + dualVar[edgeV[k]] - 2 * edgeW[k];
    }

    private void blossomLeaves(int b, List<Integer> out) {
        if (b < nVertex) {
            out.add(b);
        } else {
            for (int t : blossomChilds.get(b)) {
                blossomLeaves(t, out);
            }
        }
    }

    private List<Integer> leaves(int b) {
        List<Integer> out = new ArrayList<>();
        blossomLeaves(b, out);
        return out;
    }

    private static int at(List<Integer> list, int index) {
        return list.get(Math.floorMod(index, list.size()));
    }

    private void assignLabel(int w, int t, int p) {
        int b = inBlossom[w];
        label[w] = label[b] = t;
        labelEnd[w] = labelEnd[b] = p;
        bestEdge[w] = bestEdge[b] = -1;
        if (t == 1) {
            for (int leaf : leaves(b)) {
                queue.push(leaf);
            }
        } else if (t == 2) {
            int base = blossomBase[b];
            assignLabel(endpoint[mate[base]], 1, mate[base] ^ 1);
        }
    }

    /** Traces back from v and w to find a common root (a new blossom base) or an augmenting path (-1). */
    private int scanBlossom(int v, int w) {
        List<Integer> path = new ArrayList<>();
        int base = -1;
        while (v != -1 || w != -1) {
            int b = inBlossom[v];
            if ((label[b] & 4) != 0) {
                base = blossomBase[b];
                break;
            }
            path.add(b);
            label[b] = 5;
            if (labelEnd[b] == -1) {
                v = -1;
            } else {
                v = endpoint[labelEnd[b]];
                b = inBlossom[v];
                v = endpoint[labelEnd[b]];
            }
            if (w != -1) {
                int tmp = v;
                v = w;
                w = tmp;
            }
        }
        for (int b : path) {
            label[b] = 1;
        }
        return base;
    }

    private void addBlossom(int base, int k) {
        int v = edgeU[k];
        int w = edgeV[k];
        int bb = inBlossom[base];
        int bv = inBlossom[v];
        int bw = inBlossom[w];
        int b = unusedBlossoms.pop();
        blossomBase[b] = base;
        blossomParent[b] = -1;
        blossomParent[bb] = b;
        List<Integer> path = new ArrayList<>();
        List<Integer> endps = new ArrayList<>();
        blossomChilds.set(b, path);
        blossomEndps.set(b, endps);
        while (bv != bb) {
            blossomParent[bv] = b;
            path.add(bv);
            endps.add(labelEnd[bv]);
            v = endpoint[labelEnd[bv]];
            bv = inBlossom[v];
        }
        path.add(bb);
        Collections.reverse(path);
        Collections.reverse(endps);
        endps.add(2 * k);
        while (bw != bb) {
            blossomParent[bw] = b;
            path.add(bw);
            endps.add(labelEnd[bw] ^ 1);
            w = endpoint[labelEnd[bw]];
            bw = inBlossom[w];
        }
        label[b] = 1;
        labelEnd[b] = labelEnd[bb];
        dualVar[b] = 0;
        for (int leaf : leaves(b)) {
            if (label[inBlossom[leaf]] == 2) {
                queue.push(leaf);
            }
            inBlossom[leaf] = b;
        }
        int[] bestEdgeTo = new int[2 * nVertex];
        Arrays.fill(bestEdgeTo, -1);
        for (int sub : path) {
            List<List<Integer>> neighbourLists = new ArrayList<>();
            if (blossomBestEdges.get(sub) == null) {
                for (int leaf : leaves(sub)) {
                    List<Integer> edgesOfLeaf = new ArrayList<>();
                    for (int p : neighbEnd.get(leaf)) {
                        edgesOfLeaf.add(p / 2);
                    }
                    neighbourLists.add(edgesOfLeaf);
                }
            } else {
                neighbourLists.add(blossomBestEdges.get(sub));
            }
            for (List<Integer> list : neighbourLists) {
                for (int edge : list) {
                    int i = edgeU[edge];
                    int j = edgeV[edge];
                    if (inBlossom[j] == b) {
                        int tmp = i;
                        i = j;
                        j = tmp;
                    }
                    int bj = inBlossom[j];
                    if (bj != b && label[bj] == 1
                            && (bestEdgeTo[bj] == -1 || slack(edge) < slack(bestEdgeTo[bj]))) {
                        bestEdgeTo[bj] = edge;
                    }
                }
            }
            blossomBestEdges.set(sub, null);
            bestEdge[sub] = -1;
        }
        List<Integer> best = new ArrayList<>();
        for (int edge : bestEdgeTo) {
            if (edge != -1) {
                best.add(edge);
            }
        }
        blossomBestEdges.set(b, best);
        bestEdge[b] = -1;
        for (int edge : best) {
            if (bestEdge[b] == -1 || slack(edge) < slack(bestEdge[b])) {
                bestEdge[b] = edge;
            }
        }
    }

    private void expandBlossom(int b, boolean endStage) {
        for (int s : new ArrayList<>(blossomChilds.get(b))) {
            blossomParent[s] = -1;
            if (s < nVertex) {
                inBlossom[s] = s;
            } else if (endStage && dualVar[s] == 0) {
                expandBlossom(s, endStage);
            } else {
                for (int leaf : leaves(s)) {
                    inBlossom[leaf] = s;
                }
            }
        }
        if (!endStage && label[b] == 2) {
            List<Integer> childs = blossomChilds.get(b);
            List<Integer> endps = blossomEndps.get(b);
            int entryChild = inBlossom[endpoint[labelEnd[b] ^ 1]];
            int j = childs.indexOf(entryChild);
            int jStep;
            int endpTrick;
            if ((j & 1) != 0) {
                j -= childs.size();
                jStep = 1;
                endpTrick = 0;
            } else {
                jStep = -1;
                endpTrick = 1;
            }
            int p = labelEnd[b];
            while (j != 0) {
                label[endpoint[p ^ 1]] = 0;
                label[endpoint[at(endps, j - endpTrick) ^ endpTrick ^ 1]] = 0;
                assignLabel(endpoint[p ^ 1], 2, p);
                allowEdge[at(endps, j - endpTrick) / 2] = true;
                j += jStep;
                p = at(endps, j - endpTrick) ^ endpTrick;
                allowEdge[p / 2] = true;
                j += jStep;
            }
            int bv = at(childs, j);
            label[endpoint[p ^ 1]] = label[bv] = 2;
            labelEnd[endpoint[p ^ 1]] = labelEnd[bv] = p;
            bestEdge[bv] = -1;
            j += jStep;
            while (at(childs, j) != entryChild) {
                bv = at(childs, j);
                if (label[bv] == 1) {
                    j += jStep;
                    continue;
                }
                int v = -1;
                for (int leaf : leaves(bv)) {
                    v = leaf;
                    if (label[leaf] != 0) {
                        break;
                    }
                }
                if (label[v] != 0) {
                    label[v] = 0;
                    label[endpoint[mate[blossomBase[bv]]]] = 0;
                    assignLabel(v, 2, labelEnd[v]);
                }
                j += jStep;
            }
        }
        label[b] = labelEnd[b] = -1;
        blossomChilds.set(b, null);
        blossomEndps.set(b, null);
        blossomBase[b] = -1;
        blossomBestEdges.set(b, null);
        bestEdge[b] = -1;
        unusedBlossoms.push(b);
    }

    /** Re-roots blossom {@code b} at vertex {@code v} by swapping matched/unmatched edges along the even path. */
    private void augmentBlossom(int b, int v) {
        int t = v;
        while (blossomParent[t] != b) {
            t = blossomParent[t];
        }
        if (t >= nVertex) {
            augmentBlossom(t, v);
        }
        List<Integer> childs = blossomChilds.get(b);
        List<Integer> endps = blossomEndps.get(b);
        int i = childs.indexOf(t);
        int j = i;
        int jStep;
        int endpTrick;
        if ((i & 1) != 0) {
            j -= childs.size();
            jStep = 1;
            endpTrick = 0;
        } else {
            jStep = -1;
            endpTrick = 1;
        }
        while (j != 0) {
            j += jStep;
            t = at(childs, j);
            int p = at(endps, j - endpTrick) ^ endpTrick;
            if (t >= nVertex) {
                augmentBlossom(t, endpoint[p]);
            }
            j += jStep;
            t = at(childs, j);
            if (t >= nVertex) {
                augmentBlossom(t, endpoint[p ^ 1]);
            }
            mate[endpoint[p]] = p ^ 1;
            mate[endpoint[p ^ 1]] = p;
        }
        Collections.rotate(childs, -i);
        Collections.rotate(endps, -i);
        blossomBase[b] = blossomBase[childs.get(0)];
    }

    private void augmentMatching(int k) {
        int[][] ends = {{edgeU[k], 2 * k + 1}, {edgeV[k], 2 * k}};
        for (int[] end : ends) {
            int s = end[0];
            int p = end[1];
            while (true) {
                int bs = inBlossom[s];
                if (bs >= nVertex) {
                    augmentBlossom(bs, s);
                }
                mate[s] = p;
                if (labelEnd[bs] == -1) {
                    break;
                }
                int t = endpoint[labelEnd[bs]];
                int bt = inBlossom[t];
                s = endpoint[labelEnd[bt]];
                int j = endpoint[labelEnd[bt] ^ 1];
                if (bt >= nVertex) {
                    augmentBlossom(bt, j);
                }
                mate[j] = labelEnd[bt];
                p = labelEnd[bt] ^ 1;
            }
        }
    }

    private int[] run() {
        for (int stage = 0; stage < nVertex; stage++) {
            Arrays.fill(label, 0);
            Arrays.fill(bestEdge, -1);
            for (int b = nVertex; b < 2 * nVertex; b++) {
                blossomBestEdges.set(b, null);
            }
            Arrays.fill(allowEdge, false);
            queue.clear();

            for (int v = 0; v < nVertex; v++) {
                if (mate[v] == -1 && label[inBlossom[v]] == 0) {
                    assignLabel(v, 1, -1);
                }
            }

            boolean augmented = false;
            while (true) {
                while (!queue.isEmpty() && !augmented) {
                    int v = queue.pop();
                    for (int p : neighbEnd.get(v)) {
                        int k = p / 2;
                        int w = endpoint[p];
                        if (inBlossom[v] == inBlossom[w]) {
                            continue;
                        }
                        long kSlack = 0;
                        if (!allowEdge[k]) {
                            kSlack = slack(k);
                            if (kSlack <= 0) {
                                allowEdge[k] = true;
                            }
                        }
                        if (allowEdge[k]) {
                            if (label[inBlossom[w]] == 0) {
                                assignLabel(w, 2, p ^ 1);
                            } else if (label[inBlossom[w]] == 1) {
                                int base = scanBlossom(v, w);
                                if (base >= 0) {
                                    addBlossom(base, k);
                                } else {
                                    augmentMatching(k);
                                    augmented = true;
                                    break;
                                }
                            } else if (label[w] == 0) {
                                label[w] = 2;
                                labelEnd[w] = p ^ 1;
                            }
                        } else if (label[inBlossom[w]] == 1) {
                            int b = inBlossom[v];
                            if (bestEdge[b] == -1 || kSlack < slack(bestEdge[b])) {
                                bestEdge[b] = k;
                            }
                        } else if (label[w] == 0) {
                            if (bestEdge[w] == -1 || kSlack < slack(bestEdge[w])) {
                                bestEdge[w] = k;
                            }
                        }
                    }
                }
                if (augmented) {
                    break;
                }

                // No augmenting path with the current duals: compute the dual adjustment.
                int deltaType = -1;
                long delta = 0;
                int deltaEdge = -1;
                int deltaBlossom = -1;

                deltaType = 1;
                delta = Long.MAX_VALUE;
                for (int v = 0; v < nVertex; v++) {
                    delta = Math.min(delta, dualVar[v]);
                }
                for (int v = 0; v < nVertex; v++) {
                    if (label[inBlossom[v]] == 0 && bestEdge[v] != -1) {
                        long d = slack(bestEdge[v]);
                        if (d < delta) {
                            delta = d;
                            deltaType = 2;
                            deltaEdge = bestEdge[v];
                        }
                    }
                }
                for (int b = 0; b < 2 * nVertex; b++) {
                    if (blossomParent[b] == -1 && label[b] == 1 && bestEdge[b] != -1) {
                        long d = slack(bestEdge[b]) / 2;
                        if (d < delta) {
                            delta = d;
                            deltaType = 3;
                            deltaEdge = bestEdge[b];
                        }
                    }
                }
                for (int b = nVertex; b < 2 * nVertex; b++) {
                    if (blossomBase[b] >= 0 && blossomParent[b] == -1 && label[b] == 2 && dualVar[b] < delta) {
                        delta = dualVar[b];
                        deltaType = 4;
                        deltaBlossom = b;
                    }
                }

                for (int v = 0; v < nVertex; v++) {
                    if (label[inBlossom[v]] == 1) {
                        dualVar[v] -= delta;
                    } else if (label[inBlossom[v]] == 2) {
                        dualVar[v] += delta;
                    }
                }
                for (int b = nVertex; b < 2 * nVertex; b++) {
                    if (blossomBase[b] >= 0 && blossomParent[b] == -1) {
                        if (label[b] == 1) {
                            dualVar[b] += delta;
                        } else if (label[b] == 2) {
                            dualVar[b] -= delta;
                        }
                    }
                }

                if (deltaType == 1) {
                    // Optimum reached: a vertex dual hit zero.
                    break;
                } else if (deltaType == 2) {
                    allowEdge[deltaEdge] = true;
                    int i = edgeU[deltaEdge];
                    int j = edgeV[deltaEdge];
                    if (label[inBlossom[i]] == 0) {
                        i = j;
                    }
                    queue.push(i);
                } else if (deltaType == 3) {
                    allowEdge[deltaEdge] = true;
                    queue.push(edgeU[deltaEdge]);
                } else {
                    expandBlossom(deltaBlossom, false);
                }
            }

            if (!augmented) {
                break;
            }
            // End of stage: expand all S-blossoms whose dual dropped to zero.
            for (int b = nVertex; b < 2 * nVertex; b++) {
                if (blossomParent[b] == -1 && blossomBase[b] >= 0 && label[b] == 1 && dualVar[b] == 0) {
                    expandBlossom(b, true);
                }
            }
        }

        int[] result = new int[nVertex];
        for (int v = 0; v < nVertex; v++) {
            result[v] = mate[v] >= 0 ? endpoint[mate[v]] : -1;
        }
        return result;
    }
}

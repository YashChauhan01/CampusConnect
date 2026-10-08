package edu.campusconnect.matching;

import edu.campusconnect.matching.MatchingEngine.Analysis;
import edu.campusconnect.matching.MatchingEngine.GraphEdge;
import edu.campusconnect.presence.Presence;
import edu.campusconnect.student.Subject;
import edu.campusconnect.student.SubjectRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Produces a self-contained, explorable picture of a matching round for administrators: the compatibility graph,
 * the optimal matching, the greedy baseline and a check of the optimality conditions that anyone can verify.
 * Nothing is written: it previews the round that would run now.
 */
@Service
public class MatchGraphService {

    private static final int RECENT_ROUNDS = 25;

    public record SubjectLevel(String name, int level) {}

    public record Node(int index, UUID id, String name, String zone, List<SubjectLevel> subjects, Integer partner,
                       int degree) {}

    public record GraphEdgeView(int a, int b, int score, double knowledge, double reciprocity, double breadth,
                                double proximity, List<String> shared, boolean matched) {}

    public record Solution(int pairs, double totalWeight, double millis, int matchedStudents) {}

    /**
     * Necessary conditions for a maximum-weight matching, checked by brute force on the result:
     * no two matched pairs can be re-paired for more weight, no matched student can be swapped for an unmatched one
     * for more weight, and no two unmatched students are connected by an edge.
     */
    public record OptimalityCheck(int pairsChecked, long exchangesTested, int improvingExchanges, boolean passed) {}

    public record GraphView(int poolSize, int edgesTotal, int edgesShown, int topK, List<Node> nodes,
                            List<GraphEdgeView> edges, Solution optimal, Solution greedy, double graphBuildMillis,
                            OptimalityCheck check, List<String> zones) {}

    public record RoundView(java.time.Instant startedAt, int poolSize, int edgeCount, int pairs, double totalWeight,
                            double durationMs) {}

    private final MatchService matchService;
    private final CandidateLoader loader;
    private final MatchingEngine engine;
    private final SubjectRepository subjects;
    private final MatchRoundRepository rounds;
    private final Clock clock;

    public MatchGraphService(MatchService matchService, CandidateLoader loader, MatchingEngine engine,
                             SubjectRepository subjects, MatchRoundRepository rounds, Clock clock) {
        this.matchService = matchService;
        this.loader = loader;
        this.engine = engine;
        this.subjects = subjects;
        this.rounds = rounds;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GraphView graph(int topK) {
        MatchService.PoolSnapshot snapshot = matchService.poolSnapshot(clock.instant());
        List<Presence> pool = snapshot.pool();
        List<Candidate> candidates = loader.load(pool);
        Analysis analysis = engine.analyze(candidates, snapshot.excluded());

        Map<UUID, Integer> indexOf = new HashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            indexOf.put(candidates.get(i).studentId(), i);
        }
        int[] partner = new int[candidates.size()];
        java.util.Arrays.fill(partner, -1);
        for (MatchingEngine.Pair p : analysis.optimal().pairs()) {
            int a = indexOf.get(p.a().studentId());
            int b = indexOf.get(p.b().studentId());
            partner[a] = b;
            partner[b] = a;
        }

        Map<Long, String> subjectNames = subjects.findAllById(candidates.stream()
                        .flatMap(c -> c.levels().keySet().stream()).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getName));

        int[] degree = new int[candidates.size()];
        for (GraphEdge e : analysis.graph()) {
            degree[e.i()]++;
            degree[e.j()]++;
        }
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            Presence presence = pool.get(i);
            List<SubjectLevel> levels = c.levels().entrySet().stream()
                    .map(en -> new SubjectLevel(subjectNames.getOrDefault(en.getKey(), "?"), en.getValue()))
                    .sorted(Comparator.comparing(SubjectLevel::name)).toList();
            nodes.add(new Node(i, c.studentId(), presence.getStudent().getFullName(), presence.getZone().getName(),
                    levels, partner[i] < 0 ? null : partner[i], degree[i]));
        }

        List<GraphEdgeView> edges = selectEdges(analysis.graph(), partner, candidates.size(), topK, subjectNames);
        List<String> zones = pool.stream().map(p -> p.getZone().getName()).distinct().sorted().toList();
        return new GraphView(candidates.size(), analysis.graph().size(), edges.size(), topK, nodes, edges,
                solution(analysis.optimal()), solution(analysis.greedy()), analysis.buildNanos() / 1_000_000.0,
                check(analysis.graph(), partner), zones);
    }

    @Transactional(readOnly = true)
    public List<RoundView> recentRounds() {
        return rounds.recent(PageRequest.of(0, RECENT_ROUNDS)).stream()
                .map(r -> new RoundView(r.getStartedAt(), r.getPoolSize(), r.getEdgeCount(), r.getPairCount(),
                        r.getTotalWeight(), r.getDurationMs())).toList();
    }

    /** All matched edges plus each student's {@code topK} strongest edges, so the picture stays readable. */
    private List<GraphEdgeView> selectEdges(List<GraphEdge> graph, int[] partner, int n, int topK,
                                            Map<Long, String> subjectNames) {
        Map<Integer, List<GraphEdge>> byNode = new HashMap<>();
        for (GraphEdge e : graph) {
            byNode.computeIfAbsent(e.i(), k -> new ArrayList<>()).add(e);
            byNode.computeIfAbsent(e.j(), k -> new ArrayList<>()).add(e);
        }
        Set<GraphEdge> chosen = new HashSet<>();
        for (List<GraphEdge> list : byNode.values()) {
            list.stream().sorted(Comparator.comparingDouble((GraphEdge e) -> e.score().weight()).reversed())
                    .limit(topK).forEach(chosen::add);
        }
        for (GraphEdge e : graph) {
            if (partner[e.i()] == e.j()) {
                chosen.add(e);
            }
        }
        return chosen.stream().sorted(Comparator.comparingInt(GraphEdge::i).thenComparingInt(GraphEdge::j))
                .map(e -> new GraphEdgeView(e.i(), e.j(), (int) Math.round(e.score().weight() * 100),
                        e.score().knowledge(), e.score().reciprocity(), e.score().breadth(), e.score().proximity(),
                        e.score().sharedSubjects().stream().map(id -> subjectNames.getOrDefault(id, "?")).toList(),
                        partner[e.i()] == e.j())).toList();
    }

    private static Solution solution(MatchingEngine.Result r) {
        return new Solution(r.pairs().size(), r.totalWeight(), r.nanos() / 1_000_000.0, r.pairs().size() * 2);
    }

    /** Brute-force verification of the optimality conditions listed on {@link OptimalityCheck}. */
    static OptimalityCheck check(List<GraphEdge> graph, int[] partner) {
        int n = partner.length;
        Map<Long, Long> weight = new HashMap<>(graph.size() * 2);
        for (GraphEdge e : graph) {
            weight.put(key(e.i(), e.j(), n), e.weight());
        }
        List<int[]> matched = new ArrayList<>();
        List<Integer> unmatched = new ArrayList<>();
        for (int v = 0; v < n; v++) {
            if (partner[v] < 0) {
                unmatched.add(v);
            } else if (v < partner[v]) {
                matched.add(new int[]{v, partner[v]});
            }
        }
        long tested = 0;
        int improving = 0;
        // Two matched pairs re-paired the other two ways.
        for (int x = 0; x < matched.size(); x++) {
            for (int y = x + 1; y < matched.size(); y++) {
                int[] p = matched.get(x);
                int[] q = matched.get(y);
                long current = weight.get(key(p[0], p[1], n)) + weight.get(key(q[0], q[1], n));
                long alt1 = w(weight, p[0], q[0], n) + w(weight, p[1], q[1], n);
                long alt2 = w(weight, p[0], q[1], n) + w(weight, p[1], q[0], n);
                tested += 2;
                if (valid(weight, p[0], q[0], p[1], q[1], n) && alt1 > current) improving++;
                if (valid(weight, p[0], q[1], p[1], q[0], n) && alt2 > current) improving++;
            }
        }
        // A matched student swapped for an unmatched one.
        for (int[] p : matched) {
            long current = weight.get(key(p[0], p[1], n));
            for (int u : unmatched) {
                tested += 2;
                if (weight.containsKey(key(p[0], u, n)) && weight.get(key(p[0], u, n)) > current) improving++;
                if (weight.containsKey(key(p[1], u, n)) && weight.get(key(p[1], u, n)) > current) improving++;
            }
        }
        // Two unmatched students that could still be paired.
        for (int x = 0; x < unmatched.size(); x++) {
            for (int y = x + 1; y < unmatched.size(); y++) {
                tested++;
                Long w = weight.get(key(unmatched.get(x), unmatched.get(y), n));
                if (w != null && w > 0) improving++;
            }
        }
        return new OptimalityCheck(matched.size(), tested, improving, improving == 0);
    }

    private static long key(int a, int b, int n) {
        return a < b ? (long) a * n + b : (long) b * n + a;
    }

    private static long w(Map<Long, Long> weight, int a, int b, int n) {
        return weight.getOrDefault(key(a, b, n), 0L);
    }

    private static boolean valid(Map<Long, Long> weight, int a, int b, int c, int d, int n) {
        return weight.containsKey(key(a, b, n)) && weight.containsKey(key(c, d, n));
    }
}

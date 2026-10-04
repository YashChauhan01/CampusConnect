package edu.campusconnect.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchingEngineTest {

    private final MatchingEngine engine = new MatchingEngine(new CompatibilityScorer(CompatibilityScorerTest.props()));

    static List<Candidate> randomPool(Random random, int n) {
        List<Candidate> pool = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<Long, Integer> levels = new HashMap<>();
            int subjects = 1 + random.nextInt(4);
            for (int s = 0; s < subjects; s++) {
                levels.put((long) random.nextInt(8), 1 + random.nextInt(3));
            }
            long zone = random.nextInt(4);
            pool.add(new Candidate(UUID.randomUUID(), zone, zone * 70.0, (zone % 2) * 40.0, levels,
                    new HashSet<>(levels.keySet())));
        }
        return pool;
    }

    @Test
    void pairsTheCompatibleStudentsAndLeavesOthersOut() {
        Candidate a = CompatibilityScorerTest.student(1, 0.0, 0.0, Map.of(1L, 3), Set.of(1L));
        Candidate b = CompatibilityScorerTest.student(1, 0.0, 0.0, Map.of(1L, 2), Set.of(1L));
        Candidate loner = CompatibilityScorerTest.student(1, 0.0, 0.0, Map.of(9L, 2), Set.of(9L));

        MatchingEngine.Result result = engine.match(List.of(a, b, loner), null);

        assertEquals(1, result.pairs().size());
        MatchingEngine.Pair pair = result.pairs().get(0);
        assertEquals(Set.of(a.studentId(), b.studentId()), Set.of(pair.a().studentId(), pair.b().studentId()));
        assertEquals(3, result.poolSize());
        assertEquals(1, result.edgeCount());
    }

    @Test
    void respectsExcludedPairs() {
        Candidate a = CompatibilityScorerTest.student(1, 0.0, 0.0, Map.of(1L, 3), Set.of(1L));
        Candidate b = CompatibilityScorerTest.student(1, 0.0, 0.0, Map.of(1L, 2), Set.of(1L));
        assertTrue(engine.match(List.of(a, b), (x, y) -> true).pairs().isEmpty());
    }

    @Test
    void emptyAndSingletonPoolsProduceNoPairs() {
        assertTrue(engine.match(List.of(), null).pairs().isEmpty());
        Candidate only = CompatibilityScorerTest.student(1, 0.0, 0.0, Map.of(1L, 1), Set.of(1L));
        assertTrue(engine.match(List.of(only), null).pairs().isEmpty());
    }

    @Test
    void optimalNeverWorseThanGreedyAndOftenBetter() {
        Random random = new Random(2024);
        int strictlyBetter = 0;
        for (int round = 0; round < 300; round++) {
            List<Candidate> pool = randomPool(random, 6 + random.nextInt(25));
            MatchingEngine.Result optimal = engine.match(pool, null);
            MatchingEngine.Result greedy = engine.greedy(pool, null);

            assertTrue(optimal.totalWeightScaled() >= greedy.totalWeightScaled(),
                    "optimal " + optimal.totalWeightScaled() + " < greedy " + greedy.totalWeightScaled());
            if (optimal.totalWeightScaled() > greedy.totalWeightScaled()) {
                strictlyBetter++;
            }
            assertDisjoint(optimal);
            assertDisjoint(greedy);
        }
        assertTrue(strictlyBetter > 0, "expected the optimal matching to beat greedy at least once");
    }

    private static void assertDisjoint(MatchingEngine.Result result) {
        Set<UUID> seen = new HashSet<>();
        for (MatchingEngine.Pair p : result.pairs()) {
            assertTrue(seen.add(p.a().studentId()), "student matched twice");
            assertTrue(seen.add(p.b().studentId()), "student matched twice");
        }
    }
}

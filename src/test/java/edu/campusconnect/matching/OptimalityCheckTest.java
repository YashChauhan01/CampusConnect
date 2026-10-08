package edu.campusconnect.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusconnect.matching.MatchingEngine.Analysis;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OptimalityCheckTest {

    private final MatchingEngine engine = new MatchingEngine(new CompatibilityScorer(CompatibilityScorerTest.props()));

    private static int[] partners(List<Candidate> pool, MatchingEngine.Result result) {
        Map<UUID, Integer> index = new HashMap<>();
        for (int i = 0; i < pool.size(); i++) {
            index.put(pool.get(i).studentId(), i);
        }
        int[] partner = new int[pool.size()];
        Arrays.fill(partner, -1);
        for (MatchingEngine.Pair p : result.pairs()) {
            int a = index.get(p.a().studentId());
            int b = index.get(p.b().studentId());
            partner[a] = b;
            partner[b] = a;
        }
        return partner;
    }

    @Test
    void exactSolutionsAlwaysSatisfyTheOptimalityConditions() {
        Random random = new Random(314);
        for (int round = 0; round < 250; round++) {
            List<Candidate> pool = MatchingEngineTest.randomPool(random, 10 + random.nextInt(60));
            Analysis analysis = engine.analyze(pool, null);

            MatchGraphService.OptimalityCheck check =
                    MatchGraphService.check(analysis.graph(), partners(pool, analysis.optimal()));

            assertEquals(0, check.improvingExchanges(), "round " + round);
            assertTrue(check.passed());
        }
    }

    @Test
    void theCheckIsNotVacuousItFlagsSuboptimalGreedyMatchings() {
        Random random = new Random(314);
        int flagged = 0;
        for (int round = 0; round < 250; round++) {
            List<Candidate> pool = MatchingEngineTest.randomPool(random, 10 + random.nextInt(60));
            Analysis analysis = engine.analyze(pool, null);
            boolean greedyIsWorse = analysis.greedy().totalWeightScaled() < analysis.optimal().totalWeightScaled();

            MatchGraphService.OptimalityCheck check =
                    MatchGraphService.check(analysis.graph(), partners(pool, analysis.greedy()));

            if (!check.passed()) {
                flagged++;
                assertTrue(greedyIsWorse, "a matching can only be flagged if it is genuinely suboptimal");
            }
        }
        assertTrue(flagged > 0, "expected the check to catch at least one suboptimal greedy matching");
    }

    @Test
    void analysisSolvesBothWaysFromOneGraph() {
        List<Candidate> pool = MatchingEngineTest.randomPool(new Random(5), 30);
        Analysis analysis = engine.analyze(pool, null);

        assertEquals(engine.match(pool, null).totalWeightScaled(), analysis.optimal().totalWeightScaled());
        assertEquals(engine.greedy(pool, null).totalWeightScaled(), analysis.greedy().totalWeightScaled());
        assertEquals(analysis.graph().size(), analysis.optimal().edgeCount());
    }
}

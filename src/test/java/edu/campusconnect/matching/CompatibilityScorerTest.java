package edu.campusconnect.matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompatibilityScorerTest {

    static MatchingProperties props() {
        return new MatchingProperties(false, Duration.ofSeconds(20), Duration.ofMinutes(5), Duration.ofHours(3),
                Duration.ofDays(1), Duration.ofSeconds(5), 0.30, 120, 0.3, 500,
                new MatchingProperties.Weights(0.45, 0.15, 0.15, 0.25));
    }

    static Candidate student(long zone, Double x, Double y, Map<Long, Integer> levels, Set<Long> active) {
        return new Candidate(UUID.randomUUID(), zone, x, y, levels, active);
    }

    private final CompatibilityScorer scorer = new CompatibilityScorer(props());

    @Test
    void noSharedSubjectMeansNoEdge() {
        Candidate a = student(1, 0.0, 0.0, Map.of(1L, 3), Set.of(1L));
        Candidate b = student(1, 0.0, 0.0, Map.of(2L, 3), Set.of(2L));
        assertTrue(scorer.score(a, b).isEmpty());
    }

    @Test
    void sharedSubjectNobodyWantsToWorkOnIsIgnored() {
        Candidate a = student(1, 0.0, 0.0, Map.of(1L, 3, 2L, 2), Set.of(2L));
        Candidate b = student(1, 0.0, 0.0, Map.of(1L, 1), Set.of(1L));
        // b actively wants subject 1, so it counts even though a is working on 2.
        assertEquals(List.of(1L), scorer.score(a, b).orElseThrow().sharedSubjects());

        Candidate c = student(1, 0.0, 0.0, Map.of(1L, 1, 3L, 1), Set.of(3L));
        assertTrue(scorer.score(a, c).isEmpty(), "subject 1 is shared but neither is working on it");
    }

    @Test
    void oneLevelGapBeatsTwoLevelGapBeatsEqualLevels() {
        Candidate base = student(1, 0.0, 0.0, Map.of(1L, 2), Set.of(1L));
        double equal = scorer.score(base, student(1, 0.0, 0.0, Map.of(1L, 2), Set.of(1L))).orElseThrow().weight();
        double gap1 = scorer.score(base, student(1, 0.0, 0.0, Map.of(1L, 3), Set.of(1L))).orElseThrow().weight();
        double gap2 = scorer.score(student(1, 0.0, 0.0, Map.of(1L, 1), Set.of(1L)),
                student(1, 0.0, 0.0, Map.of(1L, 3), Set.of(1L))).orElseThrow().weight();
        assertTrue(gap1 > gap2, "gap1=" + gap1 + " gap2=" + gap2);
        assertTrue(gap2 > equal, "gap2=" + gap2 + " equal=" + equal);
    }

    @Test
    void mutualTeachingEarnsReciprocityBonus() {
        Candidate a = student(1, 0.0, 0.0, Map.of(1L, 3, 2L, 1), Set.of(1L, 2L));
        Candidate b = student(1, 0.0, 0.0, Map.of(1L, 1, 2L, 3), Set.of(1L, 2L));
        Candidate c = student(1, 0.0, 0.0, Map.of(1L, 1, 2L, 1), Set.of(1L, 2L));
        assertEquals(1.0, scorer.score(a, b).orElseThrow().reciprocity());
        assertEquals(0.0, scorer.score(a, c).orElseThrow().reciprocity());
    }

    @Test
    void closerZonesScoreHigher() {
        Map<Long, Integer> levels = Map.of(1L, 2);
        Candidate a = student(1, 0.0, 0.0, levels, Set.of(1L));
        double same = scorer.score(a, student(1, 0.0, 0.0, levels, Set.of(1L))).orElseThrow().proximity();
        double near = scorer.score(a, student(2, 50.0, 0.0, levels, Set.of(1L))).orElseThrow().proximity();
        double far = scorer.score(a, student(3, 400.0, 0.0, levels, Set.of(1L))).orElseThrow().proximity();
        double unknown = scorer.score(a, student(4, null, null, levels, Set.of(1L))).orElseThrow().proximity();
        assertEquals(1.0, same);
        assertTrue(same > near && near > far && far > 0);
        assertEquals(0.3, unknown, 1e-9);
    }

    @Test
    void weightStaysWithinUnitInterval() {
        Candidate a = student(1, 0.0, 0.0, Map.of(1L, 3, 2L, 1, 3L, 2), Set.of(1L, 2L, 3L));
        Candidate b = student(1, 0.0, 0.0, Map.of(1L, 1, 2L, 3, 3L, 1), Set.of(1L, 2L, 3L));
        double w = scorer.score(a, b).orElseThrow().weight();
        assertTrue(w > 0.9 && w <= 1.0, "w=" + w);
    }

    @Test
    void edgeAppliesTheMinimumWeightCutOff() {
        // Far apart, one equal-level subject: 0.65*0.45 + (1/3)*0.15 + ~0 = ~0.34.
        Candidate a = student(1, 0.0, 0.0, Map.of(1L, 2), Set.of(1L));
        Candidate b = student(2, 2000.0, 0.0, Map.of(1L, 2), Set.of(1L));
        double weight = scorer.score(a, b).orElseThrow().weight();
        assertEquals(0.3425, weight, 1e-3);
        assertTrue(scorer.edge(a, b).isPresent());

        MatchingProperties p = props();
        MatchingProperties strict = new MatchingProperties(p.schedulerEnabled(), p.roundInterval(), p.proposalTtl(),
                p.acceptedTtl(), p.declineCooldown(), p.onDemandCooldown(), 0.5, p.proximityScaleMeters(),
                p.unknownDistanceProximity(), p.maxPoolSize(), p.weights());
        assertTrue(new CompatibilityScorer(strict).edge(a, b).isEmpty());
    }

    @Test
    void rejectsAllZeroWeights() {
        MatchingProperties p = props();
        MatchingProperties zero = new MatchingProperties(p.schedulerEnabled(), p.roundInterval(), p.proposalTtl(),
                p.acceptedTtl(), p.declineCooldown(), p.onDemandCooldown(), p.minEdgeWeight(),
                p.proximityScaleMeters(), p.unknownDistanceProximity(), p.maxPoolSize(),
                new MatchingProperties.Weights(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CompatibilityScorer(zero));
    }
}

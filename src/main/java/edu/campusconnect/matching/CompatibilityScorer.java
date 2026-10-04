package edu.campusconnect.matching;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Edge-weight model for peer matching. For two students {@code u}, {@code v}:
 *
 * <ul>
 *   <li>{@code S} = subjects both know that at least one of them wants to work on today. No shared subject → no
 *       edge: the pair is not academically compatible.</li>
 *   <li><b>knowledge</b> = mean over {@code S} of {@code f(|level_u − level_v|)}, where a one-level gap (someone can
 *       explain to someone) scores highest, equals score well (study together) and a two-level gap (one-way
 *       mentoring) a little less: {@code f = [0.65, 1.0, 0.75]}.</li>
 *   <li><b>reciprocity</b> = 1 if each student is stronger in at least one subject of {@code S} (they can teach
 *       each other), else 0.</li>
 *   <li><b>breadth</b> = {@code min(1, |S| / 3)}: more common ground, more to work on together.</li>
 *   <li><b>proximity</b> = {@code exp(−distance / scale)} between their zones; 1 within one zone, a configurable
 *       neutral value when a zone has no map position.</li>
 * </ul>
 *
 * The weight is the convex combination of the four components using {@link MatchingProperties.Weights}, so it lies
 * in [0, 1]. Pairs below {@code minEdgeWeight} are dropped to keep the graph meaningful and sparse.
 */
public class CompatibilityScorer {

    /** Score for gap 0, 1, 2 (larger gaps are clamped to the last entry). */
    private static final double[] GAP_SCORE = {0.65, 1.0, 0.75};
    private static final int BREADTH_TARGET = 3;

    public record Score(double weight, double knowledge, double reciprocity, double breadth, double proximity,
                        List<Long> sharedSubjects) {}

    private final MatchingProperties props;
    private final double weightSum;

    public CompatibilityScorer(MatchingProperties props) {
        this.props = props;
        this.weightSum = props.weights().sum();
        if (weightSum <= 0) {
            throw new IllegalArgumentException("Matching weights must not all be zero");
        }
    }

    /** Full score regardless of the minimum-weight cut-off, or empty if the students share no subject of interest. */
    public Optional<Score> score(Candidate u, Candidate v) {
        Set<Long> interesting = new HashSet<>(u.activeSubjects());
        interesting.addAll(v.activeSubjects());
        List<Long> shared = new ArrayList<>();
        for (Long subject : u.levels().keySet()) {
            if (v.levels().containsKey(subject) && interesting.contains(subject)) {
                shared.add(subject);
            }
        }
        if (shared.isEmpty()) {
            return Optional.empty();
        }
        shared.sort(Long::compare);

        double knowledgeSum = 0;
        boolean uTeaches = false;
        boolean vTeaches = false;
        for (Long subject : shared) {
            int lu = u.levels().get(subject);
            int lv = v.levels().get(subject);
            int gap = Math.abs(lu - lv);
            knowledgeSum += GAP_SCORE[Math.min(gap, GAP_SCORE.length - 1)];
            uTeaches |= lu > lv;
            vTeaches |= lv > lu;
        }
        double knowledge = knowledgeSum / shared.size();
        double reciprocity = uTeaches && vTeaches ? 1.0 : 0.0;
        double breadth = Math.min(1.0, shared.size() / (double) BREADTH_TARGET);
        double proximity = proximity(u, v);

        MatchingProperties.Weights w = props.weights();
        double weight = (w.knowledge() * knowledge + w.reciprocity() * reciprocity
                + w.breadth() * breadth + w.proximity() * proximity) / weightSum;
        return Optional.of(new Score(weight, knowledge, reciprocity, breadth, proximity, List.copyOf(shared)));
    }

    /** Score only if the pair qualifies as an edge of the matching graph. */
    public Optional<Score> edge(Candidate u, Candidate v) {
        return score(u, v).filter(s -> s.weight() >= props.minEdgeWeight());
    }

    double proximity(Candidate u, Candidate v) {
        if (u.zoneId().equals(v.zoneId())) {
            return 1.0;
        }
        if (!u.hasPosition() || !v.hasPosition()) {
            return props.unknownDistanceProximity();
        }
        double distance = Math.hypot(u.x() - v.x(), u.y() - v.y());
        return Math.exp(-distance / props.proximityScaleMeters());
    }
}

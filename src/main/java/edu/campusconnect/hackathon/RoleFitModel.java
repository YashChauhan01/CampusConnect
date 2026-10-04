package edu.campusconnect.hackathon;

import edu.campusconnect.hackathon.TeamSynthesizer.Participant;
import edu.campusconnect.hackathon.TeamSynthesizer.Role;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * How well a participant suits a role, in [0, 1]:
 * {@code fit = 0.6 · skillMatch + 0.4 · preference}.
 *
 * <ul>
 *   <li><b>skillMatch</b>: the participant's skills whose names match the role's keywords (substring match in
 *       either direction, case-insensitive) give {@code 0.7 · bestLevel/3 + 0.3 · min(1, matches/3)}.</li>
 *   <li><b>preference</b>: 1.0 / 0.7 / 0.4 for first / second / third choice, 0.2 for lower ranks, 0 if the role
 *       was not ranked.</li>
 * </ul>
 */
public class RoleFitModel {

    private static final double SKILL_WEIGHT = 0.6;
    private static final double PREFERENCE_WEIGHT = 0.4;
    private static final double[] PREFERENCE_SCORE = {1.0, 0.7, 0.4};
    private static final double LOW_RANK_SCORE = 0.2;
    private static final int STRENGTH_SKILLS = 5;
    private static final int MAX_LEVEL = 3;

    public double fit(Participant participant, Role role) {
        return SKILL_WEIGHT * skillMatch(participant.skills(), role.keywords())
                + PREFERENCE_WEIGHT * preference(participant.rolePreferences(), role.id());
    }

    double skillMatch(Map<String, Integer> skills, List<String> keywords) {
        int best = 0;
        int matches = 0;
        for (Map.Entry<String, Integer> skill : skills.entrySet()) {
            String name = TeamSynthesizer.norm(skill.getKey());
            boolean match = keywords.stream().map(TeamSynthesizer::norm)
                    .anyMatch(k -> !k.isEmpty() && (name.contains(k) || k.contains(name)));
            if (match) {
                matches++;
                best = Math.max(best, skill.getValue());
            }
        }
        if (matches == 0) {
            return 0;
        }
        return 0.7 * best / MAX_LEVEL + 0.3 * Math.min(1.0, matches / 3.0);
    }

    double preference(List<Long> preferences, Long roleId) {
        int rank = preferences.indexOf(roleId);
        if (rank < 0) {
            return 0;
        }
        return rank < PREFERENCE_SCORE.length ? PREFERENCE_SCORE[rank] : LOW_RANK_SCORE;
    }

    /** Overall technical strength regardless of role: mean of the five best skill levels, scaled to [0, 1]. */
    public double generalStrength(Participant participant) {
        return participant.skills().values().stream()
                .sorted(Comparator.reverseOrder()).limit(STRENGTH_SKILLS)
                .mapToInt(Integer::intValue).average().orElse(0) / MAX_LEVEL;
    }
}

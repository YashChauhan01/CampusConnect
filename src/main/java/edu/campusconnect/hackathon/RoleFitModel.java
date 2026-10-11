package edu.campusconnect.hackathon;

import edu.campusconnect.hackathon.TeamSynthesizer.Participant;
import edu.campusconnect.hackathon.TeamSynthesizer.Role;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * How well a participant suits a role, in [0, 1]:
 * {@code fit = 0.6 · skillMatch + 0.4 · preference}.
 *
 * <ul>
 *   <li><b>skillMatch</b>: the participant's skills whose names match the role's keywords give
 *       {@code 0.7 · bestLevel/3 + 0.3 · min(1, matches/3)}. Matching is on whole tokens, never substrings: names
 *       are lower-cased, trimmed, mapped through a small alias table ({@code js → javascript},
 *       {@code postgres → postgresql}, …) and split on whitespace and {@code / , ( ) & _}; a skill matches a keyword
 *       when either token sequence occurs as a contiguous run in the other. So "spring boot" matches "spring", but
 *       "c" does not match "c++", "css" or "docker", "go" does not match "mongodb", and "java" does not match
 *       "javascript".</li>
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
    private static final Pattern SEPARATORS = Pattern.compile("[\\s/,()&_]+");
    /** Common spellings mapped to one canonical token; applied to the whole name and to each token. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("js", "javascript"), Map.entry("ts", "typescript"),
            Map.entry("node.js", "node"), Map.entry("nodejs", "node"),
            Map.entry("react.js", "react"), Map.entry("reactjs", "react"),
            Map.entry("postgres", "postgresql"), Map.entry("golang", "go"), Map.entry("k8s", "kubernetes"),
            Map.entry("py", "python"), Map.entry("cpp", "c++"), Map.entry("csharp", "c#"),
            Map.entry("machine learning", "ml"), Map.entry("ui/ux", "ux"));

    public double fit(Participant participant, Role role) {
        return SKILL_WEIGHT * skillMatch(participant.skills(), role.keywords())
                + PREFERENCE_WEIGHT * preference(participant.rolePreferences(), role.id());
    }

    double skillMatch(Map<String, Integer> skills, List<String> keywords) {
        int best = 0;
        int matches = 0;
        List<List<String>> wanted = keywords.stream().map(RoleFitModel::tokens).filter(k -> !k.isEmpty()).toList();
        for (Map.Entry<String, Integer> skill : skills.entrySet()) {
            List<String> name = tokens(skill.getKey());
            boolean match = !name.isEmpty() && wanted.stream()
                    .anyMatch(k -> Collections.indexOfSubList(name, k) >= 0 || Collections.indexOfSubList(k, name) >= 0);
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

    /** Normalised, alias-mapped tokens of a skill name or keyword; "c++" and "c" stay distinct whole tokens. */
    static List<String> tokens(String raw) {
        String whole = TeamSynthesizer.norm(raw);
        whole = ALIASES.getOrDefault(whole, whole);
        return Arrays.stream(SEPARATORS.split(whole)).filter(t -> !t.isEmpty())
                .map(t -> ALIASES.getOrDefault(t, t)).toList();
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

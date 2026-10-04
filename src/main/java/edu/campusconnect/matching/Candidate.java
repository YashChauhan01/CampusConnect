package edu.campusconnect.matching;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A student currently in the matching pool, reduced to what the compatibility model needs.
 *
 * @param levels         subject id → proficiency weight (1 = beginner … 3 = advanced)
 * @param activeSubjects subjects the student wants to work on today (explicitly chosen, or inferred from the
 *                       free-text requirement, or all their subjects if nothing was specified)
 * @param x              planar position of the checked-in zone in metres; {@code null} if the zone is unplaced
 */
public record Candidate(UUID studentId, Long zoneId, Double x, Double y,
                        Map<Long, Integer> levels, Set<Long> activeSubjects) {

    public boolean hasPosition() {
        return x != null && y != null;
    }
}

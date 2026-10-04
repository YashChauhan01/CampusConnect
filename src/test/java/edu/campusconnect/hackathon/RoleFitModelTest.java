package edu.campusconnect.hackathon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusconnect.hackathon.TeamSynthesizer.Participant;
import edu.campusconnect.hackathon.TeamSynthesizer.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoleFitModelTest {

    private final RoleFitModel model = new RoleFitModel();
    private final Role backend = new Role(1L, "Backend", List.of("Java", "Spring"));

    private static Participant person(Map<String, Integer> skills, Long... prefs) {
        return new Participant(UUID.randomUUID(), skills, List.of(prefs));
    }

    @Test
    void perfectMatchScoresOne() {
        Participant p = person(Map.of("java", 3, "spring boot", 3, "java ee", 3), 1L);
        assertEquals(1.0, model.fit(p, backend), 1e-9);
    }

    @Test
    void noRelevantSkillAndNoPreferenceScoresZero() {
        assertEquals(0.0, model.fit(person(Map.of("figma", 3), 2L), backend), 1e-9);
    }

    @Test
    void skillMatchIsCaseInsensitiveAndMatchesSubstringsEitherWay() {
        double fit = model.skillMatch(Map.of("JavaScript", 2), List.of("java"));
        assertTrue(fit > 0, "'javascript' contains keyword 'java'");
        assertTrue(model.skillMatch(Map.of("c", 2), List.of("c++", "c")) > 0);
    }

    @Test
    void higherProficiencyAndHigherPreferenceScoreHigher() {
        double beginner = model.fit(person(Map.of("java", 1), 1L), backend);
        double advanced = model.fit(person(Map.of("java", 3), 1L), backend);
        double thirdChoice = model.fit(person(Map.of("java", 3), 9L, 8L, 1L), backend);
        assertTrue(advanced > beginner);
        assertTrue(advanced > thirdChoice);
    }

    @Test
    void preferenceDecaysWithRank() {
        assertEquals(1.0, model.preference(List.of(1L, 2L, 3L), 1L));
        assertEquals(0.7, model.preference(List.of(1L, 2L, 3L), 2L));
        assertEquals(0.4, model.preference(List.of(1L, 2L, 3L), 3L));
        assertEquals(0.2, model.preference(List.of(9L, 8L, 7L, 1L), 1L));
        assertEquals(0.0, model.preference(List.of(2L), 1L));
    }

    @Test
    void generalStrengthAveragesTheBestFiveSkills() {
        Participant p = person(Map.of("a", 3, "b", 3, "c", 3, "d", 3, "e", 3, "f", 1));
        assertEquals(1.0, model.generalStrength(p), 1e-9);
        assertEquals(0.0, model.generalStrength(person(Map.of())), 1e-9);
    }
}

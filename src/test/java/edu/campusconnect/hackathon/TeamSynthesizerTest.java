package edu.campusconnect.hackathon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusconnect.hackathon.TeamSynthesizer.Member;
import edu.campusconnect.hackathon.TeamSynthesizer.Participant;
import edu.campusconnect.hackathon.TeamSynthesizer.Role;
import edu.campusconnect.hackathon.TeamSynthesizer.Synthesis;
import edu.campusconnect.hackathon.TeamSynthesizer.Team;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeamSynthesizerTest {

    private static final Role BACKEND = new Role(1L, "Backend", List.of("java", "spring", "sql"));
    private static final Role FRONTEND = new Role(2L, "Frontend", List.of("react", "css"));
    private static final Role DATA = new Role(3L, "Data", List.of("python", "ml"));
    private static final List<Role> ROLES = List.of(BACKEND, FRONTEND, DATA);

    private final RoleFitModel fitModel = new RoleFitModel();
    private final TeamSynthesizer synthesizer = new TeamSynthesizer(fitModel);

    private static Participant person(Map<String, Integer> skills, Long... prefs) {
        return new Participant(UUID.randomUUID(), skills, List.of(prefs));
    }

    private static Participant randomPerson(Random random) {
        String[] pool = {"java", "spring", "react", "css", "python", "ml", "sql", "figma", "go"};
        Map<String, Integer> skills = new HashMap<>();
        int count = 1 + random.nextInt(4);
        for (int i = 0; i < count; i++) {
            skills.put(pool[random.nextInt(pool.length)], 1 + random.nextInt(3));
        }
        List<Long> prefs = new ArrayList<>(List.of(1L, 2L, 3L));
        java.util.Collections.shuffle(prefs, random);
        return new Participant(UUID.randomUUID(), skills, prefs.subList(0, 1 + random.nextInt(3)));
    }

    private static void assertValid(Synthesis result, List<Participant> participants, List<Role> roles) {
        Set<UUID> placed = new HashSet<>();
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (Team team : result.teams()) {
            Set<Long> rolesInTeam = new HashSet<>();
            for (Member m : team.members()) {
                assertTrue(placed.add(m.participantId()), "participant placed twice");
                assertTrue(rolesInTeam.add(m.roleId()), "role duplicated within team " + team.number());
            }
            min = Math.min(min, team.members().size());
            max = Math.max(max, team.members().size());
            assertTrue(team.members().size() <= roles.size());
        }
        assertEquals(participants.size(), placed.size(), "everyone is placed");
        assertTrue(max - min <= 1, "team sizes differ by more than one");
    }

    @Test
    void specialistsLandInTheirRoleInEveryTeam() {
        List<Participant> people = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            people.add(person(Map.of("java", 3), 1L));
            people.add(person(Map.of("react", 3), 2L));
            people.add(person(Map.of("python", 3), 3L));
        }
        Synthesis result = synthesizer.synthesize(people, ROLES);

        assertEquals(2, result.teams().size());
        assertValid(result, people, ROLES);
        for (Team team : result.teams()) {
            assertEquals(3, team.members().size());
            for (Member m : team.members()) {
                assertTrue(m.fit() > 0.85, "specialist should fit its role, fit=" + m.fit());
            }
        }
        assertEquals(1.0, result.quality().preferenceSatisfaction(), 1e-9);
    }

    @Test
    void roleAllocationIsOptimalComparedToExhaustiveSearch() {
        Random random = new Random(11);
        for (int round = 0; round < 60; round++) {
            int n = 3 + random.nextInt(4); // 3..6 participants -> at most 720 permutations
            List<Participant> people = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                people.add(randomPerson(random));
            }
            Synthesis result = synthesizer.synthesize(people, ROLES);

            double achieved = result.teams().stream().flatMap(t -> t.members().stream()).mapToDouble(Member::fit).sum();
            assertEquals(bestTotalFit(people, n), achieved, 1e-4, "round " + round);
        }
    }

    /** Maximum total fit over all ways to fill the slot list (team sizes as the synthesizer chooses them). */
    private double bestTotalFit(List<Participant> people, int n) {
        int teams = (n + ROLES.size() - 1) / ROLES.size();
        int base = n / teams;
        int larger = n % teams;
        List<Integer> slotRoles = new ArrayList<>();
        for (int t = 0; t < teams; t++) {
            for (int r = 0; r < base + (t < larger ? 1 : 0); r++) {
                slotRoles.add(r);
            }
        }
        return permute(people, slotRoles, 0, new boolean[n]);
    }

    private double permute(List<Participant> people, List<Integer> slotRoles, int slot, boolean[] used) {
        if (slot == slotRoles.size()) {
            return 0;
        }
        double best = Double.NEGATIVE_INFINITY;
        for (int p = 0; p < people.size(); p++) {
            if (!used[p]) {
                used[p] = true;
                double value = fitModel.fit(people.get(p), ROLES.get(slotRoles.get(slot)))
                        + permute(people, slotRoles, slot + 1, used);
                best = Math.max(best, value);
                used[p] = false;
            }
        }
        return best;
    }

    @Test
    void teamSizesAreBalancedForAwkwardParticipantCounts() {
        Random random = new Random(3);
        for (int n = 1; n <= 40; n++) {
            List<Participant> people = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                people.add(randomPerson(random));
            }
            assertValid(synthesizer.synthesize(people, ROLES), people, ROLES);
        }
    }

    @Test
    void fewerParticipantsThanRolesFormOneTeam() {
        List<Participant> people = List.of(person(Map.of("java", 2), 1L), person(Map.of("react", 2), 2L));
        Synthesis result = synthesizer.synthesize(people, ROLES);
        assertEquals(1, result.teams().size());
        assertEquals(2, result.teams().get(0).members().size());
    }

    @Test
    void emptyInputAndMissingRoles() {
        assertTrue(synthesizer.synthesize(List.of(), ROLES).teams().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> synthesizer.synthesize(List.of(person(Map.of())), List.of()));
    }

    @Test
    void synthesisBalancesStrengthAndFitBetterThanRandomAssignment() {
        Random random = new Random(77);
        double synthSpread = 0;
        double randomSpread = 0;
        double synthFit = 0;
        double randomFit = 0;
        int trials = 40;
        for (int trial = 0; trial < trials; trial++) {
            List<Participant> people = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                people.add(randomPerson(random));
            }
            Synthesis optimised = synthesizer.synthesize(people, ROLES);
            Synthesis baseline = synthesizer.randomBaseline(people, ROLES, random);
            synthSpread += optimised.quality().strengthStdDev();
            randomSpread += baseline.quality().strengthStdDev();
            synthFit += optimised.quality().averageFit();
            randomFit += baseline.quality().averageFit();
        }
        assertTrue(synthFit > randomFit, "fit " + synthFit + " vs " + randomFit);
        assertTrue(synthSpread < randomSpread, "balance " + synthSpread + " vs " + randomSpread);
    }

    @Test
    void identicalParticipantsAreSplitEvenly() {
        List<Participant> people = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            people.add(person(Map.of("java", 2, "react", 2), 1L, 2L, 3L));
        }
        Synthesis result = synthesizer.synthesize(people, ROLES);
        assertValid(result, people, ROLES);
        assertEquals(0.0, result.quality().strengthSpread(), 1e-9);
    }
}

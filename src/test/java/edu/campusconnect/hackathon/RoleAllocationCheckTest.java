package edu.campusconnect.hackathon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusconnect.hackathon.TeamSynthesizer.Member;
import edu.campusconnect.hackathon.TeamSynthesizer.Participant;
import edu.campusconnect.hackathon.TeamSynthesizer.Role;
import edu.campusconnect.hackathon.TeamSynthesizer.Synthesis;
import edu.campusconnect.hackathon.TeamSynthesizer.Team;
import edu.campusconnect.matching.algo.HungarianAlgorithm;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoleAllocationCheckTest {

    private static final List<Role> ROLES = List.of(
            new Role(1L, "Backend", List.of("java", "spring", "sql", "go")),
            new Role(2L, "Frontend", List.of("react", "css", "typescript")),
            new Role(3L, "Data / ML", List.of("python", "ml", "pandas")),
            new Role(4L, "Design", List.of("figma", "ux")),
            new Role(5L, "Product", List.of("pitch")));
    private static final String[] SKILLS = {"java", "spring", "sql", "go", "react", "css", "typescript", "python", "ml",
            "pandas", "figma", "ux", "pitch", "docker", "git"};

    private final RoleFitModel fitModel = new RoleFitModel();
    private final TeamSynthesizer synthesizer = new TeamSynthesizer(fitModel);

    private static Participant randomPerson(Random random, int roleCount) {
        Map<String, Integer> skills = new HashMap<>();
        int count = 2 + random.nextInt(5);
        for (int i = 0; i < count; i++) {
            skills.put(SKILLS[random.nextInt(SKILLS.length)], 1 + random.nextInt(3));
        }
        List<Long> prefs = new ArrayList<>();
        for (long r = 1; r <= roleCount; r++) {
            prefs.add(r);
        }
        Collections.shuffle(prefs, random);
        return new Participant(UUID.randomUUID(), skills, prefs.subList(0, random.nextInt(4)));
    }

    /** One pseudo-team holding everybody: the check only looks at who holds which role. */
    private List<Team> allocation(List<Participant> people, List<Role> roles, int[] roleOf) {
        List<Member> members = new ArrayList<>();
        for (int p = 0; p < people.size(); p++) {
            members.add(new Member(people.get(p).id(), roles.get(roleOf[p]).id(),
                    fitModel.fit(people.get(p), roles.get(roleOf[p])), 0));
        }
        return List.of(new Team(1, members));
    }

    private long cost(List<Participant> people, List<Role> roles, int[] roleOf) {
        long total = 0;
        for (int p = 0; p < people.size(); p++) {
            total += HungarianAlgorithm.scale(1.0 - fitModel.fit(people.get(p), roles.get(roleOf[p])));
        }
        return total;
    }

    /** Cheapest cost over every way of handing out the same multiset of roles. */
    private long bruteForceMin(List<Participant> people, List<Role> roles, int[] roleOf) {
        int[] counts = new int[roles.size()];
        for (int r : roleOf) {
            counts[r]++;
        }
        return search(people, roles, counts, new int[people.size()], 0);
    }

    private long search(List<Participant> people, List<Role> roles, int[] left, int[] current, int p) {
        if (p == people.size()) {
            return cost(people, roles, current);
        }
        long best = Long.MAX_VALUE;
        for (int r = 0; r < left.length; r++) {
            if (left[r] > 0) {
                left[r]--;
                current[p] = r;
                best = Math.min(best, search(people, roles, left, current, p + 1));
                left[r]++;
            }
        }
        return best;
    }

    @Test
    void agreesWithExhaustiveSearchOnSmallRandomAllocations() {
        Random random = new Random(7);
        List<Role> roles = ROLES.subList(0, 3);
        int optimal = 0;
        int suboptimal = 0;
        for (int round = 0; round < 400; round++) {
            int n = 3 + random.nextInt(5); // 3..7 participants
            List<Participant> people = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                people.add(randomPerson(random, roles.size()));
            }
            int[] roleOf = new int[n];
            for (int p = 0; p < n; p++) {
                roleOf[p] = random.nextInt(roles.size());
            }
            boolean isOptimal = cost(people, roles, roleOf) == bruteForceMin(people, roles, roleOf);
            RoleAllocationCheck.Result result =
                    RoleAllocationCheck.verify(people, roles, allocation(people, roles, roleOf), fitModel);
            assertEquals(isOptimal, result.passed(), "round " + round);
            if (isOptimal) {
                optimal++;
            } else {
                suboptimal++;
            }
        }
        assertTrue(optimal > 20 && suboptimal > 20, "both outcomes exercised: " + optimal + "/" + suboptimal);
    }

    @Test
    void certifiesTheSynthesisOfFourHundredParticipantsAndCatchesASingleBadSwap() {
        Random random = new Random(400);
        List<Participant> people = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            people.add(randomPerson(random, ROLES.size()));
        }
        Synthesis synthesis = synthesizer.synthesize(people, ROLES);

        RoleAllocationCheck.Result result = RoleAllocationCheck.verify(people, ROLES, synthesis.teams(), fitModel);
        assertTrue(result.passed());
        assertEquals(400, result.participants());
        assertEquals(400L * (ROLES.size() - 1), result.movesExamined());

        // Swap the roles of two people when that strictly lowers the total fit: the check must notice.
        Map<UUID, Integer> index = new HashMap<>();
        for (int i = 0; i < people.size(); i++) {
            index.put(people.get(i).id(), i);
        }
        int[] roleOf = new int[people.size()];
        for (Team t : synthesis.teams()) {
            for (Member m : t.members()) {
                roleOf[index.get(m.participantId())] = (int) (m.roleId() - 1);
            }
        }
        long best = cost(people, ROLES, roleOf);
        outer:
        for (int a = 0; a < people.size(); a++) {
            for (int b = a + 1; b < people.size(); b++) {
                if (roleOf[a] == roleOf[b]) {
                    continue;
                }
                int[] swapped = roleOf.clone();
                swapped[a] = roleOf[b];
                swapped[b] = roleOf[a];
                if (cost(people, ROLES, swapped) > best) {
                    assertFalse(RoleAllocationCheck.verify(people, ROLES, allocation(people, ROLES, swapped), fitModel)
                            .passed());
                    break outer;
                }
            }
        }
    }

    @Test
    void hungarianTeamsBeatRandomTeamsAtFourHundredParticipants() {
        Random random = new Random(1);
        List<Participant> people = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            people.add(randomPerson(random, ROLES.size()));
        }
        TeamSynthesizer.Quality hungarian = synthesizer.synthesize(people, ROLES).quality();
        TeamSynthesizer.Quality baseline = synthesizer.randomBaseline(people, ROLES, new Random(2)).quality();
        assertTrue(hungarian.averageFit() > baseline.averageFit());
        assertTrue(hungarian.preferenceSatisfaction() > baseline.preferenceSatisfaction());
        assertTrue(hungarian.strengthStdDev() < baseline.strengthStdDev());
    }
}

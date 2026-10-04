package edu.campusconnect.hackathon;

import edu.campusconnect.matching.algo.HungarianAlgorithm;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Forms balanced, role-diverse teams with the Hungarian algorithm instead of self-selection.
 *
 * <p>With {@code n} participants and {@code R} roles (priority ordered) the synthesizer creates
 * {@code T = ceil(n / R)} teams of near-equal size ({@code floor/ceil(n / T)}); a team with fewer than {@code R}
 * members drops its lowest-priority roles. Two optimisation stages follow, each an exact minimum-cost assignment:
 *
 * <ol>
 *   <li><b>Role allocation.</b> Participants × role slots, cost {@code 1 − fit(participant, role)}. Every slot is
 *       filled by exactly one participant, so each team gets one member per role and the total mismatch between
 *       people and roles is minimal.</li>
 *   <li><b>Team balancing.</b> Role by role, the participants holding that role are assigned to teams, with cost
 *       {@code (S_t + s_p)²} where {@code S_t} is the strength already in team {@code t}. Minimising the sum of
 *       squares pairs strong people with currently weak teams, so team strengths stay as even as possible.</li>
 * </ol>
 */
public class TeamSynthesizer {

    public record Role(Long id, String name, List<String> keywords) {}

    /** @param skills lower-cased skill name → proficiency weight (1–3); @param rolePreferences ordered role ids */
    public record Participant(UUID id, Map<String, Integer> skills, List<Long> rolePreferences) {}

    public record Member(UUID participantId, Long roleId, double fit, double strength) {}

    public record Team(int number, List<Member> members) {
        public double averageStrength() {
            return members.stream().mapToDouble(Member::strength).average().orElse(0);
        }
    }

    /**
     * @param averageFit             mean role fit over all participants (1 = everybody in their ideal role)
     * @param preferenceSatisfaction share of participants placed in one of their top-3 preferred roles
     * @param strengthStdDev         standard deviation of the teams' average strengths (lower = more balanced)
     * @param strengthSpread         strongest minus weakest team average
     */
    public record Quality(double averageFit, double preferenceSatisfaction, double strengthStdDev, double strengthSpread) {}

    public record Synthesis(List<Team> teams, Quality quality) {}

    private static final int TOP_PREFERENCES = 3;

    private final RoleFitModel fitModel;

    public TeamSynthesizer(RoleFitModel fitModel) {
        this.fitModel = fitModel;
    }

    public Synthesis synthesize(List<Participant> participants, List<Role> roles) {
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }
        int n = participants.size();
        if (n == 0) {
            return new Synthesis(List.of(), new Quality(0, 0, 0, 0));
        }
        int teamCount = (n + roles.size() - 1) / roles.size();
        int base = n / teamCount;
        int larger = n % teamCount;
        int[] teamSize = new int[teamCount];
        for (int t = 0; t < teamCount; t++) {
            teamSize[t] = base + (t < larger ? 1 : 0);
        }

        double[][] fit = new double[n][roles.size()];
        for (int p = 0; p < n; p++) {
            for (int r = 0; r < roles.size(); r++) {
                fit[p][r] = fitModel.fit(participants.get(p), roles.get(r));
            }
        }

        // Stage 1: role allocation. Slot j belongs to some team and holds role slotRole[j].
        List<Integer> slotRole = new ArrayList<>();
        for (int t = 0; t < teamCount; t++) {
            for (int r = 0; r < teamSize[t]; r++) {
                slotRole.add(r);
            }
        }
        long[][] cost = new long[n][n];
        for (int p = 0; p < n; p++) {
            for (int j = 0; j < n; j++) {
                cost[p][j] = HungarianAlgorithm.scale(1.0 - fit[p][slotRole.get(j)]);
            }
        }
        int[] slotOfParticipant = HungarianAlgorithm.solve(cost);
        int[] roleOf = new int[n];
        double[] strength = new double[n];
        for (int p = 0; p < n; p++) {
            roleOf[p] = slotRole.get(slotOfParticipant[p]);
            strength[p] = 0.5 * fitModel.generalStrength(participants.get(p)) + 0.5 * fit[p][roleOf[p]];
        }

        // Stage 2: spread each role's holders over the teams that need that role.
        double[] teamStrength = new double[teamCount];
        List<List<Member>> members = new ArrayList<>();
        for (int t = 0; t < teamCount; t++) {
            members.add(new ArrayList<>());
        }
        for (int r = 0; r < roles.size(); r++) {
            final int role = r;
            List<Integer> holders = IntStream.range(0, n).filter(p -> roleOf[p] == role).boxed().toList();
            List<Integer> teamsWithRole = IntStream.range(0, teamCount).filter(t -> teamSize[t] > role).boxed().toList();
            if (holders.isEmpty()) {
                continue;
            }
            long[][] balance = new long[holders.size()][teamsWithRole.size()];
            for (int i = 0; i < holders.size(); i++) {
                for (int k = 0; k < teamsWithRole.size(); k++) {
                    double total = teamStrength[teamsWithRole.get(k)] + strength[holders.get(i)];
                    balance[i][k] = HungarianAlgorithm.scale(total * total);
                }
            }
            int[] placement = HungarianAlgorithm.solve(balance);
            for (int i = 0; i < holders.size(); i++) {
                int p = holders.get(i);
                int team = teamsWithRole.get(placement[i]);
                teamStrength[team] += strength[p];
                members.get(team).add(new Member(participants.get(p).id(), roles.get(r).id(), fit[p][r], strength[p]));
            }
        }

        List<Team> teams = new ArrayList<>();
        for (int t = 0; t < teamCount; t++) {
            teams.add(new Team(t + 1, members.get(t)));
        }
        return new Synthesis(teams, quality(participants, teams));
    }

    /** Uninformed baseline for evaluation: shuffle participants into teams of the same sizes, roles in order. */
    public Synthesis randomBaseline(List<Participant> participants, List<Role> roles, java.util.Random random) {
        List<Participant> shuffled = new ArrayList<>(participants);
        java.util.Collections.shuffle(shuffled, random);
        int n = shuffled.size();
        int teamCount = Math.max(1, (n + roles.size() - 1) / roles.size());
        int base = n / teamCount;
        int larger = n % teamCount;
        List<Team> teams = new ArrayList<>();
        int next = 0;
        for (int t = 0; t < teamCount; t++) {
            int size = base + (t < larger ? 1 : 0);
            List<Member> members = new ArrayList<>();
            for (int r = 0; r < size; r++) {
                Participant p = shuffled.get(next++);
                double fit = fitModel.fit(p, roles.get(r));
                members.add(new Member(p.id(), roles.get(r).id(), fit,
                        0.5 * fitModel.generalStrength(p) + 0.5 * fit));
            }
            teams.add(new Team(t + 1, members));
        }
        return new Synthesis(teams, quality(participants, teams));
    }

    private Quality quality(List<Participant> participants, List<Team> teams) {
        Map<UUID, Participant> byId = new HashMap<>();
        participants.forEach(p -> byId.put(p.id(), p));
        double fitSum = 0;
        int satisfied = 0;
        int count = 0;
        for (Team team : teams) {
            for (Member m : team.members()) {
                fitSum += m.fit();
                List<Long> prefs = byId.get(m.participantId()).rolePreferences();
                int rank = prefs.indexOf(m.roleId());
                if (rank >= 0 && rank < TOP_PREFERENCES) {
                    satisfied++;
                }
                count++;
            }
        }
        double[] averages = teams.stream().mapToDouble(Team::averageStrength).toArray();
        double mean = java.util.Arrays.stream(averages).average().orElse(0);
        double variance = java.util.Arrays.stream(averages).map(a -> (a - mean) * (a - mean)).average().orElse(0);
        double spread = averages.length == 0 ? 0
                : java.util.Arrays.stream(averages).max().getAsDouble() - java.util.Arrays.stream(averages).min().getAsDouble();
        return new Quality(count == 0 ? 0 : fitSum / count, count == 0 ? 0 : satisfied / (double) count,
                Math.sqrt(variance), spread);
    }

    /** Lower-cases and trims for skill/keyword comparison. */
    static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).strip();
    }
}

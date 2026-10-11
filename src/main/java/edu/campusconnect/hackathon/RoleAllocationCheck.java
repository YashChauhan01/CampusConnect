package edu.campusconnect.hackathon;

import edu.campusconnect.hackathon.TeamSynthesizer.Member;
import edu.campusconnect.hackathon.TeamSynthesizer.Participant;
import edu.campusconnect.hackathon.TeamSynthesizer.Role;
import edu.campusconnect.hackathon.TeamSynthesizer.Team;
import edu.campusconnect.matching.algo.HungarianAlgorithm;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Independent proof that a synthesis's role allocation (stage 1) has the maximum possible total fit, given how many
 * people each role needs. It does not look at how the allocation was found, only at the result.
 *
 * <p>Reassigning roles while keeping every role's head count is always a set of disjoint cycles: someone moves from
 * role A to B, someone else from B to C, ..., someone from the last role back to A. Build a graph on the roles where the
 * arc {@code A → B} costs the cheapest change in mismatch for anyone currently holding A to take B instead. The
 * allocation is optimal exactly when that graph has no negative cycle (the optimality condition of the transportation
 * problem); with at most {@value HackathonService#MAX_ROLES} roles, Floyd–Warshall decides it instantly. Costs are the
 * same integer-scaled {@code 1 − fit} the synthesizer minimises, so there is no floating-point tolerance involved.
 */
public final class RoleAllocationCheck {

    /**
     * @param movesExamined    participant-to-other-role moves priced (participants × (roles − 1))
     * @param improvingCycles  roles that lie on a cycle of moves which would raise the total fit (0 when optimal)
     * @param totalFit         sum of the allocated participants' role fit
     */
    public record Result(int participants, int roles, long movesExamined, int improvingCycles, double totalFit,
                         boolean passed) {}

    private RoleAllocationCheck() {}

    public static Result verify(List<Participant> participants, List<Role> roles, List<Team> teams, RoleFitModel fit) {
        int r = roles.size();
        Map<Long, Integer> roleIndex = new HashMap<>();
        for (int i = 0; i < r; i++) {
            roleIndex.put(roles.get(i).id(), i);
        }
        Map<UUID, Integer> assigned = new HashMap<>();
        for (Team team : teams) {
            for (Member m : team.members()) {
                assigned.put(m.participantId(), roleIndex.get(m.roleId()));
            }
        }

        final long none = Long.MAX_VALUE / 4;
        long[][] arc = new long[r][r];
        for (long[] row : arc) {
            java.util.Arrays.fill(row, none);
        }
        long moves = 0;
        double totalFit = 0;
        for (Participant p : participants) {
            Integer from = assigned.get(p.id());
            if (from == null) {
                continue;
            }
            double[] f = new double[r];
            for (int k = 0; k < r; k++) {
                f[k] = fit.fit(p, roles.get(k));
            }
            totalFit += f[from];
            long current = HungarianAlgorithm.scale(1.0 - f[from]);
            for (int to = 0; to < r; to++) {
                if (to != from) {
                    moves++;
                    arc[from][to] = Math.min(arc[from][to], HungarianAlgorithm.scale(1.0 - f[to]) - current);
                }
            }
        }

        // Floyd–Warshall: a negative distance from a role back to itself is an improving cycle.
        long[][] dist = new long[r][r];
        for (int i = 0; i < r; i++) {
            dist[i] = arc[i].clone();
        }
        for (int k = 0; k < r; k++) {
            for (int i = 0; i < r; i++) {
                for (int j = 0; j < r; j++) {
                    if (dist[i][k] < none && dist[k][j] < none && dist[i][k] + dist[k][j] < dist[i][j]) {
                        dist[i][j] = dist[i][k] + dist[k][j];
                    }
                }
            }
        }
        int improving = 0;
        for (int i = 0; i < r; i++) {
            if (dist[i][i] < 0) {
                improving++;
            }
        }
        return new Result(assigned.size(), r, moves, improving, totalFit, improving == 0);
    }
}

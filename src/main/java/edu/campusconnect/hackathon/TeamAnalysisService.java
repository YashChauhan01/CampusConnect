package edu.campusconnect.hackathon;

import edu.campusconnect.common.ApiException;
import edu.campusconnect.hackathon.TeamSynthesizer.Member;
import edu.campusconnect.hackathon.TeamSynthesizer.Participant;
import edu.campusconnect.hackathon.TeamSynthesizer.Role;
import edu.campusconnect.hackathon.TeamSynthesizer.Synthesis;
import edu.campusconnect.hackathon.TeamSynthesizer.Team;
import edu.campusconnect.student.Student;
import edu.campusconnect.student.StudentRepository;
import edu.campusconnect.student.StudentSkill;
import edu.campusconnect.student.StudentSkillRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An explorable picture of hackathon team synthesis for organisers and administrators: the teams the Hungarian
 * synthesis forms from the current registrations, the same participants shuffled into random teams for comparison,
 * and an independent proof that the role allocation is optimal. Nothing is stored; with unchanged registrations and
 * skills the preview is identical to the draft that "Generate teams" saves.
 */
@Service
public class TeamAnalysisService {

    /** Random baselines vary from run to run, so they are averaged; the seed keeps the page reproducible. */
    static final int RANDOM_RUNS = 50;
    private static final long RANDOM_SEED = 42;
    private static final int TOP_PREFERENCES = 3;
    private static final int SKILLS_SHOWN = 6;

    public record RoleInfo(Long id, String name, List<String> keywords, int holders, int firstChoices) {}

    public record SkillLevel(String name, int level) {}

    /** @param preferenceRank 1-3 when the role was one of the participant's choices, otherwise null */
    public record MemberInfo(UUID studentId, String name, Long roleId, int fit, int strength, Integer preferenceRank,
                             List<Long> preferences, List<SkillLevel> skills, List<Integer> fitByRole) {}

    public record TeamInfo(int number, List<MemberInfo> members, int averageStrength) {}

    /**
     * @param preferenceRanks share of participants placed in their 1st, 2nd and 3rd choice, and in an unranked role
     * @param teamStrengths   every team's average strength (0-1), weakest first
     */
    public record Method(double averageFit, double preferenceSatisfaction, double strengthStdDev, double strengthSpread,
                         double[] preferenceRanks, double[] teamStrengths, double millis) {}

    /**
     * @param flow {@code flow[i][j]} participants whose first choice is role i (the last row: no preference) and who
     *             were allocated role j
     */
    public record Analysis(UUID hackathonId, String name, Hackathon.Status status, int participants, int teamCount,
                           List<RoleInfo> roles, List<TeamInfo> teams, Method hungarian, Method random, int randomRuns,
                           RoleAllocationCheck.Result check, int[][] flow) {}

    private final HackathonRepository hackathons;
    private final HackathonRoleRepository roles;
    private final HackathonParticipantRepository participants;
    private final HackathonService service;
    private final StudentRepository students;
    private final StudentSkillRepository studentSkills;
    private final TeamSynthesizer synthesizer;
    private final RoleFitModel fitModel;

    public TeamAnalysisService(HackathonRepository hackathons, HackathonRoleRepository roles,
                               HackathonParticipantRepository participants, HackathonService service,
                               StudentRepository students, StudentSkillRepository studentSkills,
                               TeamSynthesizer synthesizer, RoleFitModel fitModel) {
        this.hackathons = hackathons;
        this.roles = roles;
        this.participants = participants;
        this.service = service;
        this.students = students;
        this.studentSkills = studentSkills;
        this.synthesizer = synthesizer;
        this.fitModel = fitModel;
    }

    @Transactional(readOnly = true)
    public Analysis analyze(Student viewer, UUID id) {
        Hackathon h = hackathons.findById(id).orElseThrow(() -> ApiException.notFound("Hackathon not found"));
        if (!h.getOrganizerId().equals(viewer.getId()) && !viewer.isAdmin()) {
            throw ApiException.forbidden("Only the organiser can analyse the teams");
        }
        List<HackathonParticipant> registered = participants.findByHackathon(id);
        if (registered.size() < 2) {
            throw ApiException.badRequest("At least 2 participants are needed to form teams");
        }
        List<HackathonRole> roleEntities = roles.findByHackathonIdOrderByPosition(id);
        List<Participant> people = service.toParticipants(registered);
        List<Role> roleList = HackathonService.toRoles(roleEntities);

        long start = System.nanoTime();
        Synthesis hungarian = synthesizer.synthesize(people, roleList);
        double hungarianMillis = (System.nanoTime() - start) / 1_000_000.0;

        Random random = new Random(RANDOM_SEED);
        List<Method> randomRuns = new ArrayList<>();
        for (int i = 0; i < RANDOM_RUNS; i++) {
            long t = System.nanoTime();
            Synthesis baseline = synthesizer.randomBaseline(people, roleList, random);
            randomRuns.add(method(baseline, people, (System.nanoTime() - t) / 1_000_000.0));
        }

        Map<UUID, Participant> byId = people.stream().collect(Collectors.toMap(Participant::id, p -> p));
        Set<UUID> ids = byId.keySet();
        Map<UUID, String> names = students.findAllById(ids).stream()
                .collect(Collectors.toMap(Student::getId, Student::getFullName));
        Map<UUID, List<SkillLevel>> skills = studentSkills.findByStudents(ids).stream()
                .collect(Collectors.groupingBy(StudentSkill::studentId, Collectors.mapping(
                        s -> new SkillLevel(s.getSkill().getName(), s.effectiveLevel().weight()), Collectors.toList())));

        List<TeamInfo> teams = new ArrayList<>();
        for (Team team : hungarian.teams()) {
            List<MemberInfo> members = new ArrayList<>();
            for (Member m : team.members()) {
                Participant p = byId.get(m.participantId());
                int rank = p.rolePreferences().indexOf(m.roleId());
                List<SkillLevel> top = skills.getOrDefault(p.id(), List.of()).stream()
                        .sorted(Comparator.comparingInt(SkillLevel::level).reversed().thenComparing(SkillLevel::name))
                        .limit(SKILLS_SHOWN).toList();
                List<Integer> fitByRole = roleList.stream().map(r -> percent(fitModel.fit(p, r))).toList();
                members.add(new MemberInfo(p.id(), names.getOrDefault(p.id(), "Unknown"), m.roleId(), percent(m.fit()),
                        percent(m.strength()), rank >= 0 && rank < TOP_PREFERENCES ? rank + 1 : null,
                        p.rolePreferences(), top, fitByRole));
            }
            teams.add(new TeamInfo(team.number(), members, percent(team.averageStrength())));
        }

        Map<Long, Integer> roleIndex = new HashMap<>();
        for (int i = 0; i < roleList.size(); i++) {
            roleIndex.put(roleList.get(i).id(), i);
        }
        int[][] flow = new int[roleList.size() + 1][roleList.size()];
        int[] holders = new int[roleList.size()];
        int[] firstChoices = new int[roleList.size()];
        for (Team team : hungarian.teams()) {
            for (Member m : team.members()) {
                List<Long> prefs = byId.get(m.participantId()).rolePreferences();
                int row = prefs.isEmpty() ? roleList.size() : roleIndex.get(prefs.getFirst());
                int col = roleIndex.get(m.roleId());
                flow[row][col]++;
                holders[col]++;
                if (row < roleList.size()) {
                    firstChoices[row]++;
                }
            }
        }
        List<RoleInfo> roleInfos = new ArrayList<>();
        for (int i = 0; i < roleList.size(); i++) {
            Role r = roleList.get(i);
            roleInfos.add(new RoleInfo(r.id(), r.name(), r.keywords(), holders[i], firstChoices[i]));
        }

        return new Analysis(h.getId(), h.getName(), h.getStatus(), people.size(), hungarian.teams().size(), roleInfos,
                teams, method(hungarian, people, hungarianMillis), average(randomRuns), RANDOM_RUNS,
                RoleAllocationCheck.verify(people, roleList, hungarian.teams(), fitModel), flow);
    }

    private static Method method(Synthesis s, List<Participant> people, double millis) {
        Map<UUID, List<Long>> prefs = people.stream()
                .collect(Collectors.toMap(Participant::id, Participant::rolePreferences));
        double[] ranks = new double[TOP_PREFERENCES + 1];
        int count = 0;
        for (Team team : s.teams()) {
            for (Member m : team.members()) {
                int rank = prefs.get(m.participantId()).indexOf(m.roleId());
                ranks[rank >= 0 && rank < TOP_PREFERENCES ? rank : TOP_PREFERENCES]++;
                count++;
            }
        }
        for (int i = 0; i < ranks.length; i++) {
            ranks[i] = count == 0 ? 0 : ranks[i] / count;
        }
        double[] strengths = s.teams().stream().mapToDouble(Team::averageStrength).sorted().toArray();
        TeamSynthesizer.Quality q = s.quality();
        return new Method(q.averageFit(), q.preferenceSatisfaction(), q.strengthStdDev(), q.strengthSpread(), ranks,
                strengths, millis);
    }

    /** Mean of the random runs; team strengths are averaged position by position (weakest team, second weakest...). */
    private static Method average(List<Method> runs) {
        int n = runs.size();
        double[] ranks = new double[runs.getFirst().preferenceRanks().length];
        double[] strengths = new double[runs.getFirst().teamStrengths().length];
        double fit = 0, pref = 0, sd = 0, spread = 0, millis = 0;
        for (Method m : runs) {
            fit += m.averageFit();
            pref += m.preferenceSatisfaction();
            sd += m.strengthStdDev();
            spread += m.strengthSpread();
            millis += m.millis();
            for (int i = 0; i < ranks.length; i++) {
                ranks[i] += m.preferenceRanks()[i] / n;
            }
            for (int i = 0; i < strengths.length; i++) {
                strengths[i] += m.teamStrengths()[i] / n;
            }
        }
        return new Method(fit / n, pref / n, sd / n, spread / n, ranks, strengths, millis / n);
    }

    private static int percent(double value) {
        return (int) Math.round(value * 100);
    }
}

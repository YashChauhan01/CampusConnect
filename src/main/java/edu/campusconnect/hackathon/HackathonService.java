package edu.campusconnect.hackathon;

import edu.campusconnect.common.ApiException;
import edu.campusconnect.hackathon.TeamSynthesizer.Participant;
import edu.campusconnect.hackathon.TeamSynthesizer.Role;
import edu.campusconnect.hackathon.TeamSynthesizer.Synthesis;
import edu.campusconnect.student.Student;
import edu.campusconnect.student.StudentRepository;
import edu.campusconnect.student.StudentSkill;
import edu.campusconnect.student.StudentSkillRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hackathon lifecycle and team synthesis. Organisers define roles, students register with ranked role preferences,
 * and the organiser runs the Hungarian-algorithm synthesis (repeatably) before publishing the final teams.
 */
@Service
public class HackathonService {

    public static final int MIN_ROLES = 2;
    public static final int MAX_ROLES = 8;
    private static final int MAX_PREFERENCES = 3;
    private static final int MIN_PARTICIPANTS = 2;

    public record RoleSpec(String name, List<String> keywords) {}

    public record RoleView(Long id, String name, List<String> keywords) {}

    public record Summary(UUID id, String name, String description, Hackathon.Status status, String organizer,
                          boolean organizedByMe, int participants, boolean registered, List<RoleView> roles,
                          Instant createdAt) {}

    public record ParticipantView(UUID studentId, String name, List<String> skills, List<String> preferences) {}

    public record Detail(Summary summary, List<Long> myPreferences, List<ParticipantView> participants) {}

    public record MemberView(UUID studentId, String name, String role, int fit, int strength, boolean you) {}

    public record TeamView(int number, List<MemberView> members, int averageStrength, boolean yours) {}

    public record QualityView(int averageFit, int preferenceSatisfaction, double strengthStdDev, double strengthSpread) {}

    public record TeamsView(Hackathon.Status status, boolean published, List<TeamView> teams, QualityView quality) {}

    private final HackathonRepository hackathons;
    private final HackathonRoleRepository roles;
    private final HackathonParticipantRepository participants;
    private final HackathonTeamRepository teams;
    private final StudentRepository students;
    private final StudentSkillRepository studentSkills;
    private final TeamSynthesizer synthesizer;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public HackathonService(HackathonRepository hackathons, HackathonRoleRepository roles,
                            HackathonParticipantRepository participants, HackathonTeamRepository teams,
                            StudentRepository students, StudentSkillRepository studentSkills,
                            TeamSynthesizer synthesizer, ApplicationEventPublisher events, Clock clock) {
        this.hackathons = hackathons;
        this.roles = roles;
        this.participants = participants;
        this.teams = teams;
        this.students = students;
        this.studentSkills = studentSkills;
        this.synthesizer = synthesizer;
        this.events = events;
        this.clock = clock;
    }

    // ---- organiser ------------------------------------------------------------------------------------------------

    @Transactional
    public Detail create(Student organizer, String name, String description, List<RoleSpec> specs) {
        if (specs.size() < MIN_ROLES || specs.size() > MAX_ROLES) {
            throw ApiException.badRequest("A hackathon needs between " + MIN_ROLES + " and " + MAX_ROLES + " roles");
        }
        Set<String> seen = new HashSet<>();
        for (RoleSpec spec : specs) {
            if (!seen.add(spec.name().strip().toLowerCase(Locale.ROOT))) {
                throw ApiException.badRequest("Duplicate role: " + spec.name());
            }
        }
        Hackathon hackathon = hackathons.save(new Hackathon(organizer.getId(), name.strip(),
                description == null || description.isBlank() ? null : description.strip(), clock.instant()));
        for (int i = 0; i < specs.size(); i++) {
            RoleSpec spec = specs.get(i);
            List<String> keywords = spec.keywords() == null ? List.of() : spec.keywords().stream()
                    .map(k -> k.strip().toLowerCase(Locale.ROOT)).filter(k -> !k.isEmpty()).distinct().toList();
            roles.save(new HackathonRole(hackathon.getId(), spec.name().strip(), keywords, i));
        }
        return detail(hackathon.getId(), organizer);
    }

    @Transactional
    public Detail closeRegistration(Student organizer, UUID id) {
        Hackathon h = organizerLocked(organizer, id);
        if (h.getStatus() == Hackathon.Status.PUBLISHED) {
            throw ApiException.conflict("Teams are already published");
        }
        h.setStatus(Hackathon.Status.CLOSED);
        return detail(id, organizer);
    }

    @Transactional
    public Detail reopenRegistration(Student organizer, UUID id) {
        Hackathon h = organizerLocked(organizer, id);
        if (h.getStatus() == Hackathon.Status.PUBLISHED) {
            throw ApiException.conflict("Teams are already published");
        }
        h.setStatus(Hackathon.Status.OPEN);
        return detail(id, organizer);
    }

    /** Computes (or recomputes) the draft teams; only the organiser sees them until they are published. */
    @Transactional
    public TeamsView synthesize(Student organizer, UUID id) {
        Hackathon h = organizerLocked(organizer, id);
        if (h.getStatus() == Hackathon.Status.PUBLISHED) {
            throw ApiException.conflict("Teams are already published");
        }
        List<HackathonParticipant> registered = participants.findByHackathon(id);
        if (registered.size() < MIN_PARTICIPANTS) {
            throw ApiException.badRequest("At least " + MIN_PARTICIPANTS + " participants are needed to form teams");
        }
        List<HackathonRole> roleList = roles.findByHackathonIdOrderByPosition(id);
        Synthesis result = synthesizer.synthesize(toParticipants(registered), toRoles(roleList));

        teams.deleteByHackathon(id);
        teams.flush();
        for (TeamSynthesizer.Team team : result.teams()) {
            teams.save(new HackathonTeam(id, team.number(), team.members().stream()
                    .map(m -> new HackathonTeam.Member(m.participantId(), m.roleId(), m.fit(), m.strength())).toList()));
        }
        return teamsView(h, organizer.getId());
    }

    /** Makes the draft teams final and visible, and notifies every participant. */
    @Transactional
    public TeamsView publish(Student organizer, UUID id) {
        Hackathon h = organizerLocked(organizer, id);
        if (h.getStatus() == Hackathon.Status.PUBLISHED) {
            throw ApiException.conflict("Teams are already published");
        }
        List<HackathonTeam> draft = teams.findByHackathonIdOrderByNumber(id);
        if (draft.isEmpty()) {
            throw ApiException.conflict("Generate teams before publishing");
        }
        // Registration changes after synthesis would leave the draft stale.
        Set<UUID> inTeams = draft.stream().flatMap(t -> t.getMembers().stream())
                .map(HackathonTeam.Member::getStudentId).collect(Collectors.toSet());
        Set<UUID> registered = participants.findByHackathon(id).stream()
                .map(HackathonParticipant::studentId).collect(Collectors.toSet());
        if (!inTeams.equals(registered)) {
            throw ApiException.conflict("Registrations changed since the teams were generated; generate them again");
        }
        h.setStatus(Hackathon.Status.PUBLISHED);
        events.publishEvent(new HackathonEvent.TeamsPublished(id, h.getName(), List.copyOf(registered)));
        return teamsView(h, organizer.getId());
    }

    // ---- participants ---------------------------------------------------------------------------------------------

    /** Registers the student, or updates their role preferences if they are already registered. */
    @Transactional
    public Detail register(Student student, UUID id, List<Long> preferences) {
        Hackathon h = hackathons.findForUpdate(id).orElseThrow(() -> ApiException.notFound("Hackathon not found"));
        if (h.getStatus() != Hackathon.Status.OPEN) {
            throw ApiException.conflict("Registration is closed");
        }
        if (studentSkills.findByStudent(student.getId()).isEmpty()) {
            throw ApiException.badRequest("Add at least one skill to your profile before registering");
        }
        List<Long> prefs = validatePreferences(id, preferences);
        HackathonParticipant.Id key = new HackathonParticipant.Id(id, student.getId());
        participants.findById(key).ifPresentOrElse(p -> p.replacePreferences(prefs),
                () -> participants.save(new HackathonParticipant(id, student.getId(), clock.instant(), prefs)));
        return detail(id, student);
    }

    @Transactional
    public Detail withdraw(Student student, UUID id) {
        Hackathon h = hackathons.findForUpdate(id).orElseThrow(() -> ApiException.notFound("Hackathon not found"));
        if (h.getStatus() == Hackathon.Status.PUBLISHED) {
            throw ApiException.conflict("Teams are already published");
        }
        participants.deleteById(new HackathonParticipant.Id(id, student.getId()));
        return detail(id, student);
    }

    // ---- queries --------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Summary> list(Student viewer) {
        List<Hackathon> all = hackathons.findAllByOrderByCreatedAtDesc();
        return summaries(all, viewer);
    }

    @Transactional(readOnly = true)
    public Detail detail(UUID id, Student viewer) {
        Hackathon h = hackathons.findById(id).orElseThrow(() -> ApiException.notFound("Hackathon not found"));
        Summary summary = summaries(List.of(h), viewer).getFirst();
        List<Long> mine = participants.findById(new HackathonParticipant.Id(id, viewer.getId()))
                .map(p -> List.copyOf(p.getRolePreferences())).orElse(List.of());
        List<ParticipantView> roster = h.getOrganizerId().equals(viewer.getId()) ? roster(h) : List.of();
        return new Detail(summary, mine, roster);
    }

    /** Organisers see draft teams at any time; everyone else only sees them once published. */
    @Transactional(readOnly = true)
    public TeamsView teams(Student viewer, UUID id) {
        Hackathon h = hackathons.findById(id).orElseThrow(() -> ApiException.notFound("Hackathon not found"));
        boolean organizer = h.getOrganizerId().equals(viewer.getId());
        if (!organizer && h.getStatus() != Hackathon.Status.PUBLISHED) {
            throw ApiException.forbidden("Teams have not been published yet");
        }
        return teamsView(h, viewer.getId());
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private Hackathon organizerLocked(Student organizer, UUID id) {
        Hackathon h = hackathons.findForUpdate(id).orElseThrow(() -> ApiException.notFound("Hackathon not found"));
        if (!h.getOrganizerId().equals(organizer.getId())) {
            throw ApiException.forbidden("Only the organiser can do this");
        }
        return h;
    }

    private List<Long> validatePreferences(UUID hackathonId, List<Long> preferences) {
        List<Long> prefs = preferences == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(preferences));
        if (prefs.size() > MAX_PREFERENCES) {
            throw ApiException.badRequest("Choose at most " + MAX_PREFERENCES + " preferred roles");
        }
        Set<Long> valid = roles.findByHackathonIdOrderByPosition(hackathonId).stream()
                .map(HackathonRole::getId).collect(Collectors.toSet());
        if (!valid.containsAll(prefs)) {
            throw ApiException.badRequest("Unknown role for this hackathon");
        }
        return prefs;
    }

    private List<Participant> toParticipants(Collection<HackathonParticipant> registered) {
        Set<UUID> ids = registered.stream().map(HackathonParticipant::studentId).collect(Collectors.toSet());
        Map<UUID, Map<String, Integer>> skills = new HashMap<>();
        for (StudentSkill s : studentSkills.findByStudents(ids)) {
            skills.computeIfAbsent(s.studentId(), k -> new HashMap<>())
                    .put(s.getSkill().getName().toLowerCase(Locale.ROOT), s.getProficiency().weight());
        }
        return registered.stream().map(p -> new Participant(p.studentId(),
                skills.getOrDefault(p.studentId(), Map.of()), List.copyOf(p.getRolePreferences()))).toList();
    }

    private static List<Role> toRoles(List<HackathonRole> list) {
        return list.stream().map(r -> new Role(r.getId(), r.getName(), r.keywordList())).toList();
    }

    private List<Summary> summaries(List<Hackathon> list, Student viewer) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = list.stream().map(Hackathon::getId).collect(Collectors.toSet());
        Map<UUID, Integer> counts = new HashMap<>();
        for (Object[] row : participants.countByHackathons(ids)) {
            counts.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        Map<UUID, List<RoleView>> roleViews = roles.findByHackathonIdIn(ids).stream()
                .sorted(java.util.Comparator.comparingInt(HackathonRole::getPosition))
                .collect(Collectors.groupingBy(HackathonRole::getHackathonId, Collectors.mapping(
                        r -> new RoleView(r.getId(), r.getName(), r.keywordList()), Collectors.toList())));
        Set<UUID> mine = new HashSet<>(participants.findHackathonIdsOf(viewer.getId()));
        Map<UUID, String> organizers = students.findAllById(
                list.stream().map(Hackathon::getOrganizerId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Student::getId, Student::getFullName));
        return list.stream().map(h -> new Summary(h.getId(), h.getName(), h.getDescription(), h.getStatus(),
                organizers.getOrDefault(h.getOrganizerId(), "Unknown"), h.getOrganizerId().equals(viewer.getId()),
                counts.getOrDefault(h.getId(), 0), mine.contains(h.getId()),
                roleViews.getOrDefault(h.getId(), List.of()), h.getCreatedAt())).toList();
    }

    private List<ParticipantView> roster(Hackathon h) {
        List<HackathonParticipant> registered = participants.findByHackathon(h.getId());
        Set<UUID> ids = registered.stream().map(HackathonParticipant::studentId).collect(Collectors.toSet());
        Map<UUID, String> names = students.findAllById(ids).stream()
                .collect(Collectors.toMap(Student::getId, Student::getFullName));
        Map<UUID, List<String>> skills = studentSkills.findByStudents(ids).stream()
                .collect(Collectors.groupingBy(StudentSkill::studentId, Collectors.mapping(
                        s -> s.getSkill().getName() + " (" + s.getProficiency().name().charAt(0)
                                + s.getProficiency().name().substring(1).toLowerCase(Locale.ROOT) + ")", Collectors.toList())));
        Map<Long, String> roleNames = roles.findByHackathonIdOrderByPosition(h.getId()).stream()
                .collect(Collectors.toMap(HackathonRole::getId, HackathonRole::getName));
        return registered.stream().map(p -> new ParticipantView(p.studentId(), names.get(p.studentId()),
                skills.getOrDefault(p.studentId(), List.of()),
                p.getRolePreferences().stream().map(roleNames::get).toList())).toList();
    }

    /** Builds the response from the stored teams so drafts and published teams are described identically. */
    private TeamsView teamsView(Hackathon h, UUID viewerId) {
        List<HackathonTeam> stored = teams.findByHackathonIdOrderByNumber(h.getId());
        Set<UUID> memberIds = stored.stream().flatMap(t -> t.getMembers().stream())
                .map(HackathonTeam.Member::getStudentId).collect(Collectors.toSet());
        Map<UUID, String> names = students.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Student::getId, Student::getFullName));
        Map<Long, String> roleNames = roles.findByHackathonIdOrderByPosition(h.getId()).stream()
                .collect(Collectors.toMap(HackathonRole::getId, HackathonRole::getName));
        Map<UUID, List<Long>> preferences = participants.findByHackathon(h.getId()).stream()
                .collect(Collectors.toMap(HackathonParticipant::studentId, p -> List.copyOf(p.getRolePreferences())));

        List<TeamView> views = new ArrayList<>();
        double fitSum = 0;
        int satisfied = 0;
        int members = 0;
        double[] averages = new double[stored.size()];
        for (int i = 0; i < stored.size(); i++) {
            HackathonTeam team = stored.get(i);
            boolean yours = team.getMembers().stream().anyMatch(m -> m.getStudentId().equals(viewerId));
            double strengthSum = 0;
            List<MemberView> memberViews = new ArrayList<>();
            for (HackathonTeam.Member m : team.getMembers()) {
                fitSum += m.getFit();
                strengthSum += m.getStrength();
                members++;
                int rank = preferences.getOrDefault(m.getStudentId(), List.of()).indexOf(m.getRoleId());
                if (rank >= 0 && rank < MAX_PREFERENCES) {
                    satisfied++;
                }
                memberViews.add(new MemberView(m.getStudentId(), names.getOrDefault(m.getStudentId(), "Unknown"),
                        roleNames.getOrDefault(m.getRoleId(), "Unknown"), percent(m.getFit()),
                        percent(m.getStrength()), m.getStudentId().equals(viewerId)));
            }
            memberViews.sort(java.util.Comparator.comparing(MemberView::role));
            averages[i] = team.getMembers().isEmpty() ? 0 : strengthSum / team.getMembers().size();
            views.add(new TeamView(team.getNumber(), memberViews, percent(averages[i]), yours));
        }
        double mean = java.util.Arrays.stream(averages).average().orElse(0);
        double variance = java.util.Arrays.stream(averages).map(a -> (a - mean) * (a - mean)).average().orElse(0);
        double spread = averages.length == 0 ? 0 : java.util.Arrays.stream(averages).max().getAsDouble()
                - java.util.Arrays.stream(averages).min().getAsDouble();
        QualityView quality = new QualityView(members == 0 ? 0 : percent(fitSum / members),
                members == 0 ? 0 : percent(satisfied / (double) members),
                Math.round(Math.sqrt(variance) * 1000) / 1000.0, Math.round(spread * 1000) / 1000.0);
        return new TeamsView(h.getStatus(), h.getStatus() == Hackathon.Status.PUBLISHED, views, quality);
    }

    private static int percent(double value) {
        return (int) Math.round(value * 100);
    }
}

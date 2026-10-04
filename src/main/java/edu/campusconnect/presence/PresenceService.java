package edu.campusconnect.presence;

import edu.campusconnect.common.ApiException;
import edu.campusconnect.config.AppProperties;
import edu.campusconnect.student.Proficiency;
import edu.campusconnect.student.Student;
import edu.campusconnect.student.StudentRepository;
import edu.campusconnect.student.StudentSubject;
import edu.campusconnect.student.StudentSubjectRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    /** Published whenever a student's presence appears, changes or disappears. */
    public record PresenceChanged(UUID studentId, Kind kind) {
        public enum Kind {CHECKED_IN, CHECKED_OUT, EXPIRED}
    }

    public record SubjectLevel(String name, Proficiency proficiency, boolean verified) {}

    public record PresenceView(UUID studentId, String name, Long zoneId, String zone, Presence.Status status,
                               String requirements, List<String> seeking, List<SubjectLevel> subjects,
                               Instant expiresAt) {}

    public record ZoneView(Long id, String name) {}

    private final PresenceRepository presences;
    private final ZoneRepository zones;
    private final StudentRepository students;
    private final StudentSubjectRepository studentSubjects;
    private final ApplicationEventPublisher events;
    private final AppProperties props;
    private final Clock clock;

    public PresenceService(PresenceRepository presences, ZoneRepository zones, StudentRepository students,
                           StudentSubjectRepository studentSubjects, ApplicationEventPublisher events,
                           AppProperties props, Clock clock) {
        this.presences = presences;
        this.zones = zones;
        this.students = students;
        this.studentSubjects = studentSubjects;
        this.events = events;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ZoneView> zones() {
        return zones.findByEnabledTrueOrderByName().stream().map(z -> new ZoneView(z.getId(), z.getName())).toList();
    }

    /** Creates or renews the student's presence for the configured duration. */
    @Transactional
    public PresenceView checkIn(UUID studentId, Long zoneId, Presence.Status status, String requirements,
                                Set<Long> seekingSubjectIds) {
        CampusZone zone = zones.findById(zoneId).filter(CampusZone::isEnabled)
                .orElseThrow(() -> ApiException.badRequest("Unknown campus zone"));
        Set<Long> seeking = seekingSubjectIds == null ? Set.of() : seekingSubjectIds;
        Map<Long, StudentSubject> own = studentSubjects.findByStudent(studentId).stream()
                .collect(Collectors.toMap(s -> s.getSubject().getId(), s -> s));
        if (!own.keySet().containsAll(seeking)) {
            throw ApiException.badRequest("You can only pick subjects from your own profile");
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofMinutes(props.presenceMinutes()));
        Presence presence = presences.findById(studentId).orElseGet(() ->
                new Presence(students.getReferenceById(studentId), zone, status, now, expiresAt));
        presence.setZone(zone);
        presence.setStatus(status);
        presence.setRequirements(requirements == null || requirements.isBlank() ? null : requirements.strip());
        presence.setExpiresAt(expiresAt);
        presence.setUpdatedAt(now);
        presence.replaceSeeking(seeking);
        presences.saveAndFlush(presence);

        events.publishEvent(new PresenceChanged(studentId, PresenceChanged.Kind.CHECKED_IN));
        return views(List.of(presence)).getFirst();
    }

    @Transactional
    public void checkOut(UUID studentId) {
        if (presences.existsById(studentId)) {
            presences.deleteById(studentId);
            presences.flush();
            events.publishEvent(new PresenceChanged(studentId, PresenceChanged.Kind.CHECKED_OUT));
        }
    }

    @Transactional(readOnly = true)
    public Optional<PresenceView> mine(UUID studentId) {
        Instant now = clock.instant();
        return presences.findById(studentId).filter(p -> p.isActive(now)).map(p -> views(List.of(p)).getFirst());
    }

    /** Other students who are on campus and open to studying right now, optionally within one zone. */
    @Transactional(readOnly = true)
    public List<PresenceView> available(UUID viewerId, Long zoneId) {
        List<Presence> active = presences.findAvailable(clock.instant()).stream()
                .filter(p -> !p.getStudentId().equals(viewerId))
                .filter(p -> zoneId == null || p.getZone().getId().equals(zoneId))
                .toList();
        return views(active);
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void expireStale() {
        List<UUID> expired = presences.findExpiredIds(clock.instant());
        if (expired.isEmpty()) {
            return;
        }
        presences.deleteAllByIdInBatch(expired);
        log.debug("Expired {} presences", expired.size());
        expired.forEach(id -> events.publishEvent(new PresenceChanged(id, PresenceChanged.Kind.EXPIRED)));
    }

    private List<PresenceView> views(Collection<Presence> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = list.stream().map(Presence::getStudentId).collect(Collectors.toSet());
        Map<UUID, List<StudentSubject>> subjectsByStudent = studentSubjects.findByStudents(ids).stream()
                .collect(Collectors.groupingBy(StudentSubject::studentId));
        return list.stream().map(p -> {
            List<StudentSubject> subjects = subjectsByStudent.getOrDefault(p.getStudentId(), List.of());
            Map<Long, String> names = subjects.stream()
                    .collect(Collectors.toMap(s -> s.getSubject().getId(), s -> s.getSubject().getName()));
            List<String> seeking = p.getSeekingSubjectIds().stream().map(names::get)
                    .filter(java.util.Objects::nonNull).sorted().toList();
            List<SubjectLevel> levels = subjects.stream()
                    .sorted(Comparator.comparing((StudentSubject s) -> s.effectiveLevel().weight()).reversed()
                            .thenComparing(s -> s.getSubject().getName()))
                    .map(s -> new SubjectLevel(s.getSubject().getName(), s.effectiveLevel(),
                            s.getVerifiedLevel() != null)).toList();
            return new PresenceView(p.getStudentId(), p.getStudent().getFullName(), p.getZone().getId(),
                    p.getZone().getName(), p.getStatus(), p.getRequirements(), seeking, levels, p.getExpiresAt());
        }).toList();
    }
}

package edu.campusconnect.matching;

import edu.campusconnect.presence.Presence;
import edu.campusconnect.student.StudentSubject;
import edu.campusconnect.student.StudentSubjectRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Turns presences into {@link Candidate}s, deriving which subjects each student wants to work on today. */
@Component
class CandidateLoader {

    private static final int MIN_MENTION_LENGTH = 2;

    private final StudentSubjectRepository studentSubjects;

    CandidateLoader(StudentSubjectRepository studentSubjects) {
        this.studentSubjects = studentSubjects;
    }

    List<Candidate> load(Collection<Presence> presences) {
        if (presences.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = presences.stream().map(Presence::getStudentId).collect(Collectors.toSet());
        Map<UUID, List<StudentSubject>> subjects = studentSubjects.findByStudents(ids).stream()
                .collect(Collectors.groupingBy(StudentSubject::studentId));
        return presences.stream().map(p -> candidate(p, subjects.getOrDefault(p.getStudentId(), List.of()))).toList();
    }

    private static Candidate candidate(Presence presence, List<StudentSubject> subjects) {
        Map<Long, Integer> levels = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        for (StudentSubject s : subjects) {
            levels.put(s.getSubject().getId(), s.getProficiency().weight());
            names.put(s.getSubject().getId(), s.getSubject().getName());
        }
        Set<Long> active = new HashSet<>();
        for (Long id : presence.getSeekingSubjectIds()) {
            if (levels.containsKey(id)) {
                active.add(id);
            }
        }
        active.addAll(mentionedIn(presence.getRequirements(), names));
        if (active.isEmpty()) {
            active.addAll(levels.keySet());
        }
        var zone = presence.getZone();
        return new Candidate(presence.getStudentId(), zone.getId(), zone.getX(), zone.getY(), levels, active);
    }

    /** Subjects whose name appears in the student's free-text requirement ("need help with Graph Theory"). */
    static Set<Long> mentionedIn(String requirement, Map<Long, String> subjectNames) {
        Set<Long> mentioned = new HashSet<>();
        if (requirement == null || requirement.isBlank()) {
            return mentioned;
        }
        String text = requirement.toLowerCase(Locale.ROOT);
        subjectNames.forEach((id, name) -> {
            String needle = name.toLowerCase(Locale.ROOT);
            if (needle.length() >= MIN_MENTION_LENGTH && text.contains(needle)) {
                mentioned.add(id);
            }
        });
        return mentioned;
    }
}

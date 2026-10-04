package edu.campusconnect.student;

import edu.campusconnect.common.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    public record Item(Long id, String name, Proficiency proficiency) {}

    public record Profile(UUID id, String fullName, String email, String bio, List<Item> skills, List<Item> subjects) {}

    private static final int SUGGESTION_LIMIT = 15;

    private final StudentRepository students;
    private final SkillRepository skills;
    private final SubjectRepository subjects;
    private final StudentSkillRepository studentSkills;
    private final StudentSubjectRepository studentSubjects;

    public ProfileService(StudentRepository students, SkillRepository skills, SubjectRepository subjects,
                          StudentSkillRepository studentSkills, StudentSubjectRepository studentSubjects) {
        this.students = students;
        this.skills = skills;
        this.subjects = subjects;
        this.studentSkills = studentSkills;
        this.studentSubjects = studentSubjects;
    }

    @Transactional(readOnly = true)
    public Profile get(Student student) {
        return new Profile(student.getId(), student.getFullName(), student.getEmail(), student.getBio(),
                studentSkills.findByStudent(student.getId()).stream()
                        .map(x -> new Item(x.getSkill().getId(), x.getSkill().getName(), x.getProficiency())).toList(),
                studentSubjects.findByStudent(student.getId()).stream()
                        .map(x -> new Item(x.getSubject().getId(), x.getSubject().getName(), x.getProficiency())).toList());
    }

    @Transactional
    public Profile update(Student student, String fullName, String bio) {
        Student managed = students.getReferenceById(student.getId());
        managed.setFullName(fullName.strip());
        managed.setBio(bio == null || bio.isBlank() ? null : bio.strip());
        return get(managed);
    }

    /** Adds the skill or, if the student already has it, updates the proficiency. */
    @Transactional
    public Profile saveSkill(Student student, String rawName, Proficiency proficiency) {
        String name = normalize(rawName);
        skills.insertIfAbsent(name);
        Skill skill = skills.findByNameIgnoreCase(name).orElseThrow();
        studentSkills.findById(new StudentSkill.Id(student.getId(), skill.getId()))
                .ifPresentOrElse(existing -> existing.setProficiency(proficiency),
                        () -> studentSkills.save(new StudentSkill(student.getId(), skill, proficiency)));
        return get(student);
    }

    @Transactional
    public Profile removeSkill(Student student, Long skillId) {
        studentSkills.deleteById(new StudentSkill.Id(student.getId(), skillId));
        return get(student);
    }

    @Transactional
    public Profile saveSubject(Student student, String rawName, Proficiency proficiency) {
        String name = normalize(rawName);
        subjects.insertIfAbsent(name);
        Subject subject = subjects.findByNameIgnoreCase(name).orElseThrow();
        studentSubjects.findById(new StudentSubject.Id(student.getId(), subject.getId()))
                .ifPresentOrElse(existing -> existing.setProficiency(proficiency),
                        () -> studentSubjects.save(new StudentSubject(student.getId(), subject, proficiency)));
        return get(student);
    }

    @Transactional
    public Profile removeSubject(Student student, Long subjectId) {
        studentSubjects.deleteById(new StudentSubject.Id(student.getId(), subjectId));
        return get(student);
    }

    @Transactional(readOnly = true)
    public List<String> suggestSkills(String query) {
        return skills.search(query == null ? "" : query.strip(), PageRequest.of(0, SUGGESTION_LIMIT)).stream()
                .map(Skill::getName).toList();
    }

    @Transactional(readOnly = true)
    public List<String> suggestSubjects(String query) {
        return subjects.search(query == null ? "" : query.strip(), PageRequest.of(0, SUGGESTION_LIMIT)).stream()
                .map(Subject::getName).toList();
    }

    /** Trims and collapses internal whitespace so "  data   structures " equals "data structures". */
    static String normalize(String raw) {
        String name = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw ApiException.badRequest("Name must not be blank");
        }
        return name;
    }
}

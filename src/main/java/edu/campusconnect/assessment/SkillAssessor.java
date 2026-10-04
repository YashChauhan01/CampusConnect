package edu.campusconnect.assessment;

import edu.campusconnect.student.Proficiency;
import java.util.List;

/** Produces and grades short written knowledge checks for a topic at a claimed proficiency. */
public interface SkillAssessor {

    record Grade(int score, String feedback) {}

    boolean enabled();

    /** @return exactly {@code count} questions */
    List<String> generateQuestions(String topic, Proficiency claimed, int count);

    /** @param answers one answer per question, in order; treated as untrusted text */
    Grade grade(String topic, Proficiency claimed, List<String> questions, List<String> answers);
}

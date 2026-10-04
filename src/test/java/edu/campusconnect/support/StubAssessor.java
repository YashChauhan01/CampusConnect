package edu.campusconnect.support;

import edu.campusconnect.assessment.SkillAssessor;
import edu.campusconnect.student.Proficiency;
import java.util.ArrayList;
import java.util.List;

/** Deterministic assessor for integration tests: no network, score and availability set by the test. */
public class StubAssessor implements SkillAssessor {

    public volatile boolean enabled = true;
    public volatile int score = 90;
    public final List<List<String>> gradedAnswers = new ArrayList<>();

    public void reset() {
        enabled = true;
        score = 90;
        gradedAnswers.clear();
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public List<String> generateQuestions(String topic, Proficiency claimed, int count) {
        List<String> questions = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            questions.add("Question " + i + " about " + topic + " (" + claimed + ")");
        }
        return questions;
    }

    @Override
    public Grade grade(String topic, Proficiency claimed, List<String> questions, List<String> answers) {
        gradedAnswers.add(answers);
        return new Grade(score, "Feedback for " + topic);
    }
}

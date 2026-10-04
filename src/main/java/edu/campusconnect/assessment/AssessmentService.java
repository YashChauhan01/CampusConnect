package edu.campusconnect.assessment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.campusconnect.common.ApiException;
import edu.campusconnect.common.RateLimiter;
import edu.campusconnect.student.Proficiency;
import edu.campusconnect.student.StudentSkill;
import edu.campusconnect.student.StudentSkillRepository;
import edu.campusconnect.student.StudentSubject;
import edu.campusconnect.student.StudentSubjectRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI-assisted skill verification. A student takes a short written check on one of their skills or subjects; the
 * result can only <em>lower</em> the level the platform trusts (matching and team synthesis use the capped level), so
 * the feature rewards honesty without letting anyone inflate a claim.
 *
 * <p>The slow model calls happen outside database transactions so connections are not held while waiting.
 */
@Service
public class AssessmentService {

    private static final int MAX_ANSWER_LENGTH = 2000;
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    public record StatusView(boolean enabled, int questionCount) {}

    public record AssessmentView(UUID id, SkillAssessment.Kind kind, Long itemId, String topic, Proficiency claimedLevel,
                                 List<String> questions, SkillAssessment.Status status, Integer score,
                                 Proficiency verifiedLevel, String feedback) {}

    private final SkillAssessor assessor;
    private final AiProperties props;
    private final SkillAssessmentRepository assessments;
    private final StudentSkillRepository skills;
    private final StudentSubjectRepository subjects;
    private final RateLimiter limiter;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AssessmentService(SkillAssessor assessor, AiProperties props, SkillAssessmentRepository assessments,
                             StudentSkillRepository skills, StudentSubjectRepository subjects, RateLimiter limiter,
                             TransactionTemplate tx, ObjectMapper mapper, Clock clock) {
        this.assessor = assessor;
        this.props = props;
        this.assessments = assessments;
        this.skills = skills;
        this.subjects = subjects;
        this.limiter = limiter;
        this.tx = tx;
        this.mapper = mapper;
        this.clock = clock;
    }

    public StatusView status() {
        return new StatusView(assessor.enabled(), props.questionCount());
    }

    /** Starts (or resumes) a check for one of the student's own skills or subjects. */
    public AssessmentView start(UUID studentId, SkillAssessment.Kind kind, Long itemId) {
        requireEnabled();
        Target target = tx.execute(status -> target(studentId, kind, itemId));

        SkillAssessment pending = tx.execute(status -> assessments.findPending(studentId, kind, itemId).orElse(null));
        if (pending != null) {
            return view(pending);
        }
        Instant now = clock.instant();
        SkillAssessment last = tx.execute(status -> assessments.findLastGraded(studentId, kind, itemId).orElse(null));
        if (last != null && last.getGradedAt().plus(props.retakeCooldown()).isAfter(now)) {
            long minutes = Math.max(1, Duration.between(now, last.getGradedAt().plus(props.retakeCooldown())).toMinutes());
            throw ApiException.conflict("You can retake this check in about " + minutes + " minutes");
        }
        limiter.check("assess:" + studentId, 6, Duration.ofHours(1));

        List<String> questions = assessor.generateQuestions(target.topic(), target.level(), props.questionCount());
        return tx.execute(status -> view(assessments.save(new SkillAssessment(studentId, kind, itemId, target.topic(),
                target.level(), json(questions), now))));
    }

    /** Grades the answers and applies the result to the student's profile. */
    public AssessmentView submit(UUID studentId, UUID assessmentId, List<String> answers) {
        requireEnabled();
        SkillAssessment assessment = tx.execute(status -> assessments.findById(assessmentId)
                .filter(a -> a.getStudentId().equals(studentId))
                .orElseThrow(() -> ApiException.notFound("Assessment not found")));
        if (assessment.getStatus() != SkillAssessment.Status.PENDING) {
            throw ApiException.conflict("This check has already been graded");
        }
        List<String> questions = parse(assessment.getQuestions());
        if (answers == null || answers.size() != questions.size()) {
            throw ApiException.badRequest("Please answer all " + questions.size() + " questions");
        }
        List<String> cleaned = answers.stream().map(a -> a == null ? "" : a.strip()).toList();
        if (cleaned.stream().anyMatch(a -> a.length() > MAX_ANSWER_LENGTH)) {
            throw ApiException.badRequest("Answers may be at most " + MAX_ANSWER_LENGTH + " characters");
        }

        SkillAssessor.Grade grade = assessor.grade(assessment.getTopic(), assessment.getClaimedLevel(), questions, cleaned);
        Proficiency verified = verifiedLevel(assessment.getClaimedLevel(), grade.score());

        return tx.execute(status -> {
            SkillAssessment locked = assessments.findForUpdate(assessmentId).orElseThrow();
            if (locked.getStatus() != SkillAssessment.Status.PENDING) {
                throw ApiException.conflict("This check has already been graded");
            }
            locked.complete(grade.score(), verified, grade.feedback(), clock.instant());
            applyVerification(locked, verified);
            return view(locked);
        });
    }

    /** Maps a 0-100 score to the level the student demonstrated; never above the claim. */
    Proficiency verifiedLevel(Proficiency claimed, int score) {
        if (score >= props.passScore()) {
            return claimed;
        }
        if (score >= props.partialScore()) {
            return claimed.oneLevelDown();
        }
        return Proficiency.BEGINNER;
    }

    private void applyVerification(SkillAssessment a, Proficiency verified) {
        if (a.getKind() == SkillAssessment.Kind.SKILL) {
            skills.findById(new StudentSkill.Id(a.getStudentId(), a.getItemId())).ifPresent(s -> {
                if (s.getProficiency() == a.getClaimedLevel()) {
                    s.setVerifiedLevel(verified);
                }
            });
        } else {
            subjects.findById(new StudentSubject.Id(a.getStudentId(), a.getItemId())).ifPresent(s -> {
                if (s.getProficiency() == a.getClaimedLevel()) {
                    s.setVerifiedLevel(verified);
                }
            });
        }
    }

    private record Target(String topic, Proficiency level) {}

    private Target target(UUID studentId, SkillAssessment.Kind kind, Long itemId) {
        if (kind == SkillAssessment.Kind.SKILL) {
            return skills.findById(new StudentSkill.Id(studentId, itemId))
                    .map(s -> new Target(s.getSkill().getName(), s.getProficiency()))
                    .orElseThrow(() -> ApiException.notFound("Skill not found on your profile"));
        }
        return subjects.findById(new StudentSubject.Id(studentId, itemId))
                .map(s -> new Target(s.getSubject().getName(), s.getProficiency()))
                .orElseThrow(() -> ApiException.notFound("Subject not found on your profile"));
    }

    private void requireEnabled() {
        if (!assessor.enabled()) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "AI_DISABLED",
                    "Skill verification is not enabled on this server");
        }
    }

    private AssessmentView view(SkillAssessment a) {
        return new AssessmentView(a.getId(), a.getKind(), a.getItemId(), a.getTopic(), a.getClaimedLevel(),
                parse(a.getQuestions()), a.getStatus(), a.getScore(), a.getVerifiedLevel(), a.getFeedback());
    }

    private String json(List<String> values) {
        try {
            return mapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> parse(String json) {
        try {
            return mapper.readValue(json, STRINGS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt stored questions", e);
        }
    }
}

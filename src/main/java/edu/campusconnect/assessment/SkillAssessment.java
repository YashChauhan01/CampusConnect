package edu.campusconnect.assessment;

import edu.campusconnect.student.Proficiency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "skill_assessments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkillAssessment {

    public enum Kind {SKILL, SUBJECT}

    public enum Status {PENDING, GRADED}

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false, length = 100)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "claimed_level", nullable = false, length = 20)
    private Proficiency claimedLevel;

    /** JSON array of the questions asked, kept server-side so answers are always graded against them. */
    @Column(nullable = false, columnDefinition = "text")
    private String questions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.PENDING;

    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(name = "verified_level", length = 20)
    private Proficiency verifiedLevel;

    @Column(length = 1000)
    private String feedback;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "graded_at")
    private Instant gradedAt;

    public SkillAssessment(UUID studentId, Kind kind, Long itemId, String topic, Proficiency claimedLevel,
                           String questionsJson, Instant createdAt) {
        this.studentId = studentId;
        this.kind = kind;
        this.itemId = itemId;
        this.topic = topic;
        this.claimedLevel = claimedLevel;
        this.questions = questionsJson;
        this.createdAt = createdAt;
    }

    public void complete(int score, Proficiency verified, String feedback, Instant now) {
        this.status = Status.GRADED;
        this.score = score;
        this.verifiedLevel = verified;
        this.feedback = feedback;
        this.gradedAt = now;
    }
}

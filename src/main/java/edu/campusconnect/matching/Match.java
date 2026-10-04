package edu.campusconnect.matching;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A proposed pairing of two students; becomes ACCEPTED only when both accept. */
@Entity
@Table(name = "matches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Match {

    public enum Status {PROPOSED, ACCEPTED, DECLINED, EXPIRED, COMPLETED}

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "round_id", nullable = false)
    private UUID roundId;

    @Column(name = "student_a", nullable = false)
    private UUID studentA;

    @Column(name = "student_b", nullable = false)
    private UUID studentB;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PROPOSED;

    @Column(nullable = false)
    private double weight;
    @Column(nullable = false)
    private double knowledge;
    @Column(nullable = false)
    private double reciprocity;
    @Column(nullable = false)
    private double breadth;
    @Column(nullable = false)
    private double proximity;

    @Setter
    @Column(name = "accepted_a", nullable = false)
    private boolean acceptedA;

    @Setter
    @Column(name = "accepted_b", nullable = false)
    private boolean acceptedB;

    @Setter
    @Column(name = "declined_by")
    private UUID declinedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Setter
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Setter
    @Column(name = "decided_at")
    private Instant decidedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "match_subjects", joinColumns = @JoinColumn(name = "match_id"))
    @Column(name = "subject_id")
    private Set<Long> sharedSubjectIds = new HashSet<>();

    public Match(UUID roundId, UUID studentA, UUID studentB, CompatibilityScorer.Score score, Instant now,
                 Instant expiresAt) {
        this.roundId = roundId;
        this.studentA = studentA;
        this.studentB = studentB;
        this.weight = score.weight();
        this.knowledge = score.knowledge();
        this.reciprocity = score.reciprocity();
        this.breadth = score.breadth();
        this.proximity = score.proximity();
        this.createdAt = now;
        this.expiresAt = expiresAt;
        this.sharedSubjectIds.addAll(score.sharedSubjects());
    }

    public boolean involves(UUID studentId) {
        return studentA.equals(studentId) || studentB.equals(studentId);
    }

    public UUID partnerOf(UUID studentId) {
        return studentA.equals(studentId) ? studentB : studentA;
    }

    public boolean hasAccepted(UUID studentId) {
        return studentA.equals(studentId) ? acceptedA : acceptedB;
    }

    public void markAccepted(UUID studentId) {
        if (studentA.equals(studentId)) {
            acceptedA = true;
        } else {
            acceptedB = true;
        }
    }

    public boolean isLive() {
        return status == Status.PROPOSED || status == Status.ACCEPTED;
    }
}

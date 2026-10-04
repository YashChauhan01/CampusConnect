package edu.campusconnect.student;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "student_subjects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentSubject {

    @Embeddable
    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {
        @Column(name = "student_id")
        private UUID studentId;
        @Column(name = "subject_id")
        private Long subjectId;
    }

    @EmbeddedId
    private Id id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("subjectId")
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Proficiency proficiency;

    /** Level confirmed by an assessment; never above {@link #proficiency}. Cleared when the student changes the claim. */
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "verified_level", length = 20)
    private Proficiency verifiedLevel;

    public StudentSubject(UUID studentId, Subject subject, Proficiency proficiency) {
        this.id = new Id(studentId, subject.getId());
        this.subject = subject;
        this.proficiency = proficiency;
    }

    /** Re-states the claim; an old verification no longer applies to a different claim. */
    public void claim(Proficiency level) {
        if (level != proficiency) {
            proficiency = level;
            verifiedLevel = null;
        }
    }

    /** The level the platform trusts: the claim, capped by any verification result. */
    public Proficiency effectiveLevel() {
        return verifiedLevel == null ? proficiency : Proficiency.lower(proficiency, verifiedLevel);
    }

    public UUID studentId() {
        return id.getStudentId();
    }
}

package edu.campusconnect.presence;

import edu.campusconnect.student.Student;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

/** A student's current, expiring declaration of where they are and what they want to study. */
@Entity
@Table(name = "presence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Presence implements Persistable<UUID> {

    public enum Status {AVAILABLE, BUSY}

    @Id
    @Column(name = "student_id")
    private UUID studentId;

    @MapsId
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @Setter
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private CampusZone zone;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Setter
    @Column(length = 300)
    private String requirements;

    @Setter
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Setter
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "presence_seeking", joinColumns = @JoinColumn(name = "student_id"))
    @Column(name = "subject_id")
    private Set<Long> seekingSubjectIds = new HashSet<>();

    /** The id is assigned (shared with the student), so Spring Data cannot infer newness from a null id. */
    @Transient
    private boolean isNew = true;

    @Override
    public UUID getId() {
        return studentId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    public Presence(Student student, CampusZone zone, Status status, Instant now, Instant expiresAt) {
        this.studentId = student.getId();
        this.student = student;
        this.zone = zone;
        this.status = status;
        this.updatedAt = now;
        this.expiresAt = expiresAt;
    }

    public void replaceSeeking(Set<Long> subjectIds) {
        seekingSubjectIds.clear();
        seekingSubjectIds.addAll(subjectIds);
    }

    public boolean isActive(Instant now) {
        return expiresAt.isAfter(now);
    }
}

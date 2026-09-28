package edu.campusconnect.model;

import jakarta.persistence.*;

import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "presence")
public class Presence {
    @Id
    @Column(name = "student_id")
    public UUID studentId;
    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "student_id")
    public Student student;
    @ManyToOne
    @JoinColumn(name = "zone_id")
    public CampusZone zone;
    @Enumerated(EnumType.STRING)
    public Status status;
    @Column(name = "expires_at")
    public Instant expiresAt;
    @Column(name = "updated_at")
    public Instant updatedAt;

    public enum Status {AVAILABLE, BUSY}

    protected Presence() {
    }

    public Presence(Student s, CampusZone z, Status status, Instant expires) {
        studentId = s.id;
        student = s;
        zone = z;
        this.status = status;
        expiresAt = expires;
        updatedAt = Instant.now();
    }
}

package edu.campusconnect.hackathon;

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
import lombok.Setter;

@Entity
@Table(name = "hackathons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hackathon {

    /** OPEN: registering. CLOSED: registration frozen, organiser reviews teams. PUBLISHED: teams are final and visible. */
    public enum Status {OPEN, CLOSED, PUBLISHED}

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Hackathon(UUID organizerId, String name, String description, Instant createdAt) {
        this.organizerId = organizerId;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }
}

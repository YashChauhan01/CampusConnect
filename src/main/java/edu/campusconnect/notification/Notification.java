package edu.campusconnect.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    public enum Type {
        MATCH_PROPOSED, MATCH_PARTNER_ACCEPTED, MATCH_CONFIRMED, MATCH_DECLINED, MATCH_EXPIRED, TEAMS_PUBLISHED
    }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Type type;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(length = 200)
    private String link;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Setter
    @Column(name = "read_at")
    private Instant readAt;

    public Notification(UUID studentId, Type type, String title, String body, String link, Instant createdAt) {
        this.studentId = studentId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.link = link;
        this.createdAt = createdAt;
    }
}

package edu.campusconnect.matching;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A message between the two students of a confirmed match. */
@Entity
@Table(name = "match_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ChatMessage(UUID matchId, UUID senderId, String body, Instant createdAt) {
        this.matchId = matchId;
        this.senderId = senderId;
        this.body = body;
        this.createdAt = createdAt;
    }
}

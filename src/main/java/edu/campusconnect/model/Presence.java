package edu.campusconnect.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RedisHash("Presence")
public class Presence {
    @Id
    public UUID id;
    public String studentName;
    @Indexed
    public Long zoneId;
    public String zoneName;
    @Indexed
    public Status status;
    public Instant expiresAt;
    public Instant updatedAt;
    @TimeToLive
    public Long timeToLiveSeconds;

    public enum Status {AVAILABLE, BUSY}

    protected Presence() {
    }

    public Presence(UUID studentId, String studentName, Long zoneId, String zoneName,
                    Status status, Duration timeToLive) {
        if (timeToLive.isZero() || timeToLive.isNegative()) {
            throw new IllegalArgumentException("Presence TTL must be positive");
        }

        Instant now = Instant.now();
        id = studentId;
        this.studentName = studentName;
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.status = status;
        expiresAt = now.plus(timeToLive);
        updatedAt = now;
        timeToLiveSeconds = timeToLive.getSeconds();
    }
}

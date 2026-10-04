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

/** Audit/evaluation record of one matching run. */
@Entity
@Table(name = "match_rounds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchRound {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "pool_size", nullable = false)
    private int poolSize;

    @Column(name = "edge_count", nullable = false)
    private int edgeCount;

    @Column(name = "pair_count", nullable = false)
    private int pairCount;

    @Column(name = "total_weight", nullable = false)
    private double totalWeight;

    @Column(name = "duration_ms", nullable = false)
    private double durationMs;

    public MatchRound(Instant startedAt, int poolSize, int edgeCount, int pairCount, double totalWeight,
                      double durationMs) {
        this.startedAt = startedAt;
        this.poolSize = poolSize;
        this.edgeCount = edgeCount;
        this.pairCount = pairCount;
        this.totalWeight = totalWeight;
        this.durationMs = durationMs;
    }
}

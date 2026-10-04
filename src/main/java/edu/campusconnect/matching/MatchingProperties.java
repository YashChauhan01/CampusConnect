package edu.campusconnect.matching;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Tunables for peer matchmaking (prefix {@code app.matching}). */
@Validated
@ConfigurationProperties(prefix = "app.matching")
public record MatchingProperties(
        @DefaultValue("true") boolean schedulerEnabled,
        @DefaultValue("20s") Duration roundInterval,
        @DefaultValue("5m") Duration proposalTtl,
        @DefaultValue("3h") Duration acceptedTtl,
        @DefaultValue("24h") Duration declineCooldown,
        @DefaultValue("5s") Duration onDemandCooldown,
        @DefaultValue("0.30") @DecimalMin("0") @DecimalMax("1") double minEdgeWeight,
        @DefaultValue("120") @DecimalMin("1") double proximityScaleMeters,
        @DefaultValue("0.3") @DecimalMin("0") @DecimalMax("1") double unknownDistanceProximity,
        @DefaultValue("500") @Min(2) int maxPoolSize,
        @Valid @DefaultValue Weights weights) {

    /** Relative importance of each compatibility component; normalised so that they sum to one. */
    public record Weights(
            @DefaultValue("0.45") @DecimalMin("0") double knowledge,
            @DefaultValue("0.15") @DecimalMin("0") double reciprocity,
            @DefaultValue("0.15") @DecimalMin("0") double breadth,
            @DefaultValue("0.25") @DecimalMin("0") double proximity) {

        public double sum() {
            return knowledge + reciprocity + breadth + proximity;
        }
    }
}

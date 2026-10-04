package edu.campusconnect.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Typed, validated application settings (prefix {@code app}). The app refuses to start on invalid values. */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String frontendUrl,
        @NotBlank @Size(min = 32, message = "must be at least 32 characters") String jwtSecret,
        boolean secureCookies,
        @NotEmpty List<String> approvedDomains,
        @DefaultValue("30") @Min(1) int verificationMinutes,
        @DefaultValue("30") @Min(1) int resetMinutes,
        @DefaultValue("15") @Min(1) int accessTokenMinutes,
        @DefaultValue("7") @Min(1) int refreshTokenDays,
        @DefaultValue("15") @Min(1) int presenceMinutes,
        @DefaultValue("12") @Min(4) @Max(16) int bcryptStrength,
        @Valid @DefaultValue Mail mail,
        @Valid @DefaultValue RateLimit rateLimit) {

    public AppProperties {
        approvedDomains = approvedDomains == null ? List.of()
                : approvedDomains.stream().map(d -> d.trim().toLowerCase(Locale.ROOT)).filter(d -> !d.isEmpty()).toList();
    }

    public boolean isApprovedDomain(String domain) {
        return approvedDomains.contains(domain.toLowerCase(Locale.ROOT));
    }

    /** {@code smtp} sends real mail; {@code log} writes messages to the application log (local development only). */
    public record Mail(@DefaultValue("smtp") String mode, @DefaultValue("noreply@campusconnect.local") String from) {}

    public record RateLimit(@DefaultValue("true") boolean enabled) {}
}

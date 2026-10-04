package edu.campusconnect.assessment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * AI-assisted skill verification (prefix {@code app.ai}). Disabled by default. Works with any provider that exposes
 * an OpenAI-compatible {@code /chat/completions} endpoint (hosted services, or a local server such as Ollama).
 */
@Validated
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("http://localhost:11434/v1") String baseUrl,
        @DefaultValue("") String apiKey,
        @DefaultValue("llama3.1") String model,
        @DefaultValue("60s") Duration timeout,
        @DefaultValue("4") @Min(3) @Max(6) int questionCount,
        /** Score at or above which the claimed level is confirmed. */
        @DefaultValue("75") @Min(1) @Max(100) int passScore,
        /** Score at or above which the student is placed one level below the claim; below it, at beginner. */
        @DefaultValue("45") @Min(0) @Max(100) int partialScore,
        @DefaultValue("1h") Duration retakeCooldown) {}

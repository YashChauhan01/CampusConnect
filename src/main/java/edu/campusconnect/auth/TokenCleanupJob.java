package edu.campusconnect.auth;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Removes long-expired verification, reset and refresh tokens. */
@Component
class TokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupJob.class);

    private final EmailTokenRepository emailTokens;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    TokenCleanupJob(EmailTokenRepository emailTokens, RefreshTokenRepository refreshTokens, Clock clock) {
        this.emailTokens = emailTokens;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Scheduled(cron = "0 15 * * * *")
    @Transactional
    void purge() {
        var cutoff = clock.instant().minus(Duration.ofDays(1));
        int email = emailTokens.deleteExpiredBefore(cutoff);
        int refresh = refreshTokens.deleteExpiredBefore(cutoff);
        if (email + refresh > 0) {
            log.info("Purged {} email tokens and {} refresh tokens", email, refresh);
        }
    }
}

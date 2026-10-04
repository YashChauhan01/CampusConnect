package edu.campusconnect.matching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically runs a matching round so proposals appear without anyone pressing a button. */
@Component
@ConditionalOnProperty(name = "app.matching.scheduler-enabled", havingValue = "true", matchIfMissing = true)
class MatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(MatchScheduler.class);

    private final MatchService service;

    MatchScheduler(MatchService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.matching.round-interval:20s}", initialDelayString = "${app.matching.round-interval:20s}")
    void tick() {
        try {
            service.runRound();
        } catch (RuntimeException e) {
            log.error("Scheduled matching round failed", e);
        }
    }
}

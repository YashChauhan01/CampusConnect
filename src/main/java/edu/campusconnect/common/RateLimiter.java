package edu.campusconnect.common;

import edu.campusconnect.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fixed-window, in-memory rate limiter keyed by an arbitrary string (e.g. {@code login:ip:1.2.3.4}).
 * State is per instance; use a shared limiter (gateway/Redis) if the API is scaled horizontally.
 */
@Component
public class RateLimiter {

    private static final long STALE_AFTER_MILLIS = Duration.ofHours(2).toMillis();

    private record Window(long startMillis, int count) {}

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final boolean enabled;

    public RateLimiter(Clock clock, AppProperties props) {
        this.clock = clock;
        this.enabled = props.rateLimit().enabled();
    }

    /** Records one hit for {@code key}; throws 429 when more than {@code max} hits occur within {@code window}. */
    public void check(String key, int max, Duration window) {
        if (!enabled) {
            return;
        }
        long now = clock.millis();
        long windowMillis = window.toMillis();
        Window updated = windows.compute(key, (k, w) ->
                (w == null || now - w.startMillis() >= windowMillis)
                        ? new Window(now, 1)
                        : new Window(w.startMillis(), w.count() + 1));
        if (updated.count() > max) {
            long retryAfter = Math.max(1, (updated.startMillis() + windowMillis - now + 999) / 1000);
            throw ApiException.tooManyRequests(retryAfter);
        }
    }

    @Scheduled(fixedDelay = 60_000)
    void evictStale() {
        long now = clock.millis();
        windows.entrySet().removeIf(e -> now - e.getValue().startMillis() > STALE_AFTER_MILLIS);
    }
}

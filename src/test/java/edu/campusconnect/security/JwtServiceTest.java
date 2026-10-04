package edu.campusconnect.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-of-at-least-32-bytes-1234567890";

    private final JwtService jwt = new JwtService(SECRET, Duration.ofMinutes(15), Clock.systemUTC());

    @Test
    void signedTokenRoundTrips() {
        UUID id = UUID.randomUUID();
        assertEquals(id, jwt.verify(jwt.issue(id)));
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwt.issue(UUID.randomUUID());
        assertThrows(JwtException.class, () -> jwt.verify(token.substring(0, token.length() - 5) + "XXXXX"));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService other = new JwtService("another-secret-of-at-least-32-bytes-0987654321", Duration.ofMinutes(15), Clock.systemUTC());
        assertThrows(JwtException.class, () -> jwt.verify(other.issue(UUID.randomUUID())));
    }

    @Test
    void expiredTokenIsRejected() {
        Instant start = Instant.parse("2026-01-01T10:00:00Z");
        JwtService early = new JwtService(SECRET, Duration.ofMinutes(15), Clock.fixed(start, ZoneOffset.UTC));
        String token = early.issue(UUID.randomUUID());

        JwtService later = new JwtService(SECRET, Duration.ofMinutes(15),
                Clock.fixed(start.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));
        assertThrows(ExpiredJwtException.class, () -> later.verify(token));
    }

    @Test
    void shortSecretIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtService("too-short", Duration.ofMinutes(1), Clock.systemUTC()));
    }
}

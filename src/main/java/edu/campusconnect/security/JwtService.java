package edu.campusconnect.security;

import edu.campusconnect.config.AppProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Issues and verifies short-lived HS256 access tokens whose subject is the student id. */
@Service
public class JwtService {

    static final String ISSUER = "campusconnect";

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public JwtService(AppProperties props, Clock clock) {
        this(props.jwtSecret(), Duration.ofMinutes(props.accessTokenMinutes()), clock);
    }

    public JwtService(String secret, Duration ttl, Clock clock) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttl = ttl;
        this.clock = clock;
    }

    public String issue(UUID studentId) {
        Date now = Date.from(clock.instant());
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(studentId.toString())
                .issuedAt(now)
                .expiration(Date.from(clock.instant().plus(ttl)))
                .signWith(key)
                .compact();
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    /** @throws io.jsonwebtoken.JwtException if the token is malformed, tampered with or expired */
    public UUID verify(String token) {
        String subject = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return UUID.fromString(subject);
    }
}

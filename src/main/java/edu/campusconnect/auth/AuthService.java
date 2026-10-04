package edu.campusconnect.auth;

import edu.campusconnect.common.ApiException;
import edu.campusconnect.common.RateLimiter;
import edu.campusconnect.config.AppProperties;
import edu.campusconnect.mail.MailService;
import edu.campusconnect.security.JwtService;
import edu.campusconnect.student.Student;
import edu.campusconnect.student.StudentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** A just-rotated token presented again within this window is a benign race (two tabs), not theft. */
    private static final Duration REFRESH_REUSE_GRACE = Duration.ofSeconds(10);
    private static final int MIN_PASSWORD_LENGTH = 10;

    public record Session(String accessToken, long expiresInSeconds, String refreshToken) {}

    private final StudentRepository students;
    private final EmailTokenRepository emailTokens;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final MailService mail;
    private final RateLimiter limiter;
    private final AppProperties props;
    private final Clock clock;
    /** Verified against when the account does not exist so that response time does not reveal it. */
    private final String decoyHash;

    public AuthService(StudentRepository students, EmailTokenRepository emailTokens, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwords, JwtService jwt, MailService mail, RateLimiter limiter,
                       AppProperties props, Clock clock) {
        this.students = students;
        this.emailTokens = emailTokens;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.jwt = jwt;
        this.mail = mail;
        this.limiter = limiter;
        this.props = props;
        this.clock = clock;
        this.decoyHash = passwords.encode(TokenHasher.newToken());
    }

    /**
     * Registers an account. The outcome is identical whether or not the email is already known, so the endpoint
     * cannot be used to discover who has an account.
     */
    @Transactional
    public void register(String fullName, String rawEmail, String password, String ip) {
        limiter.check("register:ip:" + ip, 10, Duration.ofHours(1));
        String email = normalizeEmail(rawEmail);
        if (!props.isApprovedDomain(domainOf(email))) {
            throw ApiException.badRequest("Please use your college email address");
        }
        validatePassword(password, email);

        Optional<Student> existing = students.findByEmail(email);
        if (existing.isEmpty()) {
            Student student = students.save(new Student(fullName.strip(), email, passwords.encode(password)));
            sendVerification(student);
        } else if (existing.get().isVerified()) {
            Student student = existing.get();
            mail.send(email, "Your CampusConnect account",
                    MailTemplates.alreadyRegistered(student.getFullName(), props.frontendUrl() + "/login",
                            props.frontendUrl() + "/forgot-password"));
        } else {
            sendVerification(existing.get());
        }
    }

    @Transactional
    public void verifyEmail(String token) {
        Instant now = clock.instant();
        EmailToken stored = emailTokens.findByHash(TokenHasher.hash(token), EmailToken.Purpose.VERIFY)
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> ApiException.badRequest("This verification link is invalid or has expired"));
        stored.setUsedAt(now);
        stored.getStudent().setVerified(true);
    }

    @Transactional
    public void resendVerification(String rawEmail, String ip) {
        limiter.check("resend:ip:" + ip, 10, Duration.ofHours(1));
        String email = normalizeEmail(rawEmail);
        limiter.check("resend:email:" + email, 3, Duration.ofHours(1));
        students.findByEmail(email).filter(s -> !s.isVerified()).ifPresent(this::sendVerification);
    }

    @Transactional
    public Session login(String rawEmail, String password, String ip) {
        String email = normalizeEmail(rawEmail);
        limiter.check("login:ip:" + ip, 30, Duration.ofMinutes(10));
        limiter.check("login:email:" + email, 8, Duration.ofMinutes(15));

        Optional<Student> found = students.findByEmail(email);
        String hash = found.map(Student::getPasswordHash).orElse(decoyHash);
        boolean passwordOk = passwords.matches(password == null ? "" : password, hash);
        if (found.isEmpty() || !passwordOk) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        Student student = found.get();
        if (!student.isVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
                    "Please verify your email address before logging in");
        }
        return issueSession(student.getId(), UUID.randomUUID());
    }

    /** Rotates the refresh token. Reuse of an already-rotated token revokes the whole family. */
    @Transactional(noRollbackFor = ApiException.class)
    public Session refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.unauthorized("No session");
        }
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findForUpdate(TokenHasher.hash(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("Invalid session"));

        if (token.getRevokedAt() != null) {
            if (token.getRevokedAt().plus(REFRESH_REUSE_GRACE).isBefore(now)) {
                log.warn("Refresh token reuse detected; revoking token family for student {}", token.getStudentId());
                refreshTokens.revokeFamily(token.getFamilyId(), now);
            }
            throw ApiException.unauthorized("Invalid session");
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw ApiException.unauthorized("Session expired");
        }
        if (students.findById(token.getStudentId()).filter(Student::isVerified).isEmpty()) {
            throw ApiException.unauthorized("Invalid session");
        }
        token.setRevokedAt(now);
        return issueSession(token.getStudentId(), token.getFamilyId());
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokens.findForUpdate(TokenHasher.hash(rawToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), clock.instant()));
    }

    @Transactional
    public void requestPasswordReset(String rawEmail, String ip) {
        limiter.check("forgot:ip:" + ip, 10, Duration.ofHours(1));
        String email = normalizeEmail(rawEmail);
        limiter.check("forgot:email:" + email, 3, Duration.ofHours(1));
        students.findByEmail(email).ifPresent(student -> {
            String raw = issueEmailToken(student, EmailToken.Purpose.RESET, props.resetMinutes());
            mail.send(student.getEmail(), "Reset your CampusConnect password",
                    MailTemplates.passwordReset(student.getFullName(),
                            props.frontendUrl() + "/reset-password?token=" + raw, props.resetMinutes()));
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        Instant now = clock.instant();
        EmailToken stored = emailTokens.findByHash(TokenHasher.hash(token), EmailToken.Purpose.RESET)
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> ApiException.badRequest("This reset link is invalid or has expired"));
        Student student = stored.getStudent();
        validatePassword(newPassword, student.getEmail());
        student.setPasswordHash(passwords.encode(newPassword));
        // Following the link proves control of the mailbox.
        student.setVerified(true);
        stored.setUsedAt(now);
        emailTokens.invalidateOutstanding(student.getId(), EmailToken.Purpose.RESET, now);
        refreshTokens.revokeAllForStudent(student.getId(), now);
    }

    private void sendVerification(Student student) {
        String raw = issueEmailToken(student, EmailToken.Purpose.VERIFY, props.verificationMinutes());
        mail.send(student.getEmail(), "Verify your CampusConnect account",
                MailTemplates.verification(student.getFullName(), props.frontendUrl() + "/verify?token=" + raw,
                        props.verificationMinutes()));
    }

    private String issueEmailToken(Student student, EmailToken.Purpose purpose, int validMinutes) {
        Instant now = clock.instant();
        emailTokens.invalidateOutstanding(student.getId(), purpose, now);
        String raw = TokenHasher.newToken();
        emailTokens.save(new EmailToken(student, TokenHasher.hash(raw), purpose,
                now.plus(Duration.ofMinutes(validMinutes))));
        return raw;
    }

    private Session issueSession(UUID studentId, UUID familyId) {
        String raw = TokenHasher.newToken();
        refreshTokens.save(new RefreshToken(studentId, familyId, TokenHasher.hash(raw),
                clock.instant().plus(Duration.ofDays(props.refreshTokenDays()))));
        return new Session(jwt.issue(studentId), jwt.ttlSeconds(), raw);
    }

    private static String normalizeEmail(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }

    private static String domainOf(String email) {
        return email.substring(email.lastIndexOf('@') + 1);
    }

    private static void validatePassword(String password, String email) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw ApiException.badRequest("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (password.length() > 128) {
            throw ApiException.badRequest("Password must be at most 128 characters");
        }
        if (password.equalsIgnoreCase(email)) {
            throw ApiException.badRequest("Password must not be your email address");
        }
    }
}

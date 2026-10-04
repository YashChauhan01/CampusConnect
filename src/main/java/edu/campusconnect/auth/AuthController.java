package edu.campusconnect.auth;

import edu.campusconnect.common.ClientIp;
import edu.campusconnect.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    static final String REFRESH_COOKIE = "cc_refresh";
    private static final String COOKIE_PATH = "/api/v1/auth";

    public record RegisterRequest(@NotBlank @Size(max = 120) String fullName,
                                  @NotBlank @Email @Size(max = 254) String email,
                                  @NotBlank @Size(max = 128) String password) {}

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {}

    public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {}

    public record TokenRequest(@NotBlank @Size(max = 200) String token) {}

    public record ResetRequest(@NotBlank @Size(max = 200) String token, @NotBlank @Size(max = 128) String password) {}

    public record AccessResponse(String accessToken, long expiresInSeconds) {}

    public record MessageResponse(String message) {}

    private final AuthService auth;
    private final AppProperties props;

    public AuthController(AuthService auth, AppProperties props) {
        this.auth = auth;
        this.props = props;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        auth.register(request.fullName(), request.email(), request.password(), ClientIp.of(http));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new MessageResponse("If the address can be used, a verification link is on its way"));
    }

    @PostMapping("/verify")
    public MessageResponse verify(@Valid @RequestBody TokenRequest request) {
        auth.verifyEmail(request.token());
        return new MessageResponse("Email verified. You can now log in.");
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resend(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        auth.resendVerification(request.email(), ClientIp.of(http));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new MessageResponse("If the account needs verification, a new link is on its way"));
    }

    @PostMapping("/login")
    public AccessResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
                                HttpServletResponse response) {
        return respond(auth.login(request.email(), request.password(), ClientIp.of(http)), response);
    }

    @PostMapping("/refresh")
    public AccessResponse refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token,
                                  HttpServletResponse response) {
        return respond(auth.refresh(token), response);
    }

    @PostMapping("/logout")
    public MessageResponse logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token,
                                  HttpServletResponse response) {
        auth.logout(token);
        setRefreshCookie(response, "", Duration.ZERO);
        return new MessageResponse("Logged out");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgot(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        auth.requestPasswordReset(request.email(), ClientIp.of(http));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new MessageResponse("If an account exists, a reset link is on its way"));
    }

    @PostMapping("/reset-password")
    public MessageResponse reset(@Valid @RequestBody ResetRequest request) {
        auth.resetPassword(request.token(), request.password());
        return new MessageResponse("Password updated. You can now log in.");
    }

    private AccessResponse respond(AuthService.Session session, HttpServletResponse response) {
        setRefreshCookie(response, session.refreshToken(), Duration.ofDays(props.refreshTokenDays()));
        return new AccessResponse(session.accessToken(), session.expiresInSeconds());
    }

    private void setRefreshCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(props.secureCookies())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}

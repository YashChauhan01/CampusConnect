package edu.campusconnect.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.campusconnect.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class AuthIntegrationTest extends IntegrationTest {

    private static final String EMAIL = "asha@college.edu";

    private Map<String, String> registration(String email) {
        return Map.of("fullName", "Asha Rao", "email", email, "password", PASSWORD);
    }

    private Cookie loginCookie(String email) throws Exception {
        MvcResult result = mvc.perform(postJson("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie("cc_refresh");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    @Test
    void registerVerifyLoginAndReadProfile() throws Exception {
        mvc.perform(postJson("/api/v1/auth/register", registration(EMAIL))).andExpect(status().isAccepted());
        assertThat(mailer.await(EMAIL, "Verify").body()).contains("/verify?token=").contains("30 minutes");

        // Not allowed to log in before verification, and told why.
        mvc.perform(postJson("/api/v1/auth/login", Map.of("email", EMAIL, "password", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        String token = mailer.await(EMAIL, "Verify").token();
        mvc.perform(postJson("/api/v1/auth/verify", Map.of("token", token))).andExpect(status().isOk());

        MvcResult login = mvc.perform(postJson("/api/v1/auth/login", Map.of("email", EMAIL, "password", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie refresh = login.getResponse().getCookie("cc_refresh");
        assertThat(refresh).isNotNull();
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(login.getResponse().getHeader("Set-Cookie")).contains("SameSite=Strict").contains("Path=/api/v1/auth");

        String access = json.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/v1/students/me").header("Authorization", bearer(access)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.fullName").value("Asha Rao"));
    }

    @Test
    void verificationLinkWorksOnlyOnce() throws Exception {
        mvc.perform(postJson("/api/v1/auth/register", registration(EMAIL))).andExpect(status().isAccepted());
        String token = mailer.await(EMAIL, "Verify").token();

        mvc.perform(postJson("/api/v1/auth/verify", Map.of("token", token))).andExpect(status().isOk());
        mvc.perform(postJson("/api/v1/auth/verify", Map.of("token", token))).andExpect(status().isBadRequest());
    }

    @Test
    void expiredVerificationLinkIsRejected() throws Exception {
        mvc.perform(postJson("/api/v1/auth/register", registration(EMAIL))).andExpect(status().isAccepted());
        String token = mailer.await(EMAIL, "Verify").token();
        jdbc.update("UPDATE email_tokens SET expires_at = now() - interval '1 minute'");

        mvc.perform(postJson("/api/v1/auth/verify", Map.of("token", token))).andExpect(status().isBadRequest());
    }

    @Test
    void resendingInvalidatesTheEarlierLink() throws Exception {
        mvc.perform(postJson("/api/v1/auth/register", registration(EMAIL))).andExpect(status().isAccepted());
        String first = mailer.await(EMAIL, "Verify").token();
        mailer.clear();

        mvc.perform(postJson("/api/v1/auth/resend-verification", Map.of("email", EMAIL))).andExpect(status().isAccepted());
        String second = mailer.await(EMAIL, "Verify").token();

        assertThat(second).isNotEqualTo(first);
        mvc.perform(postJson("/api/v1/auth/verify", Map.of("token", first))).andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/v1/auth/verify", Map.of("token", second))).andExpect(status().isOk());
    }

    @Test
    void registrationRejectsNonCollegeDomains() throws Exception {
        mvc.perform(postJson("/api/v1/auth/register", registration("someone@gmail.com")))
                .andExpect(status().isBadRequest());
        assertThat(mailer.all()).isEmpty();
    }

    @Test
    void registrationValidatesInput() throws Exception {
        mvc.perform(postJson("/api/v1/auth/register",
                        Map.of("fullName", "A", "email", EMAIL, "password", "short")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/v1/auth/register",
                        Map.of("fullName", "A", "email", "not-an-email", "password", PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void registeringAKnownAddressDoesNotRevealThatItExists() throws Exception {
        signUp(EMAIL, "Asha Rao");
        mailer.clear();

        mvc.perform(postJson("/api/v1/auth/register", registration(EMAIL)))
                .andExpect(status().isAccepted());

        // The owner is told by email; the HTTP response is the same as for a new address.
        assertThat(mailer.await(EMAIL, "Your CampusConnect account").body()).contains("already exists");
        Integer accounts = jdbc.queryForObject("SELECT count(*) FROM students", Integer.class);
        assertThat(accounts).isEqualTo(1);
    }

    @Test
    void emailMatchingIsCaseInsensitive() throws Exception {
        mvc.perform(postJson("/api/v1/auth/register", registration("Asha@College.EDU"))).andExpect(status().isAccepted());
        String token = mailer.await("asha@college.edu", "Verify").token();
        mvc.perform(postJson("/api/v1/auth/verify", Map.of("token", token))).andExpect(status().isOk());
        mvc.perform(postJson("/api/v1/auth/login", Map.of("email", "ASHA@college.edu", "password", PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void loginFailuresAreIndistinguishable() throws Exception {
        signUp(EMAIL, "Asha Rao");

        String wrongPassword = mvc.perform(postJson("/api/v1/auth/login", Map.of("email", EMAIL, "password", "wrong-password-1")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownUser = mvc.perform(postJson("/api/v1/auth/login",
                        Map.of("email", "nobody@college.edu", "password", "wrong-password-1")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownUser);
    }

    @Test
    void protectedEndpointsReturn401WithoutToken() throws Exception {
        mvc.perform(get("/api/v1/students/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/presence/zones").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTheTokenAndDetectsReplay() throws Exception {
        signUp(EMAIL, "Asha Rao");
        Cookie original = loginCookie(EMAIL);

        MvcResult rotated = mvc.perform(post("/api/v1/auth/refresh").cookie(original))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty()).andReturn();
        Cookie next = rotated.getResponse().getCookie("cc_refresh");
        assertThat(next).isNotNull();
        assertThat(next.getValue()).isNotEqualTo(original.getValue());

        // Replaying the old token within the grace window is refused but does not log the user out...
        mvc.perform(post("/api/v1/auth/refresh").cookie(original)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(next)).andExpect(status().isOk());

        // ...while a late replay is treated as theft and revokes every token in the family.
        Cookie latest = loginCookie(EMAIL);
        MvcResult step = mvc.perform(post("/api/v1/auth/refresh").cookie(latest)).andExpect(status().isOk()).andReturn();
        Cookie stepCookie = step.getResponse().getCookie("cc_refresh");
        jdbc.update("UPDATE refresh_tokens SET revoked_at = now() - interval '1 minute' WHERE revoked_at IS NOT NULL");

        mvc.perform(post("/api/v1/auth/refresh").cookie(latest)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(stepCookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheSession() throws Exception {
        signUp(EMAIL, "Asha Rao");
        Cookie cookie = loginCookie(EMAIL);

        MvcResult result = mvc.perform(post("/api/v1/auth/logout").cookie(cookie)).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetFlowChangesPasswordAndEndsSessions() throws Exception {
        signUp(EMAIL, "Asha Rao");
        Cookie session = loginCookie(EMAIL);
        mailer.clear();

        mvc.perform(postJson("/api/v1/auth/forgot-password", Map.of("email", EMAIL))).andExpect(status().isAccepted());
        String token = mailer.await(EMAIL, "Reset").token();

        mvc.perform(postJson("/api/v1/auth/reset-password", Map.of("token", token, "password", "a-brand-new-password")))
                .andExpect(status().isOk());

        mvc.perform(postJson("/api/v1/auth/login", Map.of("email", EMAIL, "password", PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/v1/auth/login", Map.of("email", EMAIL, "password", "a-brand-new-password")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").cookie(session)).andExpect(status().isUnauthorized());
        // Single use.
        mvc.perform(postJson("/api/v1/auth/reset-password", Map.of("token", token, "password", "yet-another-password")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPasswordForUnknownAddressSendsNothingButLooksTheSame() throws Exception {
        mvc.perform(postJson("/api/v1/auth/forgot-password", Map.of("email", "ghost@college.edu")))
                .andExpect(status().isAccepted());
        assertThat(mailer.all()).isEmpty();
    }

    @Test
    void resetRejectsWeakPasswords() throws Exception {
        signUp(EMAIL, "Asha Rao");
        mailer.clear();
        mvc.perform(postJson("/api/v1/auth/forgot-password", Map.of("email", EMAIL))).andExpect(status().isAccepted());
        String token = mailer.await(EMAIL, "Reset").token();

        mvc.perform(postJson("/api/v1/auth/reset-password", Map.of("token", token, "password", "short")))
                .andExpect(status().isBadRequest());
    }
}

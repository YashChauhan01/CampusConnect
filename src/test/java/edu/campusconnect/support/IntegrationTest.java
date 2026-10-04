package edu.campusconnect.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.boot.test.context.TestConfiguration;

/**
 * Boots the full application against a real PostgreSQL server (embedded, no Docker needed) so Flyway migrations,
 * JPA mappings and SQL are exercised exactly as in production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.TestBeans.class)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "correct-horse-battery";

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @TestConfiguration
    static class TestBeans {
        @Bean
        RecordingMailer recordingMailer() {
            return new RecordingMailer();
        }
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            EmbeddedPostgres pg = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    pg.close();
                } catch (IOException ignored) {
                    // best effort on JVM exit
                }
            }));
            return pg;
        } catch (IOException e) {
            throw new IllegalStateException("Could not start embedded PostgreSQL", e);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected RecordingMailer mailer;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("TRUNCATE TABLE students, skills, subjects, match_rounds RESTART IDENTITY CASCADE");
        mailer.clear();
    }

    protected MockHttpServletRequestBuilder postJson(String path, Object body) throws Exception {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    /** Registers, verifies through the emailed link and logs in; returns the access token. */
    protected String signUp(String email, String name) throws Exception {
        mvc.perform(postJson("/api/v1/auth/register", java.util.Map.of("fullName", name, "email", email, "password", PASSWORD)))
                .andExpect(status().isAccepted());
        String token = mailer.await(email, "Verify").token();
        mvc.perform(postJson("/api/v1/auth/verify", java.util.Map.of("token", token))).andExpect(status().isOk());
        return login(email);
    }

    protected String login(String email) throws Exception {
        String body = mvc.perform(postJson("/api/v1/auth/login", java.util.Map.of("email", email, "password", PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return node.get("accessToken").asText();
    }

    protected long zoneId(String token, String name) throws Exception {
        String body = mvc.perform(get("/api/v1/presence/zones").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode zone : json.readTree(body)) {
            if (zone.get("name").asText().equals(name)) {
                return zone.get("id").asLong();
            }
        }
        throw new AssertionError("zone not found: " + name);
    }

    /** Adds (or updates) a profile subject and returns its id. */
    protected long addSubject(String token, String name, String level) throws Exception {
        String body = mvc.perform(postJson("/api/v1/students/me/subjects", java.util.Map.of("name", name, "proficiency", level))
                .header("Authorization", bearer(token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode s : json.readTree(body).get("subjects")) {
            if (s.get("name").asText().equals(name)) {
                return s.get("id").asLong();
            }
        }
        throw new AssertionError("subject not found: " + name);
    }

    protected void checkIn(String token, long zone, String presenceStatus, java.util.Map<String, Object> extra) throws Exception {
        var body = new java.util.HashMap<String, Object>(java.util.Map.of("zoneId", zone, "status", presenceStatus));
        body.putAll(extra);
        mvc.perform(postJson("/api/v1/presence/check-in", body).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    protected static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}

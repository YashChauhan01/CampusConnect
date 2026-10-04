package edu.campusconnect.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import edu.campusconnect.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PresenceIntegrationTest extends IntegrationTest {

    @Autowired
    private PresenceService presenceService;

    private String asha;
    private String ravi;
    private long libraryId;
    private long labId;

    @BeforeEach
    void setUp() throws Exception {
        asha = signUp("asha@college.edu", "Asha Rao");
        ravi = signUp("ravi@college.edu", "Ravi Patel");
        libraryId = zoneId("Library");
        labId = zoneId("Computer Lab");
    }

    private long zoneId(String name) throws Exception {
        String body = mvc.perform(get("/api/v1/presence/zones").header("Authorization", bearer(asha)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode zone : json.readTree(body)) {
            if (zone.get("name").asText().equals(name)) {
                return zone.get("id").asLong();
            }
        }
        throw new AssertionError("zone not found: " + name);
    }

    private long addSubject(String token, String name, String level) throws Exception {
        String body = mvc.perform(postJson("/api/v1/students/me/subjects", Map.of("name", name, "proficiency", level))
                .header("Authorization", bearer(token))).andReturn().getResponse().getContentAsString();
        for (JsonNode s : json.readTree(body).get("subjects")) {
            if (s.get("name").asText().equals(name)) {
                return s.get("id").asLong();
            }
        }
        throw new AssertionError();
    }

    private void checkIn(String token, long zone, String status, Map<String, Object> extra) throws Exception {
        var body = new java.util.HashMap<String, Object>(Map.of("zoneId", zone, "status", status));
        body.putAll(extra);
        mvc.perform(postJson("/api/v1/presence/check-in", body).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void listsSeededZones() throws Exception {
        mvc.perform(get("/api/v1/presence/zones").header("Authorization", bearer(asha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void checkInIsVisibleToOthersButNotToSelf() throws Exception {
        long dsa = addSubject(asha, "DSA", "ADVANCED");
        checkIn(asha, libraryId, "AVAILABLE", Map.of("requirements", "Need help with graphs", "seekingSubjectIds", List.of(dsa)));

        mvc.perform(get("/api/v1/presence/available").header("Authorization", bearer(ravi)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Asha Rao"))
                .andExpect(jsonPath("$[0].zone").value("Library"))
                .andExpect(jsonPath("$[0].requirements").value("Need help with graphs"))
                .andExpect(jsonPath("$[0].seeking[0]").value("DSA"))
                .andExpect(jsonPath("$[0].subjects[0].name").value("DSA"));
        mvc.perform(get("/api/v1/presence/available").header("Authorization", bearer(asha)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void busyStudentsAreNotListedAndZonesFilter() throws Exception {
        checkIn(asha, libraryId, "BUSY", Map.of());
        checkIn(ravi, labId, "AVAILABLE", Map.of());
        String third = signUp("meera@college.edu", "Meera Shah");

        mvc.perform(get("/api/v1/presence/available").header("Authorization", bearer(third)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Ravi Patel"));
        mvc.perform(get("/api/v1/presence/available").param("zoneId", String.valueOf(libraryId))
                        .header("Authorization", bearer(third)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void renewingMovesTheStudentInsteadOfDuplicating() throws Exception {
        checkIn(asha, libraryId, "AVAILABLE", Map.of());
        checkIn(asha, labId, "AVAILABLE", Map.of());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM presence", Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/v1/presence/me").header("Authorization", bearer(asha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zone").value("Computer Lab"));
    }

    @Test
    void cannotSeekSubjectsOutsideOwnProfile() throws Exception {
        long foreign = addSubject(ravi, "Compilers", "ADVANCED");
        mvc.perform(postJson("/api/v1/presence/check-in",
                        Map.of("zoneId", libraryId, "status", "AVAILABLE", "seekingSubjectIds", List.of(foreign)))
                        .header("Authorization", bearer(asha)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownZone() throws Exception {
        mvc.perform(postJson("/api/v1/presence/check-in", Map.of("zoneId", 9999, "status", "AVAILABLE"))
                .header("Authorization", bearer(asha))).andExpect(status().isBadRequest());
    }

    @Test
    void checkOutRemovesPresence() throws Exception {
        checkIn(asha, libraryId, "AVAILABLE", Map.of());
        mvc.perform(post("/api/v1/presence/check-out").header("Authorization", bearer(asha)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/presence/me").header("Authorization", bearer(asha)))
                .andExpect(status().isNoContent());
        // Idempotent.
        mvc.perform(post("/api/v1/presence/check-out").header("Authorization", bearer(asha)))
                .andExpect(status().isNoContent());
    }

    @Test
    void expiredPresenceIsHiddenImmediatelyAndPurgedByTheJob() throws Exception {
        checkIn(asha, libraryId, "AVAILABLE", Map.of());
        jdbc.update("UPDATE presence SET expires_at = now() - interval '1 second'");

        mvc.perform(get("/api/v1/presence/available").header("Authorization", bearer(ravi)))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/presence/me").header("Authorization", bearer(asha)))
                .andExpect(status().isNoContent());

        presenceService.expireStale();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM presence", Integer.class)).isZero();
    }
}

package edu.campusconnect.presence;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.campusconnect.support.IntegrationTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ZoneAdminIntegrationTest extends IntegrationTest {

    private String admin;
    private String student;

    @BeforeEach
    void setUp() throws Exception {
        // boss@college.edu is listed in app.admin-emails of the test profile.
        admin = signUp("boss@college.edu", "Big Boss");
        student = signUp("asha@college.edu", "Asha Rao");
    }

    private Map<String, Object> zone(String name, Double x, Double y) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("x", x);
        body.put("y", y);
        return body;
    }

    private void update(long id, Map<String, Object> body, int expected) throws Exception {
        mvc.perform(put("/api/v1/admin/zones/" + id).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(expected));
    }

    @Test
    void onlyAdministratorsCanManageZones() throws Exception {
        mvc.perform(get("/api/v1/admin/zones").header("Authorization", bearer(student))).andExpect(status().isForbidden());
        mvc.perform(postJson("/api/v1/admin/zones", zone("Gym", 1.0, 2.0)).header("Authorization", bearer(student)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/zones")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/students/me").header("Authorization", bearer(admin))).andExpect(jsonPath("$.admin").value(true));
        mvc.perform(get("/api/v1/students/me").header("Authorization", bearer(student))).andExpect(jsonPath("$.admin").value(false));
    }

    @Test
    void adminCreatesAZoneThatStudentsCanThenUse() throws Exception {
        mvc.perform(postJson("/api/v1/admin/zones", zone("  Sports   Complex ", 120.5, -40.0)).header("Authorization", bearer(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sports Complex"))
                .andExpect(jsonPath("$.enabled").value(true));

        mvc.perform(get("/api/v1/presence/zones").header("Authorization", bearer(student)))
                .andExpect(jsonPath("$.length()").value(5));
        checkIn(student, zoneId(student, "Sports Complex"), "AVAILABLE", Map.of());
    }

    @Test
    void validatesNamesCoordinatesAndUniqueness() throws Exception {
        mvc.perform(postJson("/api/v1/admin/zones", zone("library", 1.0, 1.0)).header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
        mvc.perform(postJson("/api/v1/admin/zones", zone("X", 1.0, 1.0)).header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/v1/admin/zones", zone("Half placed", 1.0, null)).header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/v1/admin/zones", zone("Far away", 1e12, 0.0)).header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/v1/admin/zones", zone("Unplaced", null, null)).header("Authorization", bearer(admin)))
                .andExpect(status().isCreated());
    }

    @Test
    void disablingAZoneHidesItFromStudentsButKeepsIt() throws Exception {
        long id = zoneId(student, "Cafeteria");
        Map<String, Object> body = zone("Cafeteria", 200.0, -60.0);
        body.put("enabled", false);
        update(id, body, 200);

        mvc.perform(get("/api/v1/presence/zones").header("Authorization", bearer(student)))
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(postJson("/api/v1/presence/check-in", Map.of("zoneId", id, "status", "AVAILABLE"))
                .header("Authorization", bearer(student))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/zones").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void updateMovesAZoneAndReportsLiveCheckIns() throws Exception {
        long id = zoneId(student, "Library");
        checkIn(student, id, "AVAILABLE", Map.of());

        update(id, zone("Central Library", 5.0, 6.0), 200);
        mvc.perform(get("/api/v1/admin/zones").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$[?(@.name=='Central Library')].x").value(5.0))
                .andExpect(jsonPath("$[?(@.name=='Central Library')].checkedIn").value(1));

        update(9999, zone("Nope", 1.0, 1.0), 404);
        // Renaming onto another zone's name is refused.
        update(id, zone("cafeteria", 1.0, 1.0), 409);
    }
}

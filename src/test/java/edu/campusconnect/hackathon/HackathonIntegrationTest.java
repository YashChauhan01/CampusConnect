package edu.campusconnect.hackathon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import edu.campusconnect.support.IntegrationTest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class HackathonIntegrationTest extends IntegrationTest {

    private String organizer;
    private final List<String> students = new ArrayList<>();
    private String hackathonId;
    private List<Long> roleIds;

    @BeforeEach
    void setUp() throws Exception {
        organizer = signUp("org@college.edu", "Olive Organizer");
        students.clear();
        String[][] people = {
                {"java", "ADVANCED"}, {"spring", "INTERMEDIATE"}, {"react", "ADVANCED"},
                {"css", "INTERMEDIATE"}, {"python", "ADVANCED"}, {"ml", "BEGINNER"}};
        for (int i = 0; i < people.length; i++) {
            String token = signUp("s" + i + "@college.edu", "Student " + i);
            addSkill(token, people[i][0], people[i][1]);
            students.add(token);
        }
        createHackathon();
    }

    private void addSkill(String token, String name, String level) throws Exception {
        mvc.perform(postJson("/api/v1/students/me/skills", Map.of("name", name, "proficiency", level))
                .header("Authorization", bearer(token))).andExpect(status().isOk());
    }

    private void createHackathon() throws Exception {
        Map<String, Object> body = Map.of("name", "Campus Hack 2026", "description", "24h build",
                "roles", List.of(
                        Map.of("name", "Backend", "keywords", List.of("java", "spring")),
                        Map.of("name", "Frontend", "keywords", List.of("react", "css")),
                        Map.of("name", "Data", "keywords", List.of("python", "ml"))));
        String response = mvc.perform(postJson("/api/v1/hackathons", body).header("Authorization", bearer(organizer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.status").value("OPEN"))
                .andExpect(jsonPath("$.summary.roles.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(response);
        hackathonId = node.get("summary").get("id").asText();
        roleIds = new ArrayList<>();
        node.get("summary").get("roles").forEach(r -> roleIds.add(r.get("id").asLong()));
    }

    private String url(String suffix) {
        return "/api/v1/hackathons/" + hackathonId + suffix;
    }

    private void register(String token, List<Long> prefs, int expected) throws Exception {
        mvc.perform(put(url("/registration")).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("rolePreferences", prefs))))
                .andExpect(status().is(expected));
    }

    private void registerAll() throws Exception {
        // Everyone ranks the role that matches their skills first.
        long[] firstChoice = {0, 0, 1, 1, 2, 2};
        for (int i = 0; i < students.size(); i++) {
            register(students.get(i), List.of(roleIds.get((int) firstChoice[i])), 200);
        }
    }

    private JsonNode getJson(String token, String path, int expected) throws Exception {
        String body = mvc.perform(get(path).header("Authorization", bearer(token))).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : json.readTree(body);
    }

    private JsonNode postJsonOk(String token, String path, int expected) throws Exception {
        String body = mvc.perform(post(path).header("Authorization", bearer(token))).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : json.readTree(body);
    }

    @Test
    void createRequiresAtLeastTwoDistinctRoles() throws Exception {
        mvc.perform(postJson("/api/v1/hackathons", Map.of("name", "Solo", "roles", List.of(Map.of("name", "Dev"))))
                .header("Authorization", bearer(organizer))).andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/v1/hackathons", Map.of("name", "Dupes",
                        "roles", List.of(Map.of("name", "Dev"), Map.of("name", "dev"))))
                .header("Authorization", bearer(organizer))).andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/v1/hackathons", Map.of("name", "No roles"))
                .header("Authorization", bearer(organizer))).andExpect(status().isBadRequest());
    }

    @Test
    void listsHackathonsWithRegistrationState() throws Exception {
        register(students.get(0), List.of(), 200);

        JsonNode asParticipant = getJson(students.get(0), "/api/v1/hackathons", 200);
        assertThat(asParticipant).hasSize(1);
        assertThat(asParticipant.get(0).get("registered").asBoolean()).isTrue();
        assertThat(asParticipant.get(0).get("participants").asInt()).isEqualTo(1);
        assertThat(asParticipant.get(0).get("organizedByMe").asBoolean()).isFalse();

        JsonNode asOrganizer = getJson(organizer, "/api/v1/hackathons", 200);
        assertThat(asOrganizer.get(0).get("organizedByMe").asBoolean()).isTrue();
        assertThat(asOrganizer.get(0).get("registered").asBoolean()).isFalse();
    }

    @Test
    void registrationNeedsSkillsAndValidPreferences() throws Exception {
        String noSkills = signUp("bare@college.edu", "No Skills");
        register(noSkills, List.of(), 400);

        register(students.get(0), List.of(999_999L), 400);
        register(students.get(0), List.of(roleIds.get(0), roleIds.get(1), roleIds.get(2), roleIds.get(0)), 400);
        register(students.get(0), List.of(roleIds.get(2), roleIds.get(0)), 200);

        // Re-registering updates the preferences rather than duplicating the participant.
        register(students.get(0), List.of(roleIds.get(1)), 200);
        JsonNode detail = getJson(students.get(0), url(""), 200);
        assertThat(detail.get("summary").get("participants").asInt()).isEqualTo(1);
        assertThat(detail.get("myPreferences")).hasSize(1);
        assertThat(detail.get("myPreferences").get(0).asLong()).isEqualTo(roleIds.get(1));
    }

    @Test
    void onlyTheOrganizerSeesTheRoster() throws Exception {
        register(students.get(0), List.of(roleIds.get(0)), 200);
        assertThat(getJson(organizer, url(""), 200).get("participants")).hasSize(1);
        assertThat(getJson(students.get(1), url(""), 200).get("participants")).isEmpty();
    }

    @Test
    void synthesisFormsBalancedTeamsAndPublishingRevealsThem() throws Exception {
        registerAll();

        postJsonOk(students.get(0), url("/synthesize"), 403);
        postJsonOk(organizer, url("/close"), 200);
        register(students.get(0), List.of(), 409);

        JsonNode draft = postJsonOk(organizer, url("/synthesize"), 200);
        assertThat(draft.get("published").asBoolean()).isFalse();
        assertThat(draft.get("teams")).hasSize(2);
        Set<String> placed = new HashSet<>();
        for (JsonNode team : draft.get("teams")) {
            assertThat(team.get("members")).hasSize(3);
            Set<String> rolesInTeam = new HashSet<>();
            team.get("members").forEach(m -> {
                rolesInTeam.add(m.get("role").asText());
                assertThat(placed.add(m.get("studentId").asText())).as("placed once").isTrue();
            });
            assertThat(rolesInTeam).containsExactlyInAnyOrder("Backend", "Frontend", "Data");
        }
        assertThat(placed).hasSize(6);
        assertThat(draft.get("quality").get("preferenceSatisfaction").asInt()).isEqualTo(100);
        assertThat(draft.get("quality").get("averageFit").asInt()).isGreaterThan(60);

        // Drafts are private to the organiser.
        getJson(students.get(0), url("/teams"), 403);
        assertThat(getJson(organizer, url("/teams"), 200).get("teams")).hasSize(2);

        // Synthesis can be re-run without duplicating teams.
        postJsonOk(organizer, url("/synthesize"), 200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM hackathon_teams", Integer.class)).isEqualTo(2);

        JsonNode published = postJsonOk(organizer, url("/publish"), 200);
        assertThat(published.get("published").asBoolean()).isTrue();

        for (String token : students) {
            JsonNode teams = getJson(token, url("/teams"), 200);
            long mine = 0;
            for (JsonNode team : teams.get("teams")) {
                if (team.get("yours").asBoolean()) {
                    mine++;
                    assertThat(team.get("members")).anyMatch(m -> m.get("you").asBoolean());
                }
            }
            assertThat(mine).isEqualTo(1);
        }

        // Final: no more changes.
        postJsonOk(organizer, url("/synthesize"), 409);
        postJsonOk(organizer, url("/publish"), 409);
        mvc.perform(delete(url("/registration")).header("Authorization", bearer(students.get(0))))
                .andExpect(status().isConflict());
    }

    @Test
    void synthesisNeedsAtLeastTwoParticipants() throws Exception {
        register(students.get(0), List.of(), 200);
        postJsonOk(organizer, url("/synthesize"), 400);
    }

    @Test
    void publishRequiresFreshTeams() throws Exception {
        postJsonOk(organizer, url("/publish"), 409);

        registerAll();
        postJsonOk(organizer, url("/synthesize"), 200);
        // A late withdrawal makes the draft stale.
        mvc.perform(delete(url("/registration")).header("Authorization", bearer(students.get(5))))
                .andExpect(status().isOk());
        postJsonOk(organizer, url("/publish"), 409);

        postJsonOk(organizer, url("/synthesize"), 200);
        postJsonOk(organizer, url("/publish"), 200);
    }

    @Test
    void unknownHackathonIs404() throws Exception {
        mvc.perform(get("/api/v1/hackathons/" + java.util.UUID.randomUUID()).header("Authorization", bearer(organizer)))
                .andExpect(status().isNotFound());
    }
}

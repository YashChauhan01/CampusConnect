package edu.campusconnect.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import edu.campusconnect.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssessmentIntegrationTest extends IntegrationTest {

    private String asha;
    private long subjectId;

    @BeforeEach
    void setUp() throws Exception {
        asha = signUp("asha@college.edu", "Asha Rao");
        subjectId = addSubject(asha, "DSA", "ADVANCED");
    }

    private JsonNode start(String token, long itemId, int expected) throws Exception {
        String body = mvc.perform(postJson("/api/v1/assessments", Map.of("kind", "SUBJECT", "itemId", itemId))
                        .header("Authorization", bearer(token))).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode submit(String token, String id, List<String> answers, int expected) throws Exception {
        String body = mvc.perform(postJson("/api/v1/assessments/" + id + "/submit", Map.of("answers", answers))
                        .header("Authorization", bearer(token))).andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode subjectOnProfile(String token) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/students/me").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString()).get("subjects").get(0);
    }

    @Test
    void statusReportsWhetherTheFeatureIsAvailable() throws Exception {
        mvc.perform(get("/api/v1/assessments/status").header("Authorization", bearer(asha)))
                .andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.questionCount").value(4));
        assessor.enabled = false;
        mvc.perform(get("/api/v1/assessments/status").header("Authorization", bearer(asha)))
                .andExpect(jsonPath("$.enabled").value(false));
        start(asha, subjectId, 503);
    }

    @Test
    void passingConfirmsTheClaimedLevel() throws Exception {
        JsonNode started = start(asha, subjectId, 200);
        assertThat(started.get("questions")).hasSize(4);
        assertThat(started.get("topic").asText()).isEqualTo("DSA");
        assertThat(started.get("status").asText()).isEqualTo("PENDING");

        JsonNode graded = submit(asha, started.get("id").asText(), List.of("a", "b", "c", "d"), 200);

        assertThat(graded.get("score").asInt()).isEqualTo(90);
        assertThat(graded.get("verifiedLevel").asText()).isEqualTo("ADVANCED");
        assertThat(subjectOnProfile(asha).get("verifiedLevel").asText()).isEqualTo("ADVANCED");
        assertThat(assessor.gradedAnswers.get(0)).containsExactly("a", "b", "c", "d");
    }

    @Test
    void middlingScoreLowersByOneLevelAndLowScoreToBeginner() throws Exception {
        assessor.score = 60;
        JsonNode first = start(asha, subjectId, 200);
        JsonNode graded = submit(asha, first.get("id").asText(), List.of("a", "b", "c", "d"), 200);
        assertThat(graded.get("verifiedLevel").asText()).isEqualTo("INTERMEDIATE");

        // Retake is blocked during the cool-down...
        start(asha, subjectId, 409);
        jdbc.update("UPDATE skill_assessments SET graded_at = now() - interval '2 hours'");
        // ...and afterwards a poor result drops the verified level to beginner.
        assessor.score = 10;
        JsonNode second = start(asha, subjectId, 200);
        JsonNode regraded = submit(asha, second.get("id").asText(), List.of("", "", "", ""), 200);
        assertThat(regraded.get("verifiedLevel").asText()).isEqualTo("BEGINNER");
    }

    @Test
    void verifiedLevelCapsTheLevelUsedForMatching() throws Exception {
        assessor.score = 60;
        JsonNode started = start(asha, subjectId, 200);
        submit(asha, started.get("id").asText(), List.of("a", "b", "c", "d"), 200);

        // Asha claims ADVANCED but is only verified INTERMEDIATE: other students see the capped level.
        String ravi = signUp("ravi@college.edu", "Ravi Patel");
        checkIn(asha, zoneId(asha, "Library"), "AVAILABLE", Map.of());
        mvc.perform(get("/api/v1/presence/available").header("Authorization", bearer(ravi)))
                .andExpect(jsonPath("$[0].subjects[0].proficiency").value("INTERMEDIATE"))
                .andExpect(jsonPath("$[0].subjects[0].verified").value(true));
    }

    @Test
    void changingTheClaimClearsAnOldVerification() throws Exception {
        JsonNode started = start(asha, subjectId, 200);
        submit(asha, started.get("id").asText(), List.of("a", "b", "c", "d"), 200);
        assertThat(subjectOnProfile(asha).get("verifiedLevel").asText()).isEqualTo("ADVANCED");

        addSubject(asha, "DSA", "INTERMEDIATE");

        assertThat(subjectOnProfile(asha).get("verifiedLevel").isNull()).isTrue();
    }

    @Test
    void startingAgainResumesThePendingCheck() throws Exception {
        JsonNode first = start(asha, subjectId, 200);
        JsonNode again = start(asha, subjectId, 200);
        assertThat(again.get("id").asText()).isEqualTo(first.get("id").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM skill_assessments", Integer.class)).isEqualTo(1);
    }

    @Test
    void answersMustMatchAndOnlyTheOwnerMaySubmitOnce() throws Exception {
        JsonNode started = start(asha, subjectId, 200);
        String id = started.get("id").asText();

        submit(asha, id, List.of("only one"), 400);
        submit(asha, id, List.of("x".repeat(2001), "b", "c", "d"), 400);
        String ravi = signUp("ravi@college.edu", "Ravi Patel");
        submit(ravi, id, List.of("a", "b", "c", "d"), 404);

        submit(asha, id, List.of("a", "b", "c", "d"), 200);
        submit(asha, id, List.of("a", "b", "c", "d"), 409);
    }

    @Test
    void cannotAssessSomethingNotOnYourProfile() throws Exception {
        String ravi = signUp("ravi@college.edu", "Ravi Patel");
        start(ravi, subjectId, 404);
    }

    @Test
    void skillsCanBeAssessedToo() throws Exception {
        String body = mvc.perform(postJson("/api/v1/students/me/skills", Map.of("name", "Java", "proficiency", "INTERMEDIATE"))
                .header("Authorization", bearer(asha))).andReturn().getResponse().getContentAsString();
        long skillId = json.readTree(body).get("skills").get(0).get("id").asLong();

        String started = mvc.perform(postJson("/api/v1/assessments", Map.of("kind", "SKILL", "itemId", skillId))
                .header("Authorization", bearer(asha))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        submit(asha, json.readTree(started).get("id").asText(), List.of("a", "b", "c", "d"), 200);

        mvc.perform(get("/api/v1/students/me").header("Authorization", bearer(asha)))
                .andExpect(jsonPath("$.skills[0].verifiedLevel").value("INTERMEDIATE"));
    }
}

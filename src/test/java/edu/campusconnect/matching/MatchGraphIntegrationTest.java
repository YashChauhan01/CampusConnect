package edu.campusconnect.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import edu.campusconnect.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatchGraphIntegrationTest extends IntegrationTest {

    private String admin;
    private String student;

    @BeforeEach
    void setUp() throws Exception {
        admin = signUp("boss@college.edu", "Big Boss");
        student = signUp("asha@college.edu", "Asha Rao");
        String ravi = signUp("ravi@college.edu", "Ravi Patel");
        String meera = signUp("meera@college.edu", "Meera Shah");
        long library = zoneId(admin, "Library");
        addSubject(student, "DSA", "ADVANCED");
        addSubject(ravi, "DSA", "INTERMEDIATE");
        addSubject(meera, "DSA", "BEGINNER");
        for (String token : new String[]{student, ravi, meera}) {
            checkIn(token, library, "AVAILABLE", Map.of());
        }
    }

    @Test
    void onlyAdministratorsMayViewTheGraph() throws Exception {
        mvc.perform(get("/api/v1/matching/graph").header("Authorization", bearer(student))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/matching/rounds").header("Authorization", bearer(student))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/matching/graph")).andExpect(status().isUnauthorized());
    }

    @Test
    void describesNodesEdgesBothSolutionsAndTheOptimalityCheck() throws Exception {
        String body = mvc.perform(get("/api/v1/matching/graph").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode graph = json.readTree(body);

        assertThat(graph.get("poolSize").asInt()).isEqualTo(3);
        assertThat(graph.get("nodes")).hasSize(3);
        assertThat(graph.get("edgesTotal").asInt()).isEqualTo(3);
        // Three mutually compatible students: exactly one pair is matched and one stays alone.
        assertThat(graph.get("optimal").get("pairs").asInt()).isEqualTo(1);
        long matchedEdges = 0;
        for (JsonNode edge : graph.get("edges")) {
            if (edge.get("matched").asBoolean()) {
                matchedEdges++;
                assertThat(edge.get("shared").get(0).asText()).isEqualTo("DSA");
            }
        }
        assertThat(matchedEdges).isEqualTo(1);
        assertThat(graph.get("check").get("passed").asBoolean()).isTrue();
        assertThat(graph.get("optimal").get("totalWeight").asDouble())
                .isGreaterThanOrEqualTo(graph.get("greedy").get("totalWeight").asDouble());
        int matchedNodes = 0;
        for (JsonNode node : graph.get("nodes")) {
            if (!node.get("partner").isNull()) {
                matchedNodes++;
            }
        }
        assertThat(matchedNodes).isEqualTo(2);
    }

    @Test
    void previewChangesNothingAndRoundsAreListedOnceOneRuns() throws Exception {
        mvc.perform(get("/api/v1/matching/graph").header("Authorization", bearer(admin))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM matches", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_rounds", Integer.class)).isZero();

        mvc.perform(post("/api/v1/matching/find").header("Authorization", bearer(student))).andExpect(status().isOk());
        String rounds = mvc.perform(get("/api/v1/matching/rounds").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(rounds)).hasSize(1);
        assertThat(json.readTree(rounds).get(0).get("poolSize").asInt()).isEqualTo(3);
    }

    @Test
    void topKLimitsEdgesButAlwaysKeepsMatchedOnes() throws Exception {
        String body = mvc.perform(get("/api/v1/matching/graph").param("topK", "1").header("Authorization", bearer(admin)))
                .andReturn().getResponse().getContentAsString();
        JsonNode graph = json.readTree(body);
        assertThat(graph.get("edgesShown").asInt()).isLessThanOrEqualTo(graph.get("edgesTotal").asInt());
        boolean hasMatched = false;
        for (JsonNode edge : graph.get("edges")) {
            hasMatched |= edge.get("matched").asBoolean();
        }
        assertThat(hasMatched).isTrue();
    }
}

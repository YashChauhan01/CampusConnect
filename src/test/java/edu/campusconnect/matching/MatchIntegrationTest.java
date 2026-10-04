package edu.campusconnect.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import edu.campusconnect.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MatchIntegrationTest extends IntegrationTest {

    @Autowired
    private MatchService matching;

    private String asha;
    private String ravi;
    private String meera;
    private long library;
    private long lab;

    @BeforeEach
    void setUp() throws Exception {
        asha = signUp("asha@college.edu", "Asha Rao");
        ravi = signUp("ravi@college.edu", "Ravi Patel");
        meera = signUp("meera@college.edu", "Meera Shah");
        library = zoneId(asha, "Library");
        lab = zoneId(asha, "Computer Lab");
    }

    private JsonNode currentMatch(String token) throws Exception {
        String body = mvc.perform(get("/api/v1/matching/matches/current").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : json.readTree(body);
    }

    private void act(String token, String matchId, String action, int expectedStatus) throws Exception {
        mvc.perform(post("/api/v1/matching/matches/" + matchId + "/" + action).header("Authorization", bearer(token)))
                .andExpect(status().is(expectedStatus));
    }

    /** Asha (advanced) and Ravi (beginner) both know DSA and are in the library. */
    private void ashaAndRaviAvailable() throws Exception {
        addSubject(asha, "DSA", "ADVANCED");
        addSubject(ravi, "DSA", "INTERMEDIATE");
        checkIn(asha, library, "AVAILABLE", Map.of());
        checkIn(ravi, library, "AVAILABLE", Map.of());
    }

    @Test
    void roundPairsCompatibleStudentsAndBothMustAccept() throws Exception {
        ashaAndRaviAvailable();
        addSubject(meera, "Biology", "ADVANCED");
        checkIn(meera, lab, "AVAILABLE", Map.of());

        Optional<MatchService.RoundSummary> summary = matching.runRound();

        assertThat(summary).isPresent();
        assertThat(summary.get().poolSize()).isEqualTo(3);
        assertThat(summary.get().pairs()).isEqualTo(1);

        JsonNode forAsha = currentMatch(asha);
        JsonNode forRavi = currentMatch(ravi);
        assertThat(forAsha).isNotNull();
        assertThat(forAsha.get("status").asText()).isEqualTo("PROPOSED");
        assertThat(forAsha.get("partner").get("name").asText()).isEqualTo("Ravi Patel");
        assertThat(forRavi.get("partner").get("name").asText()).isEqualTo("Asha Rao");
        assertThat(forAsha.get("sharedSubjects").get(0).asText()).isEqualTo("DSA");
        assertThat(forAsha.get("score").asInt()).isBetween(50, 100);
        assertThat(forAsha.get("partner").get("email").isNull()).as("no contact details before both accept").isTrue();
        assertThat(currentMatch(meera)).as("unmatched student gets nothing").isNull();

        String id = forAsha.get("id").asText();
        act(asha, id, "accept", 200);
        mvc.perform(get("/api/v1/matching/matches/current").header("Authorization", bearer(asha)))
                .andExpect(jsonPath("$.status").value("PROPOSED"))
                .andExpect(jsonPath("$.youAccepted").value(true))
                .andExpect(jsonPath("$.partnerAccepted").value(false));

        act(ravi, id, "accept", 200);
        mvc.perform(get("/api/v1/matching/matches/current").header("Authorization", bearer(ravi)))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.partner.email").value("asha@college.edu"));
    }

    @Test
    void studentsInALiveMatchAreNotRematched() throws Exception {
        ashaAndRaviAvailable();
        matching.runRound();
        long before = jdbc.queryForObject("SELECT count(*) FROM matches", Long.class);

        matching.runRound();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM matches", Long.class)).isEqualTo(before);
    }

    @Test
    void declineFreesBothButKeepsThePairApartDuringCooldown() throws Exception {
        ashaAndRaviAvailable();
        matching.runRound();
        String id = currentMatch(asha).get("id").asText();

        act(ravi, id, "decline", 200);
        assertThat(currentMatch(asha)).isNull();
        assertThat(currentMatch(ravi)).isNull();

        matching.runRound();
        assertThat(currentMatch(asha)).as("declined pair is not proposed again").isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM matches", Long.class)).isEqualTo(1);
        // Declined proposals cannot be answered again.
        act(asha, id, "accept", 409);
    }

    @Test
    void unansweredProposalExpiresAndStudentsAreMatchedAgain() throws Exception {
        ashaAndRaviAvailable();
        matching.runRound();
        jdbc.update("UPDATE matches SET expires_at = now() - interval '1 second'");

        // Late acceptance is refused...
        act(asha, currentMatch(asha).get("id").asText(), "accept", 409);
        // ...and the next round expires the stale proposal and pairs them afresh (expiry is not a decline).
        matching.runRound();

        assertThat(jdbc.queryForList("SELECT status FROM matches ORDER BY created_at", String.class))
                .containsExactly("EXPIRED", "PROPOSED");
    }

    @Test
    void checkingOutCancelsTheLiveProposalForBoth() throws Exception {
        ashaAndRaviAvailable();
        matching.runRound();
        assertThat(currentMatch(ravi)).isNotNull();

        mvc.perform(post("/api/v1/presence/check-out").header("Authorization", bearer(asha))).andExpect(status().isNoContent());

        assertThat(currentMatch(ravi)).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM matches", String.class)).isEqualTo("EXPIRED");
    }

    @Test
    void confirmedMatchCanBeCompleted() throws Exception {
        ashaAndRaviAvailable();
        matching.runRound();
        String id = currentMatch(asha).get("id").asText();
        act(asha, id, "accept", 200);
        act(ravi, id, "accept", 200);

        act(asha, id, "complete", 200);

        assertThat(currentMatch(asha)).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM matches", String.class)).isEqualTo("COMPLETED");
    }

    @Test
    void outsidersCannotActOnAMatch() throws Exception {
        ashaAndRaviAvailable();
        matching.runRound();
        String id = currentMatch(asha).get("id").asText();

        act(meera, id, "accept", 404);
        act(meera, id, "decline", 404);
    }

    @Test
    void completingAnUnconfirmedMatchIsRejected() throws Exception {
        ashaAndRaviAvailable();
        matching.runRound();
        act(asha, currentMatch(asha).get("id").asText(), "complete", 409);
    }

    @Test
    void roundRecordsEvaluationMetrics() throws Exception {
        ashaAndRaviAvailable();
        matching.runRound();

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM match_rounds");
        assertThat(row.get("pool_size")).isEqualTo(2);
        assertThat(row.get("pair_count")).isEqualTo(1);
        assertThat(((Number) row.get("total_weight")).doubleValue()).isPositive();
    }

    @Test
    void busyOrDisjointStudentsProduceNoRound() throws Exception {
        addSubject(asha, "DSA", "ADVANCED");
        addSubject(ravi, "DSA", "ADVANCED");
        checkIn(asha, library, "AVAILABLE", Map.of());
        checkIn(ravi, library, "BUSY", Map.of());

        assertThat(matching.runRound()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_rounds", Long.class)).isZero();
    }

    @Test
    void freeTextRequirementSteersWhichSubjectsCount() throws Exception {
        long dsa = addSubject(asha, "DSA", "ADVANCED");
        addSubject(asha, "Networks", "INTERMEDIATE");
        addSubject(ravi, "Networks", "INTERMEDIATE");
        checkIn(asha, library, "AVAILABLE", Map.of("seekingSubjectIds", List.of(dsa)));
        // Ravi asks for Networks in prose; that makes the shared Networks subject count for the pair.
        checkIn(ravi, library, "AVAILABLE", Map.of("requirements", "Preparing for the networks quiz, need a partner"));

        matching.runRound();

        JsonNode match = currentMatch(asha);
        assertThat(match).isNotNull();
        assertThat(match.get("sharedSubjects").get(0).asText()).isEqualTo("Networks");
    }

    @Test
    void suggestionsAreRankedByCompatibility() throws Exception {
        addSubject(asha, "DSA", "ADVANCED");
        addSubject(ravi, "DSA", "INTERMEDIATE");
        addSubject(meera, "DSA", "BEGINNER");
        checkIn(asha, library, "AVAILABLE", Map.of());
        checkIn(ravi, library, "AVAILABLE", Map.of());
        checkIn(meera, lab, "AVAILABLE", Map.of());

        String body = mvc.perform(get("/api/v1/matching/suggestions").header("Authorization", bearer(asha)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode list = json.readTree(body);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("name").asText()).isEqualTo("Ravi Patel");
        assertThat(list.get(0).get("score").asInt()).isGreaterThanOrEqualTo(list.get(1).get("score").asInt());
    }

    @Test
    void findEndpointRunsARoundOnDemand() throws Exception {
        ashaAndRaviAvailable();

        mvc.perform(post("/api/v1/matching/find").header("Authorization", bearer(asha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partner.name").value("Ravi Patel"));
    }

    @Test
    void matchingRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/matching/matches/current")).andExpect(status().isUnauthorized());
    }
}

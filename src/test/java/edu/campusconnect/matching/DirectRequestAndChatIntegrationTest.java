package edu.campusconnect.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import edu.campusconnect.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DirectRequestAndChatIntegrationTest extends IntegrationTest {

    private String asha;
    private String ravi;
    private String meera;
    private String raviId;
    private String meeraId;
    private long library;

    @BeforeEach
    void setUp() throws Exception {
        asha = signUp("asha@college.edu", "Asha Rao");
        ravi = signUp("ravi@college.edu", "Ravi Patel");
        meera = signUp("meera@college.edu", "Meera Shah");
        raviId = me(ravi).get("id").asText();
        meeraId = me(meera).get("id").asText();
        library = zoneId(asha, "Library");
        addSubject(asha, "DSA", "ADVANCED");
        addSubject(ravi, "DSA", "INTERMEDIATE");
        addSubject(meera, "Biology", "ADVANCED");
    }

    private JsonNode me(String token) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/students/me").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode request(String token, String studentId, int expected) throws Exception {
        String body = mvc.perform(postJson("/api/v1/matching/requests", Map.of("studentId", studentId))
                        .header("Authorization", bearer(token)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private void act(String token, String matchId, String action, int expected) throws Exception {
        mvc.perform(post("/api/v1/matching/matches/" + matchId + "/" + action).header("Authorization", bearer(token)))
                .andExpect(status().is(expected));
    }

    private JsonNode unreadOf(String token) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void requestNotifiesTheClassmateAndAcceptingOpensAChatOnlyTheTwoCanUse() throws Exception {
        checkIn(asha, library, "AVAILABLE", Map.of());
        checkIn(ravi, library, "AVAILABLE", Map.of());

        JsonNode sent = request(asha, raviId, 200);
        assertThat(sent.get("status").asText()).isEqualTo("PROPOSED");
        assertThat(sent.get("direct").asBoolean()).isTrue();
        assertThat(sent.get("requestedByYou").asBoolean()).isTrue();
        assertThat(sent.get("youAccepted").asBoolean()).as("sending a request counts as accepting").isTrue();
        String id = sent.get("id").asText();

        JsonNode inbox = unreadOf(ravi);
        assertThat(inbox.get("items").get(0).get("type").asText()).isEqualTo("MATCH_REQUESTED");
        assertThat(inbox.get("items").get(0).get("title").asText()).isEqualTo("Study request from Asha Rao");
        mvc.perform(get("/api/v1/matching/matches/current").header("Authorization", bearer(ravi)))
                .andExpect(jsonPath("$.requestedByYou").value(false))
                .andExpect(jsonPath("$.partnerAccepted").value(true));

        // Chat is closed until both accept.
        mvc.perform(get("/api/v1/matching/matches/" + id + "/messages").header("Authorization", bearer(asha)))
                .andExpect(status().isConflict());

        act(ravi, id, "accept", 200);
        mvc.perform(get("/api/v1/matching/matches/current").header("Authorization", bearer(asha)))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mvc.perform(postJson("/api/v1/matching/matches/" + id + "/messages", Map.of("body", "  Meet at the library entrance?  "))
                        .header("Authorization", bearer(asha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.body").value("Meet at the library entrance?"));
        mvc.perform(postJson("/api/v1/matching/matches/" + id + "/messages", Map.of("body", "Sure, 5 minutes"))
                .header("Authorization", bearer(ravi))).andExpect(status().isOk());
        mvc.perform(postJson("/api/v1/matching/matches/" + id + "/messages", Map.of("body", "See you"))
                .header("Authorization", bearer(asha))).andExpect(status().isOk());

        mvc.perform(get("/api/v1/matching/matches/" + id + "/messages").header("Authorization", bearer(ravi)))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].mine").value(false))
                .andExpect(jsonPath("$[1].mine").value(true))
                .andExpect(jsonPath("$[1].body").value("Sure, 5 minutes"));

        // Only one unread chat alert piles up for Ravi even though Asha sent two messages.
        long chatAlerts = 0;
        for (JsonNode n : unreadOf(ravi).get("items")) {
            if (n.get("type").asText().equals("CHAT_MESSAGE") && !n.get("read").asBoolean()) {
                chatAlerts++;
            }
        }
        assertThat(chatAlerts).isEqualTo(1);

        // Outsiders cannot read or write, and empty messages are rejected.
        mvc.perform(get("/api/v1/matching/matches/" + id + "/messages").header("Authorization", bearer(meera)))
                .andExpect(status().isNotFound());
        mvc.perform(postJson("/api/v1/matching/matches/" + id + "/messages", Map.of("body", "hi"))
                .header("Authorization", bearer(meera))).andExpect(status().isNotFound());
        mvc.perform(postJson("/api/v1/matching/matches/" + id + "/messages", Map.of("body", "   "))
                .header("Authorization", bearer(asha))).andExpect(status().isBadRequest());

        // After the session ends the history stays readable but no new messages can be sent.
        act(asha, id, "complete", 200);
        mvc.perform(get("/api/v1/matching/matches/" + id + "/messages").header("Authorization", bearer(asha)))
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(postJson("/api/v1/matching/matches/" + id + "/messages", Map.of("body", "one more"))
                .header("Authorization", bearer(asha))).andExpect(status().isConflict());
    }

    @Test
    void senderCanWithdrawAPendingRequestWithoutACoolDown() throws Exception {
        checkIn(asha, library, "AVAILABLE", Map.of());
        checkIn(ravi, library, "AVAILABLE", Map.of());
        String id = request(asha, raviId, 200).get("id").asText();

        act(ravi, id, "cancel", 409); // only the sender can withdraw
        act(asha, id, "cancel", 200);
        assertThat(unreadOf(ravi).get("items").get(0).get("type").asText()).isEqualTo("MATCH_CANCELLED");
        mvc.perform(get("/api/v1/matching/matches/current").header("Authorization", bearer(ravi)))
                .andExpect(status().isNoContent());

        // Withdrawing is not declining: Asha can ask again straight away.
        request(asha, raviId, 200);
    }

    @Test
    void requestsAreRefusedWhenTheyCannotWork() throws Exception {
        String ashaId = me(asha).get("id").asText();
        request(asha, raviId, 409); // Asha is not checked in
        checkIn(asha, library, "AVAILABLE", Map.of());
        request(asha, raviId, 409); // Ravi is not on campus
        request(asha, ashaId, 400); // not to yourself

        checkIn(meera, library, "AVAILABLE", Map.of());
        request(asha, meeraId, 400); // no shared subject

        checkIn(ravi, library, "BUSY", Map.of());
        request(asha, raviId, 409); // busy students do not get requests

        checkIn(ravi, library, "AVAILABLE", Map.of());
        String id = request(asha, raviId, 200).get("id").asText();
        request(asha, raviId, 409); // one live request at a time
        act(ravi, id, "decline", 200);
        request(asha, raviId, 409); // declined pair stays apart for the cool-down
    }
}

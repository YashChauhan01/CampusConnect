package edu.campusconnect.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import edu.campusconnect.matching.MatchService;
import edu.campusconnect.support.IntegrationTest;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeIntegrationTest extends IntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MatchService matching;

    private WebSocketStompClient client;
    private String asha;
    private String ravi;

    @BeforeEach
    void setUp() throws Exception {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        asha = signUp("asha@college.edu", "Asha Rao");
        ravi = signUp("ravi@college.edu", "Ravi Patel");
    }

    @AfterEach
    void tearDown() {
        client.stop();
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders headers = new StompHeaders();
        if (token != null) {
            headers.add("Authorization", "Bearer " + token);
        }
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
    }

    private BlockingQueue<JsonNode> subscribe(StompSession session, String destination) {
        BlockingQueue<JsonNode> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((JsonNode) payload);
            }
        });
        return queue;
    }

    private JsonNode next(BlockingQueue<JsonNode> queue) throws InterruptedException {
        JsonNode message = queue.poll(5, TimeUnit.SECONDS);
        assertThat(message).as("expected a pushed message").isNotNull();
        return message;
    }

    private void matchAshaAndRavi() throws Exception {
        addSubject(asha, "DSA", "ADVANCED");
        addSubject(ravi, "DSA", "INTERMEDIATE");
        long library = zoneId(asha, "Library");
        checkIn(asha, library, "AVAILABLE", Map.of());
        checkIn(ravi, library, "AVAILABLE", Map.of());
        matching.runRound();
    }

    @Test
    void studentsReceiveAPushedNotificationWhenMatched() throws Exception {
        StompSession session = connect(asha);
        BlockingQueue<JsonNode> updates = subscribe(session, "/user/queue/updates");
        Thread.sleep(300); // let the subscription register on the broker

        matchAshaAndRavi();

        JsonNode push = next(updates);
        assertThat(push.get("kind").asText()).isEqualTo("NOTIFICATION");
        assertThat(push.get("topic").asText()).isEqualTo("matches");
        assertThat(push.get("notification").get("type").asText()).isEqualTo("MATCH_PROPOSED");
        assertThat(push.get("notification").get("body").asText()).contains("Ravi Patel");
        session.disconnect();
    }

    @Test
    void pushesAreNotLeakedToOtherStudents() throws Exception {
        String meera = signUp("meera@college.edu", "Meera Shah");
        StompSession session = connect(meera);
        BlockingQueue<JsonNode> updates = subscribe(session, "/user/queue/updates");
        Thread.sleep(300);

        matchAshaAndRavi();

        // Meera only sees presence hints she subscribed to (none here) — never Asha's and Ravi's notification.
        assertThat(updates.poll(1500, TimeUnit.MILLISECONDS)).isNull();
        session.disconnect();
    }

    @Test
    void presenceChangesAreBroadcastAsRefreshHints() throws Exception {
        StompSession session = connect(ravi);
        BlockingQueue<JsonNode> presence = subscribe(session, "/topic/presence");
        Thread.sleep(300);

        checkIn(asha, zoneId(asha, "Library"), "AVAILABLE", Map.of());

        JsonNode push = next(presence);
        assertThat(push.get("kind").asText()).isEqualTo("REFRESH");
        assertThat(push.get("topic").asText()).isEqualTo("presence");
        session.disconnect();
    }

    @Test
    void connectionWithoutValidTokenIsRejected() {
        assertThatThrownBy(() -> connect(null)).isNotNull();
        assertThatThrownBy(() -> connect("not-a-token")).isNotNull();
    }

    @Test
    void notificationsCanBeListedAndMarkedRead() throws Exception {
        matchAshaAndRavi();

        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(asha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1))
                .andExpect(jsonPath("$.items[0].type").value("MATCH_PROPOSED"))
                .andExpect(jsonPath("$.items[0].read").value(false));

        String id = json.readTree(mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(asha)))
                .andReturn().getResponse().getContentAsString()).get("items").get(0).get("id").asText();

        // Someone else cannot mark it as read.
        mvc.perform(post("/api/v1/notifications/" + id + "/read").header("Authorization", bearer(ravi)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/notifications/" + id + "/read").header("Authorization", bearer(asha)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(asha)))
                .andExpect(jsonPath("$.unread").value(0))
                .andExpect(jsonPath("$.items[0].read").value(true));

        mvc.perform(post("/api/v1/notifications/read-all").header("Authorization", bearer(ravi)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(ravi)))
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void acceptingNotifiesThePartnerAndConfirmationNotifiesBoth() throws Exception {
        matchAshaAndRavi();
        String body = mvc.perform(get("/api/v1/matching/matches/current").header("Authorization", bearer(asha)))
                .andReturn().getResponse().getContentAsString();
        String matchId = json.readTree(body).get("id").asText();

        mvc.perform(post("/api/v1/matching/matches/" + matchId + "/accept").header("Authorization", bearer(asha)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(ravi)))
                .andExpect(jsonPath("$.items[0].type").value("MATCH_PARTNER_ACCEPTED"));

        mvc.perform(post("/api/v1/matching/matches/" + matchId + "/accept").header("Authorization", bearer(ravi)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(asha)))
                .andExpect(jsonPath("$.items[0].type").value("MATCH_CONFIRMED"));
        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(ravi)))
                .andExpect(jsonPath("$.items[0].type").value("MATCH_CONFIRMED"));
    }
}

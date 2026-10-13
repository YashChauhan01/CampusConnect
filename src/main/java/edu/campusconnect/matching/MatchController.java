package edu.campusconnect.matching;

import edu.campusconnect.security.CurrentStudent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/matching")
public class MatchController {

    public record StudyRequest(@NotNull UUID studentId) {}

    public record MessageBody(@NotBlank
                              @Size(max = ChatService.MAX_LENGTH) String body) {}

    private final CurrentStudent current;
    private final MatchService matching;
    private final MatchGraphService graphs;
    private final ChatService chat;

    public MatchController(CurrentStudent current, MatchService matching, MatchGraphService graphs, ChatService chat) {
        this.current = current;
        this.matching = matching;
        this.graphs = graphs;
        this.chat = chat;
    }

    /** Sends a direct study request to a classmate who is available on campus. */
    @PostMapping("/requests")
    public MatchService.MatchView request(@Valid @RequestBody StudyRequest body) {
        return matching.request(current.require().getId(), body.studentId());
    }

    /** Withdraws a pending request the student sent. */
    @PostMapping("/matches/{id}/cancel")
    public MatchService.MatchView cancel(@PathVariable UUID id) {
        return matching.cancel(current.require().getId(), id);
    }

    @GetMapping("/matches/{id}/messages")
    public List<ChatService.MessageView> messages(@PathVariable UUID id) {
        return chat.list(current.require().getId(), id);
    }

    @PostMapping("/matches/{id}/messages")
    public ChatService.MessageView send(@PathVariable UUID id,
                                        @Valid @RequestBody MessageBody body) {
        return chat.send(current.require().getId(), id, body.body());
    }

    /** The student's live proposal or confirmed match; 204 if there is none. */
    @GetMapping("/matches/current")
    public ResponseEntity<MatchService.MatchView> currentMatch() {
        return matching.current(current.require().getId()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/matches")
    public List<MatchService.MatchView> history() {
        return matching.history(current.require().getId());
    }

    /** Ranked list of the most compatible students currently available. */
    @GetMapping("/suggestions")
    public List<MatchService.Suggestion> suggestions() {
        return matching.suggestions(current.require().getId());
    }

    /** "Find me a partner now": runs a matching round immediately (throttled) and returns the caller's match, if any. */
    @PostMapping("/find")
    public ResponseEntity<MatchService.MatchView> find() {
        return matching.requestRound(current.require().getId()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/matches/{id}/accept")
    public MatchService.MatchView accept(@PathVariable UUID id) {
        return matching.accept(current.require().getId(), id);
    }

    @PostMapping("/matches/{id}/decline")
    public MatchService.MatchView decline(@PathVariable UUID id) {
        return matching.decline(current.require().getId(), id);
    }

    @PostMapping("/matches/{id}/complete")
    public MatchService.MatchView complete(@PathVariable UUID id) {
        return matching.complete(current.require().getId(), id);
    }

    /**
     * Administrators only: the compatibility graph of everyone who would be in the next round, the optimal and greedy
     * matchings on it, and a check of the optimality conditions. Nothing is stored or proposed.
     */
    @GetMapping("/graph")
    public MatchGraphService.GraphView graph(@RequestParam(defaultValue = "4") int topK) {
        current.requireAdmin();
        return graphs.graph(Math.max(1, Math.min(topK, 20)));
    }

    /** Administrators only: timings of recent matching rounds. */
    @GetMapping("/rounds")
    public List<MatchGraphService.RoundView> rounds() {
        current.requireAdmin();
        return graphs.recentRounds();
    }
}

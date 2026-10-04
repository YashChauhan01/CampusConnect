package edu.campusconnect.matching;

import edu.campusconnect.security.CurrentStudent;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/matching")
public class MatchController {

    private final CurrentStudent current;
    private final MatchService matching;

    public MatchController(CurrentStudent current, MatchService matching) {
        this.current = current;
        this.matching = matching;
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
}

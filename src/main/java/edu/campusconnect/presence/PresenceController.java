package edu.campusconnect.presence;

import edu.campusconnect.security.CurrentStudent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/presence")
public class PresenceController {

    public record CheckInRequest(@NotNull Long zoneId, @NotNull Presence.Status status,
                                 @Size(max = 300) String requirements,
                                 @Size(max = 10) Set<Long> seekingSubjectIds) {}

    private final CurrentStudent current;
    private final PresenceService presence;

    public PresenceController(CurrentStudent current, PresenceService presence) {
        this.current = current;
        this.presence = presence;
    }

    @GetMapping("/zones")
    public List<PresenceService.ZoneView> zones() {
        current.require();
        return presence.zones();
    }

    @PostMapping("/check-in")
    public PresenceService.PresenceView checkIn(@Valid @RequestBody CheckInRequest request) {
        return presence.checkIn(current.require().getId(), request.zoneId(), request.status(),
                request.requirements(), request.seekingSubjectIds());
    }

    @PostMapping("/check-out")
    public ResponseEntity<Void> checkOut() {
        presence.checkOut(current.require().getId());
        return ResponseEntity.noContent().build();
    }

    /** The caller's active presence, or 204 when they are not checked in. */
    @GetMapping("/me")
    public ResponseEntity<PresenceService.PresenceView> mine() {
        return presence.mine(current.require().getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/available")
    public List<PresenceService.PresenceView> available(@RequestParam(required = false) Long zoneId) {
        return presence.available(current.require().getId(), zoneId);
    }
}

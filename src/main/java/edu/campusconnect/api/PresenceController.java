package edu.campusconnect.api;

import edu.campusconnect.model.CampusZone;
import edu.campusconnect.model.Presence;
import edu.campusconnect.model.Student;
import edu.campusconnect.repo.PresenceRepository;
import edu.campusconnect.repo.ZoneRepository;
import edu.campusconnect.security.CurrentStudent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/presence")
public class PresenceController {
    private final CurrentStudent current;
    private final PresenceRepository presence;
    private final ZoneRepository zones;
    private final Duration presenceTtl;

    public PresenceController(CurrentStudent current, PresenceRepository presence,
                              ZoneRepository zones,
                              @Value("${app.presence-minutes:15}") long presenceMinutes) {
        if (presenceMinutes <= 0) {
            throw new IllegalArgumentException("app.presence-minutes must be positive");
        }

        this.current = current;
        this.presence = presence;
        this.zones = zones;
        this.presenceTtl = Duration.ofMinutes(presenceMinutes);
    }

    public record Checkin(@NotNull Long zoneId, @NotNull Presence.Status status) {
    }

    public record PresenceView(UUID studentId, String name, String zone, String status,
                               Instant expiresAt) {
    }

    public record ZoneView(Long id, String name) {
    }

    @GetMapping("/zones")
    public List<ZoneView> zones() {
        current.get();
        return zones.findByEnabledTrue().stream()
                .map(zone -> new ZoneView(zone.id, zone.name))
                .toList();
    }

    @PostMapping("/check-in")
    public PresenceView checkin(@Valid @RequestBody Checkin request) {
        Student student = current.get();
        CampusZone zone = zones.findById(request.zoneId())
                .filter(candidate -> candidate.enabled)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid zone"));

        Presence record = new Presence(student.id, student.fullName, zone.id, zone.name,
                request.status(), presenceTtl);
        return view(presence.save(record));
    }

    @PostMapping("/check-out")
    public Map<String, String> checkout() {
        presence.deleteById(current.get().id);
        return Map.of("message", "Checked out");
    }

    @GetMapping("/me")
    public Optional<PresenceView> mine() {
        return presence.findById(current.get().id)
                .filter(record -> record.expiresAt.isAfter(Instant.now()))
                .map(this::view);
    }

    @GetMapping("/available")
    public List<PresenceView> available(@RequestParam(required = false) Long zoneId) {
        current.get();
        List<Presence> matches = zoneId == null
                ? presence.findByStatus(Presence.Status.AVAILABLE)
                : presence.findByStatusAndZoneId(Presence.Status.AVAILABLE, zoneId);

        Instant now = Instant.now();
        return matches.stream()
                .filter(record -> record.expiresAt.isAfter(now))
                .map(this::view)
                .toList();
    }

    private PresenceView view(Presence record) {
        return new PresenceView(record.id, record.studentName, record.zoneName,
                record.status.name(), record.expiresAt);
    }
}

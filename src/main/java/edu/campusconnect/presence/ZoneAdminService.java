package edu.campusconnect.presence;

import edu.campusconnect.common.ApiException;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrator operations on campus zones. Zones are disabled rather than deleted so history stays intact. */
@Service
public class ZoneAdminService {

    public record ZoneAdminView(Long id, String name, boolean enabled, Double x, Double y, int checkedIn) {}

    private final ZoneRepository zones;
    private final PresenceRepository presences;
    private final Clock clock;

    public ZoneAdminService(ZoneRepository zones, PresenceRepository presences, Clock clock) {
        this.zones = zones;
        this.presences = presences;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ZoneAdminView> list() {
        Map<Long, Integer> counts = new HashMap<>();
        for (Object[] row : presences.countActiveByZone(clock.instant())) {
            counts.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return zones.findAllByOrderByName().stream().map(z -> view(z, counts.getOrDefault(z.getId(), 0))).toList();
    }

    @Transactional
    public ZoneAdminView create(String name, Double x, Double y) {
        String clean = clean(name);
        checkPosition(x, y);
        if (zones.existsByNameIgnoreCase(clean)) {
            throw ApiException.conflict("A zone with this name already exists");
        }
        return view(zones.save(new CampusZone(clean, x, y)), 0);
    }

    @Transactional
    public ZoneAdminView update(Long id, String name, Double x, Double y, boolean enabled) {
        CampusZone zone = zones.findById(id).orElseThrow(() -> ApiException.notFound("Zone not found"));
        String clean = clean(name);
        checkPosition(x, y);
        if (zones.existsByNameIgnoreCaseAndIdNot(clean, id)) {
            throw ApiException.conflict("A zone with this name already exists");
        }
        zone.setName(clean);
        zone.setX(x);
        zone.setY(y);
        zone.setEnabled(enabled);
        zones.flush();
        return list().stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
    }

    private static String clean(String name) {
        String clean = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (clean.length() < 2) {
            throw ApiException.badRequest("Zone name must have at least 2 characters");
        }
        return clean;
    }

    private static void checkPosition(Double x, Double y) {
        if ((x == null) != (y == null)) {
            throw ApiException.badRequest("Set both coordinates or neither");
        }
    }

    private static ZoneAdminView view(CampusZone z, int checkedIn) {
        return new ZoneAdminView(z.getId(), z.getName(), z.isEnabled(), z.getX(), z.getY(), checkedIn);
    }
}

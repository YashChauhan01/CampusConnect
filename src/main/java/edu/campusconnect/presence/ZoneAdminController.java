package edu.campusconnect.presence;

import edu.campusconnect.security.CurrentStudent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/zones")
public class ZoneAdminController {

    /** Coordinates are metres on the campus plane (any origin); bounded to reject typos like 1e12. */
    public record ZoneRequest(@NotBlank @Size(max = 100) String name,
                              @DecimalMin("-100000") @DecimalMax("100000") Double x,
                              @DecimalMin("-100000") @DecimalMax("100000") Double y,
                              Boolean enabled) {}

    private final CurrentStudent current;
    private final ZoneAdminService zones;

    public ZoneAdminController(CurrentStudent current, ZoneAdminService zones) {
        this.current = current;
        this.zones = zones;
    }

    @GetMapping
    public List<ZoneAdminService.ZoneAdminView> list() {
        current.requireAdmin();
        return zones.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ZoneAdminService.ZoneAdminView create(@Valid @RequestBody ZoneRequest request) {
        current.requireAdmin();
        return zones.create(request.name(), request.x(), request.y());
    }

    @PutMapping("/{id}")
    public ZoneAdminService.ZoneAdminView update(@PathVariable Long id, @Valid @RequestBody ZoneRequest request) {
        current.requireAdmin();
        return zones.update(id, request.name(), request.x(), request.y(), request.enabled() == null || request.enabled());
    }
}

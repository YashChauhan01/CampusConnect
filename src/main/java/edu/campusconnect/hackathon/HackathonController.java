package edu.campusconnect.hackathon;

import edu.campusconnect.security.CurrentStudent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hackathons")
public class HackathonController {

    public record RoleRequest(@NotBlank @Size(min = 2, max = 60) String name,
                              @Size(max = 10) List<@NotBlank @Size(max = 30) String> keywords) {}

    public record CreateRequest(@NotBlank @Size(min = 3, max = 120) String name,
                                @Size(max = 1000) String description,
                                @NotEmpty List<@Valid RoleRequest> roles) {}

    public record RegisterRequest(@Size(max = 3) List<Long> rolePreferences) {}

    private final CurrentStudent current;
    private final HackathonService hackathons;

    public HackathonController(CurrentStudent current, HackathonService hackathons) {
        this.current = current;
        this.hackathons = hackathons;
    }

    @GetMapping
    public List<HackathonService.Summary> list() {
        return hackathons.list(current.require());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HackathonService.Detail create(@Valid @RequestBody CreateRequest request) {
        return hackathons.create(current.require(), request.name(), request.description(),
                request.roles().stream().map(r -> new HackathonService.RoleSpec(r.name(), r.keywords())).toList());
    }

    @GetMapping("/{id}")
    public HackathonService.Detail detail(@PathVariable UUID id) {
        return hackathons.detail(id, current.require());
    }

    @PutMapping("/{id}/registration")
    public HackathonService.Detail register(@PathVariable UUID id, @Valid @RequestBody RegisterRequest request) {
        return hackathons.register(current.require(), id, request.rolePreferences());
    }

    @DeleteMapping("/{id}/registration")
    public HackathonService.Detail withdraw(@PathVariable UUID id) {
        return hackathons.withdraw(current.require(), id);
    }

    @PostMapping("/{id}/close")
    public HackathonService.Detail close(@PathVariable UUID id) {
        return hackathons.closeRegistration(current.require(), id);
    }

    @PostMapping("/{id}/reopen")
    public HackathonService.Detail reopen(@PathVariable UUID id) {
        return hackathons.reopenRegistration(current.require(), id);
    }

    @PostMapping("/{id}/synthesize")
    public HackathonService.TeamsView synthesize(@PathVariable UUID id) {
        return hackathons.synthesize(current.require(), id);
    }

    @PostMapping("/{id}/publish")
    public HackathonService.TeamsView publish(@PathVariable UUID id) {
        return hackathons.publish(current.require(), id);
    }

    @GetMapping("/{id}/teams")
    public HackathonService.TeamsView teams(@PathVariable UUID id) {
        return hackathons.teams(current.require(), id);
    }
}

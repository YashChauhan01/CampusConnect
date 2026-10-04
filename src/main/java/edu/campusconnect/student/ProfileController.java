package edu.campusconnect.student;

import edu.campusconnect.security.CurrentStudent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProfileController {

    /** Letters, digits, spaces and the punctuation found in names such as "C++", "C#", ".NET", "R&D". */
    private static final String NAME_PATTERN = "^[\\p{L}\\p{N} .+#&/_-]+$";

    public record UpdateProfile(@NotBlank @Size(max = 120) String fullName, @Size(max = 500) String bio) {}

    public record SaveItem(
            @NotBlank @Size(max = 80) @Pattern(regexp = NAME_PATTERN, message = "contains unsupported characters") String name,
            @NotNull Proficiency proficiency) {}

    private final CurrentStudent current;
    private final ProfileService profiles;

    public ProfileController(CurrentStudent current, ProfileService profiles) {
        this.current = current;
        this.profiles = profiles;
    }

    @GetMapping("/students/me")
    public ProfileService.Profile me() {
        return profiles.get(current.require());
    }

    @PutMapping("/students/me")
    public ProfileService.Profile update(@Valid @RequestBody UpdateProfile request) {
        return profiles.update(current.require(), request.fullName(), request.bio());
    }

    @PostMapping("/students/me/skills")
    public ProfileService.Profile saveSkill(@Valid @RequestBody SaveItem request) {
        return profiles.saveSkill(current.require(), request.name(), request.proficiency());
    }

    @DeleteMapping("/students/me/skills/{id}")
    public ProfileService.Profile removeSkill(@PathVariable Long id) {
        return profiles.removeSkill(current.require(), id);
    }

    @PostMapping("/students/me/subjects")
    public ProfileService.Profile saveSubject(@Valid @RequestBody SaveItem request) {
        return profiles.saveSubject(current.require(), request.name(), request.proficiency());
    }

    @DeleteMapping("/students/me/subjects/{id}")
    public ProfileService.Profile removeSubject(@PathVariable Long id) {
        return profiles.removeSubject(current.require(), id);
    }

    @GetMapping("/skills")
    public List<String> skills(@RequestParam(required = false) String q) {
        current.id();
        return profiles.suggestSkills(q);
    }

    @GetMapping("/subjects")
    public List<String> subjects(@RequestParam(required = false) String q) {
        current.id();
        return profiles.suggestSubjects(q);
    }
}

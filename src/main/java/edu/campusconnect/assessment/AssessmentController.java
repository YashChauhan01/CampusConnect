package edu.campusconnect.assessment;

import edu.campusconnect.security.CurrentStudent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assessments")
public class AssessmentController {

    public record StartRequest(@NotNull SkillAssessment.Kind kind, @NotNull Long itemId) {}

    public record SubmitRequest(@NotEmpty @Size(max = 10) List<String> answers) {}

    private final CurrentStudent current;
    private final AssessmentService assessments;

    public AssessmentController(CurrentStudent current, AssessmentService assessments) {
        this.current = current;
        this.assessments = assessments;
    }

    /** Lets the UI hide the feature when no AI provider is configured. */
    @GetMapping("/status")
    public AssessmentService.StatusView status() {
        current.require();
        return assessments.status();
    }

    @PostMapping
    public AssessmentService.AssessmentView start(@Valid @RequestBody StartRequest request) {
        return assessments.start(current.require().getId(), request.kind(), request.itemId());
    }

    @PostMapping("/{id}/submit")
    public AssessmentService.AssessmentView submit(@PathVariable UUID id, @Valid @RequestBody SubmitRequest request) {
        return assessments.submit(current.require().getId(), id, request.answers());
    }
}

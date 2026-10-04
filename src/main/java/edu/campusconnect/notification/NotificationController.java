package edu.campusconnect.notification;

import edu.campusconnect.security.CurrentStudent;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final CurrentStudent current;
    private final NotificationService notifications;

    public NotificationController(CurrentStudent current, NotificationService notifications) {
        this.current = current;
        this.notifications = notifications;
    }

    @GetMapping
    public NotificationService.Inbox inbox() {
        return notifications.inbox(current.require().getId());
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(@PathVariable UUID id) {
        notifications.markRead(current.require().getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> readAll() {
        notifications.markAllRead(current.require().getId());
        return ResponseEntity.noContent().build();
    }
}

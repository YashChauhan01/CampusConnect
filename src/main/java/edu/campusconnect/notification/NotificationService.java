package edu.campusconnect.notification;

import edu.campusconnect.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Stores notifications and pushes them to the student's open browser tabs once the transaction commits. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int PAGE_SIZE = 50;
    private static final Duration RETENTION = Duration.ofDays(30);

    public static final String USER_QUEUE = "/queue/updates";
    public static final String PRESENCE_TOPIC = "/topic/presence";

    public record View(UUID id, Notification.Type type, String title, String body, String link, Instant createdAt,
                       boolean read) {}

    public record Inbox(List<View> items, long unread) {}

    /** What travels over the WebSocket: either a stored notification, or a bare "something changed" hint. */
    public record Push(String kind, String topic, View notification) {}

    private final NotificationRepository notifications;
    private final SimpMessagingTemplate messaging;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, SimpMessagingTemplate messaging, Clock clock) {
        this.notifications = notifications;
        this.messaging = messaging;
        this.clock = clock;
    }

    /** Persists a notification and delivers it in real time after the surrounding transaction commits. */
    @Transactional
    public void notify(UUID studentId, Notification.Type type, String title, String body, String link) {
        Notification saved = notifications.save(new Notification(studentId, type, title, body, link, clock.instant()));
        View view = toView(saved);
        afterCommit(() -> sendToUser(studentId, new Push("NOTIFICATION", topicOf(type), view)));
    }

    /** Tells a student's clients that data on {@code topic} (matches, hackathons, ...) changed and should be refetched. */
    public void hint(UUID studentId, String topic) {
        afterCommit(() -> sendToUser(studentId, new Push("REFRESH", topic, null)));
    }

    public void broadcastPresenceChange() {
        afterCommit(() -> messaging.convertAndSend(PRESENCE_TOPIC, new Push("REFRESH", "presence", null)));
    }

    @Transactional(readOnly = true)
    public Inbox inbox(UUID studentId) {
        List<View> items = notifications.findLatest(studentId, PageRequest.of(0, PAGE_SIZE)).stream()
                .map(NotificationService::toView).toList();
        return new Inbox(items, notifications.countUnread(studentId));
    }

    @Transactional
    public void markRead(UUID studentId, UUID notificationId) {
        Notification n = notifications.findById(notificationId).filter(x -> x.getStudentId().equals(studentId))
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        if (n.getReadAt() == null) {
            n.setReadAt(clock.instant());
        }
    }

    @Transactional
    public void markAllRead(UUID studentId) {
        notifications.markAllRead(studentId, clock.instant());
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    void purgeOld() {
        int removed = notifications.deleteOlderThan(clock.instant().minus(RETENTION));
        if (removed > 0) {
            log.info("Purged {} old notifications", removed);
        }
    }

    private void sendToUser(UUID studentId, Push push) {
        try {
            messaging.convertAndSendToUser(studentId.toString(), USER_QUEUE, push);
        } catch (RuntimeException e) {
            // Delivery is best effort: the notification is stored and shows up on the next fetch.
            log.warn("Could not push update to {}", studentId, e);
        }
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static String topicOf(Notification.Type type) {
        return type == Notification.Type.TEAMS_PUBLISHED ? "hackathons" : "matches";
    }

    static View toView(Notification n) {
        return new View(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(), n.getCreatedAt(),
                n.getReadAt() != null);
    }
}

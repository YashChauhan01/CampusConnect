package edu.campusconnect.notification;

import edu.campusconnect.hackathon.HackathonEvent;
import edu.campusconnect.matching.Match;
import edu.campusconnect.matching.MatchEvent;
import edu.campusconnect.matching.MatchRepository;
import edu.campusconnect.presence.PresenceService.PresenceChanged;
import edu.campusconnect.student.Student;
import edu.campusconnect.student.StudentRepository;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Translates domain events into stored notifications and live updates. Handlers run inside the publisher's
 * transaction, so notifications are committed atomically with the change that caused them and are only pushed after
 * commit.
 */
@Component
class NotificationListener {

    private static final String MATCHES_LINK = "/matches";
    private static final String HACKATHONS_LINK = "/hackathons";

    private final NotificationService notifications;
    private final StudentRepository students;
    private final MatchRepository matches;

    NotificationListener(NotificationService notifications, StudentRepository students, MatchRepository matches) {
        this.notifications = notifications;
        this.students = students;
        this.matches = matches;
    }

    @EventListener
    void on(MatchEvent event) {
        switch (event) {
            case MatchEvent.Proposed e -> {
                int score = matches.findById(e.matchId()).map(m -> (int) Math.round(m.getWeight() * 100)).orElse(0);
                notifyBoth(e.studentA(), e.studentB(), Notification.Type.MATCH_PROPOSED, "New study partner match",
                        (other) -> "You were matched with " + name(other) + " (" + score + "% compatible). "
                                + "Accept to meet up.");
            }
            case MatchEvent.PartnerAccepted e -> notifications.notify(e.waitingStudent(),
                    Notification.Type.MATCH_PARTNER_ACCEPTED, name(e.acceptedBy()) + " accepted",
                    name(e.acceptedBy()) + " wants to study with you. Accept to confirm the match.", MATCHES_LINK);
            case MatchEvent.Confirmed e -> notifyBoth(e.studentA(), e.studentB(), Notification.Type.MATCH_CONFIRMED,
                    "Match confirmed", (other) -> "You and " + name(other) + " are matched. Say hello!");
            case MatchEvent.Declined e -> notifications.notify(e.other(), Notification.Type.MATCH_DECLINED,
                    "Match declined", "Your proposed partner is not available. We will keep looking.", MATCHES_LINK);
            case MatchEvent.Expired e -> notifyBoth(e.studentA(), e.studentB(), Notification.Type.MATCH_EXPIRED,
                    "Match expired", (other) -> "The match with " + name(other) + " was not completed in time.");
            case MatchEvent.Completed e -> {
                notifications.hint(e.studentA(), "matches");
                notifications.hint(e.studentB(), "matches");
            }
        }
    }

    @EventListener
    void on(HackathonEvent.TeamsPublished event) {
        for (UUID id : event.participantIds()) {
            notifications.notify(id, Notification.Type.TEAMS_PUBLISHED, "Teams announced",
                    "Teams for " + event.hackathonName() + " are ready. Find out who you will be building with.",
                    HACKATHONS_LINK + "/" + event.hackathonId());
        }
    }

    @EventListener
    void on(PresenceChanged event) {
        notifications.broadcastPresenceChange();
    }

    private void notifyBoth(UUID a, UUID b, Notification.Type type, String title,
                            java.util.function.Function<UUID, String> bodyForOther) {
        notifications.notify(a, type, title, bodyForOther.apply(b), MATCHES_LINK);
        notifications.notify(b, type, title, bodyForOther.apply(a), MATCHES_LINK);
    }

    private String name(UUID studentId) {
        return students.findById(studentId).map(Student::getFullName).orElse("a classmate");
    }
}

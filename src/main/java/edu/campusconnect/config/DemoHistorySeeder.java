package edu.campusconnect.config;

import edu.campusconnect.matching.ChatMessage;
import edu.campusconnect.matching.ChatMessageRepository;
import edu.campusconnect.matching.CompatibilityScorer;
import edu.campusconnect.matching.Match;
import edu.campusconnect.matching.MatchRepository;
import edu.campusconnect.notification.Notification;
import edu.campusconnect.notification.NotificationRepository;
import edu.campusconnect.student.ProfileService;
import edu.campusconnect.student.Student;
import edu.campusconnect.student.StudentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Gives the demo accounts a past: a few finished study sessions with chat history and the notifications that go with
 * them, so the Matches page, the chat and the bell are not empty on a fresh database. Runs once (it does nothing when
 * the demo accounts already have a chat), only with the {@code dev} profile, and can be switched off with
 * {@code app.seed.demo-history=false}. The sessions are in the past, so nobody is blocked from finding a new partner.
 */
@Component
@Profile("dev")
@Order(3)
class DemoHistorySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoHistorySeeder.class);
    private static final String MATCHES_LINK = "/matches";

    /** One line of a conversation: who says it (the requester or the target) and when, in minutes after the start. */
    private record Line(boolean byRequester, int minute, String text) {}

    private record Session(String requester, String target, int daysAgo, double weight, double knowledge,
                           double reciprocity, double breadth, double proximity, List<String> subjects,
                           List<Line> chat) {}

    private static final List<Session> SESSIONS = List.of(
            new Session("asha@college.edu", "dev@college.edu", 3, 0.84, 0.9, 0.7, 0.34, 1.0,
                    List.of("Data Structures"), List.of(
                    new Line(true, 0, "Hi Dev! I am stuck on balanced BSTs before the quiz. Can we go through AVL rotations?"),
                    new Line(false, 2, "Sure, I am at Study Hall. Bring your notes and we will draw a few examples."),
                    new Line(true, 3, "Perfect, leaving now."),
                    new Line(false, 52, "Left-right and right-left cases are the ones people mix up. Try the practice set tonight."),
                    new Line(true, 55, "That helped a lot, thank you!"))),
            new Session("ishaan@college.edu", "ravi@college.edu", 2, 0.78, 0.8, 0.6, 0.34, 0.9,
                    List.of("DBMS"), List.of(
                    new Line(true, 0, "Hey Ravi, could you explain normal forms? 3NF vs BCNF keeps confusing me."),
                    new Line(false, 4, "Happy to. Meet at the Cafeteria in 10 minutes?"),
                    new Line(true, 5, "Yes please."),
                    new Line(false, 47, "Remember: every determinant must be a candidate key for BCNF. I will share my notes."))),
            new Session("ravi@college.edu", "asha@college.edu", 1, 0.91, 0.95, 0.85, 0.67, 1.0,
                    List.of("Data Structures", "DBMS"), List.of(
                    new Line(true, 0, "Asha, want to revise for the DBMS and DSA internals together?"),
                    new Line(false, 1, "Yes! Library, second floor?"),
                    new Line(true, 2, "On my way."),
                    new Line(false, 90, "Good session. Same time next week?"),
                    new Line(true, 93, "Definitely. I will bring the transaction problems."))));

    private final StudentRepository students;
    private final ProfileService profiles;
    private final MatchRepository matches;
    private final ChatMessageRepository chat;
    private final NotificationRepository notifications;
    private final Clock clock;
    private final boolean enabled;

    DemoHistorySeeder(StudentRepository students, ProfileService profiles, MatchRepository matches,
                      ChatMessageRepository chat, NotificationRepository notifications, Clock clock,
                      @Value("${app.seed.demo-history:true}") boolean enabled) {
        this.students = students;
        this.profiles = profiles;
        this.matches = matches;
        this.chat = chat;
        this.notifications = notifications;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        Optional<Student> asha = students.findByEmail("asha@college.edu");
        if (asha.isEmpty() || alreadySeeded(asha.get())) {
            return;
        }
        int created = 0;
        List<Notification> all = new ArrayList<>();
        for (Session s : SESSIONS) {
            Optional<Student> requester = students.findByEmail(s.requester());
            Optional<Student> target = students.findByEmail(s.target());
            if (requester.isEmpty() || target.isEmpty()) {
                continue;
            }
            all.addAll(seed(s, requester.get(), target.get()));
            created++;
        }
        // The newest chat message of each student stays unread, so the bell has something to open.
        all.stream().filter(n -> n.getType() == Notification.Type.CHAT_MESSAGE)
                .collect(java.util.stream.Collectors.toMap(Notification::getStudentId, n -> n,
                        (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b))
                .values().forEach(n -> n.setReadAt(null));
        notifications.saveAll(all);
        log.info("Seeded {} finished study sessions with chat history for the demo accounts", created);
    }

    private boolean alreadySeeded(Student asha) {
        return matches.findHistory(asha.getId(), PageRequest.of(0, 50)).stream()
                .anyMatch(m -> !chat.findByMatchIdOrderByCreatedAtAsc(m.getId()).isEmpty());
    }

    /** Stores one finished session and its chat; returns its notifications (all read, the caller reopens some). */
    private List<Notification> seed(Session s, Student requester, Student target) {
        Instant start = clock.instant().minus(Duration.ofDays(s.daysAgo())).minus(Duration.ofHours(2));
        List<Long> shared = profiles.get(requester).subjects().stream()
                .filter(sub -> s.subjects().contains(sub.name())).map(ProfileService.Item::id).toList();
        var score = new CompatibilityScorer.Score(s.weight(), s.knowledge(), s.reciprocity(), s.breadth(),
                s.proximity(), shared);

        Instant end = start.plus(Duration.ofMinutes(s.chat().get(s.chat().size() - 1).minute() + 5));
        Match match = Match.request(requester.getId(), target.getId(), score, start, start.plus(Duration.ofMinutes(15)));
        match.markAccepted(target.getId());
        match.setStatus(Match.Status.COMPLETED);
        match.setDecidedAt(end);
        matches.save(match);

        int percent = (int) Math.round(s.weight() * 100);
        List<Notification> out = new ArrayList<>();
        out.add(read(target, Notification.Type.MATCH_REQUESTED, "Study request from " + requester.getFullName(),
                requester.getFullName() + " wants to study with you (" + percent + "% compatible). Accept to meet up.",
                start));
        Instant confirmed = start.plus(Duration.ofMinutes(1));
        out.add(read(requester, Notification.Type.MATCH_CONFIRMED, "Match confirmed",
                "You and " + target.getFullName() + " are matched. Say hello in the chat!", confirmed));
        out.add(read(target, Notification.Type.MATCH_CONFIRMED, "Match confirmed",
                "You and " + requester.getFullName() + " are matched. Say hello in the chat!", confirmed));

        for (Line line : s.chat()) {
            Student sender = line.byRequester() ? requester : target;
            Student recipient = line.byRequester() ? target : requester;
            Instant at = start.plus(Duration.ofMinutes(line.minute()));
            chat.save(new ChatMessage(match.getId(), sender.getId(), line.text(), at));
            out.add(read(recipient, Notification.Type.CHAT_MESSAGE, "New message from " + sender.getFullName(),
                    line.text(), at));
        }
        return out;
    }

    private static Notification read(Student recipient, Notification.Type type, String title, String body, Instant at) {
        Notification n = new Notification(recipient.getId(), type, title, body, MATCHES_LINK, at);
        n.setReadAt(at.plus(Duration.ofMinutes(1)));
        return n;
    }
}

package edu.campusconnect.support;

import edu.campusconnect.mail.Mailer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;

/** Test transport that keeps every message in memory. */
public class RecordingMailer implements Mailer {

    public record Mail(String to, String subject, String body) {

        public String token() {
            Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(body);
            if (!m.find()) {
                throw new IllegalStateException("No token in mail body: " + body);
            }
            return m.group(1);
        }
    }

    private final List<Mail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String body) {
        sent.add(new Mail(to, subject, body));
    }

    public void clear() {
        sent.clear();
    }

    public List<Mail> all() {
        return List.copyOf(sent);
    }

    /** Waits for (asynchronous, after-commit) delivery of the newest message for {@code to} whose subject contains {@code subject}. */
    public Mail await(String to, String subject) {
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> find(to, subject) != null);
        return find(to, subject);
    }

    public int countFor(String to) {
        return (int) sent.stream().filter(m -> m.to().equals(to)).count();
    }

    private Mail find(String to, String subject) {
        Mail found = null;
        for (Mail m : sent) {
            if (m.to().equals(to) && m.subject().contains(subject)) {
                found = m;
            }
        }
        return found;
    }
}

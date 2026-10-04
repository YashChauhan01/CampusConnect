package edu.campusconnect.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Queues emails for delivery <em>after</em> the surrounding transaction commits, off the request thread,
 * so SMTP latency or outages never roll back or slow down the business operation.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    public record MailRequested(String to, String subject, String body) {}

    private final ApplicationEventPublisher events;
    private final Mailer mailer;

    public MailService(ApplicationEventPublisher events, Mailer mailer) {
        this.events = events;
        this.mailer = mailer;
    }

    public void send(String to, String subject, String body) {
        events.publishEvent(new MailRequested(to, subject, body));
    }

    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void deliver(MailRequested mail) {
        try {
            mailer.send(mail.to(), mail.subject(), mail.body());
        } catch (RuntimeException e) {
            log.error("Failed to deliver email '{}' to {}", mail.subject(), mail.to(), e);
        }
    }
}

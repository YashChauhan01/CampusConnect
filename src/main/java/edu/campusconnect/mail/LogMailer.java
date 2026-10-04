package edu.campusconnect.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development-only transport: writes the message (including any links) to the log instead of sending it. */
@Component
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "log")
class LogMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(LogMailer.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[mail:log] to={} subject={}\n{}", to, subject, body);
    }
}

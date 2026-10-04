package edu.campusconnect.mail;

/** Outbound plain-text email transport. */
public interface Mailer {

    void send(String to, String subject, String body);
}

package edu.campusconnect.auth;

/** Plain-text email bodies for the authentication flows. */
final class MailTemplates {

    private MailTemplates() {}

    static String verification(String name, String link, int minutes) {
        return """
                Hi %s,

                Confirm your college email to activate your CampusConnect account:

                %s

                The link is valid for %d minutes. If you did not sign up, you can ignore this message.
                """.formatted(name, link, minutes);
    }

    static String alreadyRegistered(String name, String loginLink, String resetLink) {
        return """
                Hi %s,

                Someone tried to register a CampusConnect account with this email address, but one already exists.

                Log in: %s
                Forgot your password? %s

                If this wasn't you, no action is needed.
                """.formatted(name, loginLink, resetLink);
    }

    static String passwordReset(String name, String link, int minutes) {
        return """
                Hi %s,

                Use this link to choose a new CampusConnect password:

                %s

                The link is valid for %d minutes and can be used once. If you did not ask for this, ignore this message.
                """.formatted(name, link, minutes);
    }
}

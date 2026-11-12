package com.hexora.hexora.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("consoleNotifier")
@Profile({"dev", "demo"})
public class ConsoleNotifier implements Notifier {

    // Dedicated logger: console only, never masked and never written to the log file (see logback-spring.xml).
    private static final Logger LOGGER = LoggerFactory.getLogger("tinyhat.notifier.console");

    @Override
    public void sendVerificationEmail(String email, String verificationUrl) {
        LOGGER.info("[CONSOLE NOTIFIER] Verification email for {}: enlace={}", email, verificationUrl);
    }

    @Override
    public void sendInvitationEmail(String email, String invitationUrl, String organizationName) {
        LOGGER.info(
            "[CONSOLE NOTIFIER] Invitation email for {} to organization {}: enlace={}",
            email, organizationName, invitationUrl
        );
    }

    @Override
    public void sendPasswordResetEmail(String email, String resetUrl) {
        LOGGER.info("[CONSOLE NOTIFIER] Password reset email for {}: enlace={}", email, resetUrl);
    }

    @Override
    public void sendAccountAlreadyExistsNotification(String email) {
        LOGGER.info(
            "[CONSOLE NOTIFIER] Notice for {}: An account already exists for this email address.",
            email
        );
    }
}

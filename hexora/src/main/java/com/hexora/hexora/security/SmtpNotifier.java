package com.hexora.hexora.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class SmtpNotifier implements Notifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpNotifier.class);

    @Override
    public void sendVerificationEmail(String email, String verificationUrl) {
        LOGGER.warn("SMTP notifier pending implementation for verification email to {}", email);
    }

    @Override
    public void sendInvitationEmail(String email, String invitationUrl, String organizationName) {
        LOGGER.warn("SMTP notifier pending implementation for invitation email to {}", email);
    }

    @Override
    public void sendPasswordResetEmail(String email, String resetUrl) {
        LOGGER.warn("SMTP notifier pending implementation for password reset to {}", email);
    }

    @Override
    public void sendAccountAlreadyExistsNotification(String email) {
        LOGGER.warn("SMTP notifier pending implementation for existing account notice to {}", email);
    }
}


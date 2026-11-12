package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProfileSecurityGuardMfaTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(ProfileSecurityGuard.class)
        .withPropertyValues(
            "spring.datasource.username=test",
            "spring.flyway.user=migrator",
            "spring.flyway.password=migrator",
            "spring.datasource.password=test"
        );

    @Test
    void refusesToStartWithoutAnEncryptionKeyWhenMfaIsRequired() {
        contextRunner
            .withPropertyValues("tinyhat.security.mfa.required=true")
            .run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .hasMessageContaining("TINYHAT_MFA_KEY must be a Base64-encoded 32-byte key"));
    }

    @Test
    void refusesToDisableMfaInProduction() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=prod",
                "tinyhat.security.mfa.required=false",
                "server.ssl.enabled=true",
                "tinyhat.security.csp.report-only=false",
                "tinyhat.security.network.allow-public-ranges=false",
                "tinyhat.security.public-base-url=https://hexora.example.com",
                "tinyhat.demo.seed.enabled=false",
                "server.servlet.session.cookie.secure=true"
            )
            .run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .hasMessageContaining("MFA must be required"));
    }

    @Test
    void refusesToStartProductionWithoutAuthenticatedStartTlsSmtpConfiguration() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=prod",
                "tinyhat.security.mfa.required=true",
                "tinyhat.security.mfa.key=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=",
                "server.ssl.enabled=true",
                "tinyhat.security.csp.report-only=false",
                "tinyhat.security.network.allow-public-ranges=false",
                "tinyhat.security.public-base-url=https://hexora.example.com",
                "tinyhat.demo.seed.enabled=false",
                "server.servlet.session.cookie.secure=true",
                "spring.mail.host=smtp.example.com",
                "spring.mail.username=mailer",
                "spring.mail.password=test-only-not-a-secret",
                "spring.mail.properties.mail.smtp.auth=false",
                "spring.mail.properties.mail.smtp.starttls.enable=false",
                "spring.mail.properties.mail.smtp.starttls.required=false",
                "spring.mail.properties.mail.smtp.ssl.checkserveridentity=false",
                "tinyhat.mail.from=no-reply@example.com"
            )
            .run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .hasMessageContaining(
                    "SMTP authentication, required STARTTLS, and server identity checks must be enabled"));
    }

    @Test
    void acceptsProductionWithAuthenticatedRequiredStartTlsSmtpConfiguration() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=prod",
                "tinyhat.security.mfa.required=true",
                "tinyhat.security.mfa.key=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=",
                "server.ssl.enabled=true",
                "tinyhat.security.csp.report-only=false",
                "tinyhat.security.network.allow-public-ranges=false",
                "tinyhat.security.public-base-url=https://hexora.example.com",
                "tinyhat.demo.seed.enabled=false",
                "server.servlet.session.cookie.secure=true",
                "spring.mail.host=smtp.example.com",
                "spring.mail.username=mailer",
                "spring.mail.password=test-only-not-a-secret",
                "spring.mail.properties.mail.smtp.auth=true",
                "spring.mail.properties.mail.smtp.starttls.enable=true",
                "spring.mail.properties.mail.smtp.starttls.required=true",
                "spring.mail.properties.mail.smtp.ssl.checkserveridentity=true",
                "tinyhat.mail.from=no-reply@example.com"
            )
            .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void refusesToUseTheRuntimeDatabaseUserForFlywayInProduction() {
        contextRunner
            .withPropertyValues(
                "spring.profiles.active=prod",
                "spring.flyway.user=test"
            )
            .run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .hasMessageContaining("Flyway must use a separate database user"));
    }
}

package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProfileSecurityGuardMfaTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(ProfileSecurityGuard.class)
        .withPropertyValues(
            "spring.datasource.username=test",
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
                "tinyhat.demo.seed.enabled=false",
                "server.servlet.session.cookie.secure=true"
            )
            .run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .hasMessageContaining("MFA must be required"));
    }
}

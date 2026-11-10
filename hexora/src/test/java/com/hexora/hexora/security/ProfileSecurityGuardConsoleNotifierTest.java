package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ProfileSecurityGuardConsoleNotifierTest {

    @Configuration
    static class TestConfig {
        @Bean
        public ConsoleNotifier consoleNotifier() {
            return new ConsoleNotifier();
        }
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(ProfileSecurityGuard.class, TestConfig.class)
        .withPropertyValues(
            "spring.datasource.username=test",
            "spring.datasource.password=test"
        );

    @Test
    void refusesToStartInProductionWithConsoleNotifier() {
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
                "server.servlet.session.cookie.secure=true"
            )
            .run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .hasMessageContaining("development-only components must not be active"));
    }
}

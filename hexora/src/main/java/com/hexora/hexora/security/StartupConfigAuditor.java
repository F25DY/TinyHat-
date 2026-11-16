package com.hexora.hexora.security;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Writes one STARTUP_CONFIG audit row with the effective security configuration. Only booleans,
 * profile names and counts are recorded: never credentials, keys, URLs with userinfo or user names.
 */
@Component
public class StartupConfigAuditor {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupConfigAuditor.class);

    private final AuditService auditService;
    private final Environment environment;

    public StartupConfigAuditor(AuditService auditService, Environment environment) {
        this.auditService = auditService;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            auditService.startupConfig(effectiveConfiguration());
        } catch (RuntimeException exception) {
            // Auditing the start must never prevent the application from serving requests.
            LOGGER.error("Could not record the STARTUP_CONFIG audit event; exceptionType={}",
                exception.getClass().getName());
        }
    }

    Map<String, Object> effectiveConfiguration() {
        List<String> profiles = Arrays.asList(
            environment.getActiveProfiles().length == 0
                ? environment.getDefaultProfiles()
                : environment.getActiveProfiles());
        String runtimeUser = environment.getProperty("spring.datasource.username");
        String flywayUser = environment.getProperty("spring.flyway.user");
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("profiles", profiles);
        configuration.put("bindAddress", environment.getProperty("server.address", "127.0.0.1"));
        configuration.put("https", environment.getProperty("server.ssl.enabled", Boolean.class, false));
        configuration.put("secureCookie",
            environment.getProperty("server.servlet.session.cookie.secure", Boolean.class, true));
        configuration.put("mfaRequired",
            environment.getProperty("tinyhat.security.mfa.required", Boolean.class, true));
        configuration.put("cspReportOnly",
            environment.getProperty("tinyhat.security.csp.report-only", Boolean.class, true));
        configuration.put("publicRangesAllowed",
            environment.getProperty("tinyhat.security.network.allow-public-ranges", Boolean.class, false));
        configuration.put("demoSeedEnabled",
            environment.getProperty("tinyhat.demo.seed.enabled", Boolean.class, false));
        configuration.put("registrationEnabled",
            environment.getProperty("tinyhat.security.registration.enabled", Boolean.class, true));
        configuration.put("wsAllowedOrigins", environment.getProperty("tinyhat.security.ws.allowed-origins[0]") == null
            ? 0 : countIndexed("tinyhat.security.ws.allowed-origins"));
        configuration.put("separateFlywayUser",
            flywayUser != null && !flywayUser.isBlank() && !flywayUser.equals(runtimeUser));
        configuration.put("auditRetentionDays",
            environment.getProperty("tinyhat.security.audit.retention-days", Integer.class, 365));
        return configuration;
    }

    private int countIndexed(String prefix) {
        int count = 0;
        while (environment.getProperty(prefix + "[" + count + "]") != null) {
            count++;
        }
        return count;
    }
}

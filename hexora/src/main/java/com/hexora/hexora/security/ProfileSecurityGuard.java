package com.hexora.hexora.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProfileSecurityGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileSecurityGuard.class);
    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        boolean demoSeedEnabled = environment.getProperty("tinyhat.demo.seed.enabled", Boolean.class, false);
        boolean sslEnabled = environment.getProperty("server.ssl.enabled", Boolean.class, false);
        boolean mfaRequired = environment.getProperty("tinyhat.security.mfa.required", Boolean.class, true);
        boolean cspReportOnly = environment.getProperty("tinyhat.security.csp.report-only", Boolean.class, true);
        boolean publicRangesAllowed = environment.getProperty(
            "tinyhat.security.network.allow-public-ranges", Boolean.class, false);
        boolean secureCookie = environment.getProperty(
            "server.servlet.session.cookie.secure", Boolean.class, true);
        String mfaKey = environment.getProperty("tinyhat.security.mfa.key");
        String publicBaseUrl = environment.getProperty("tinyhat.security.public-base-url");
        String mailHost = environment.getProperty("spring.mail.host");
        String mailUsername = environment.getProperty("spring.mail.username");
        String mailPassword = environment.getProperty("spring.mail.password");
        String mailFrom = environment.getProperty("tinyhat.mail.from");
        boolean smtpAuth = environment.getProperty("spring.mail.properties.mail.smtp.auth", Boolean.class, true);
        boolean smtpStartTls = environment.getProperty(
            "spring.mail.properties.mail.smtp.starttls.enable", Boolean.class, true);
        boolean smtpStartTlsRequired = environment.getProperty(
            "spring.mail.properties.mail.smtp.starttls.required", Boolean.class, true);
        boolean smtpServerIdentityCheck = environment.getProperty(
            "spring.mail.properties.mail.smtp.ssl.checkserveridentity", Boolean.class, true);

        List<String> violations = new ArrayList<>();
        if (!hasText(environment.getProperty("spring.datasource.username"))) {
            violations.add("DB_USER must be configured");
        }
        if (!hasText(environment.getProperty("spring.datasource.password"))) {
            violations.add("DB_PASSWORD must be configured");
        }
        if (mfaRequired && !MfaKeyValidator.isValid(mfaKey)) {
            violations.add("TINYHAT_MFA_KEY must be a Base64-encoded 32-byte key when MFA is required");
        }

        if (production) {
            if (!sslEnabled) {
                violations.add("HTTPS must be enabled");
            }
            if (!mfaRequired) {
                violations.add("MFA must be required");
            }
            if (cspReportOnly) {
                violations.add("CSP report-only mode must be disabled");
            }
            if (publicRangesAllowed) {
                violations.add("public network ranges must be disabled");
            }
            if (demoSeedEnabled) {
                violations.add("demo data seeding must be disabled");
            }
            if (hasConsoleNotifier(beanFactory)) {
                violations.add("development-only components must not be active");
            }
            if (!secureCookie) {
                violations.add("the session cookie must be Secure");
            }
            if (!isHttpsPublicBaseUrl(publicBaseUrl)) {
                violations.add("PUBLIC_BASE_URL must be an absolute HTTPS URL");
            }
            if (!hasText(mailHost) || !hasText(mailUsername) || !hasText(mailPassword)) {
                violations.add("SMTP_HOST, SMTP_USERNAME, and SMTP_PASSWORD must be configured");
            }
            if (!isValidEmailAddress(mailFrom)) {
                violations.add("SMTP_FROM must be a valid email address");
            }
            if (!smtpAuth || !smtpStartTls || !smtpStartTlsRequired || !smtpServerIdentityCheck) {
                violations.add(
                    "SMTP authentication, required STARTTLS, and server identity checks must be enabled");
            }
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                (production ? "Unsafe prod configuration: " : "Invalid configuration: ")
                    + String.join("; ", violations));
        }

        LOGGER.info(
            "Security configuration: profiles={}, bindAddress={}, https={}, secureCookie={}, "
                + "mfaRequired={}, cspReportOnly={}, publicRangesAllowed={}, demoSeedEnabled={}",
            Arrays.toString(environment.getActiveProfiles().length == 0
                ? environment.getDefaultProfiles()
                : environment.getActiveProfiles()),
            environment.getProperty("server.address", "127.0.0.1"),
            sslEnabled,
            secureCookie,
            mfaRequired,
            cspReportOnly,
            publicRangesAllowed,
            demoSeedEnabled
        );
    }

    private boolean hasConsoleNotifier(ConfigurableListableBeanFactory beanFactory) {
        return Arrays.stream(beanFactory.getBeanDefinitionNames())
            .anyMatch(name -> {
                String beanClassName = beanFactory.getBeanDefinition(name).getBeanClassName();
                return name.toLowerCase().contains("consolenotifier")
                    || (beanClassName != null
                        && (beanClassName.endsWith(".ConsoleNotifier")
                            || beanClassName.endsWith("$ConsoleNotifier")));
            });
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isHttpsPublicBaseUrl(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return uri.isAbsolute()
                && "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && uri.getUserInfo() == null
                && uri.getQuery() == null
                && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isValidEmailAddress(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            jakarta.mail.internet.InternetAddress address =
                new jakarta.mail.internet.InternetAddress(value, true);
            address.validate();
            return address.getAddress().equals(value);
        } catch (jakarta.mail.internet.AddressException exception) {
            return false;
        }
    }
}

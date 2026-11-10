package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexora.test.IsolatedMySqlTestDatabase;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "hexora.network.discovery-enabled=false",
        "tinyhat.security.mfa.required=false"
    })
class PhaseFiveSessionHttpIntegrationTest extends IsolatedMySqlTestDatabase {

    private static final Pattern CSRF_META = Pattern.compile(
        "<meta name=\"_csrf\" content=\"([^\"]+)\"");
    private static final Pattern CSRF_HEADER_META = Pattern.compile(
        "<meta name=\"_csrf_header\" content=\"([^\"]+)\"");

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String email;

    @BeforeEach
    void createAuditor() {
        email = "phase5-" + UUID.randomUUID() + "@example.test";
        jdbcTemplate.update(
            "INSERT INTO usuario "
                + "(public_id, cuenta_id, rol, estado, email_normalizado, password_hash) "
                + "VALUES (?, 1, 'AUDITOR', 'ACTIVO', ?, ?)",
            UUID.randomUUID().toString(),
            email,
            passwordEncoder.encode("phase5-test-password"));
    }

    @Test
    void sessionCookieIsSecureAndLoginRotatesTheSessionIdWithoutUrlRewriting() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        URI loginUri = URI.create("http://127.0.0.1:" + port + "/login");
        HttpResponse<String> loginPage = client.send(
            HttpRequest.newBuilder(loginUri).GET().build(),
            HttpResponse.BodyHandlers.ofString());

        assertThat(loginPage.statusCode()).isEqualTo(200);
        assertThat(loginPage.body()).doesNotContain("jsessionid");
        String initialCookie = cookieValue(loginPage, "TINYHAT_SID");
        String csrfToken = metaValue(CSRF_META, loginPage.body());
        String csrfHeader = metaValue(CSRF_HEADER_META, loginPage.body());
        assertSecureSessionCookie(loginPage);

        String form = "email=" + encode(email)
            + "&password=" + encode("phase5-test-password")
            + "&_csrf=" + encode(csrfToken);
        HttpResponse<String> loginResponse = client.send(
            HttpRequest.newBuilder(loginUri)
                .header("Cookie", initialCookie)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header(csrfHeader, csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build(),
            HttpResponse.BodyHandlers.ofString());

        assertThat(loginResponse.statusCode()).isEqualTo(302);
        assertThat(loginResponse.headers().firstValue("Location").orElse(""))
            .doesNotContain("jsessionid");
        assertSecureSessionCookie(loginResponse);
        assertThat(cookieValue(loginResponse, "TINYHAT_SID")).isNotEqualTo(initialCookie);
    }

    private void assertSecureSessionCookie(HttpResponse<?> response) {
        String setCookie = response.headers().allValues("Set-Cookie").stream()
            .filter(value -> value.startsWith("TINYHAT_SID="))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Session cookie was not set"));
        assertThat(setCookie)
            .contains("HttpOnly", "Secure", "SameSite=Strict")
            .doesNotContain("jsessionid");
    }

    private String cookieValue(HttpResponse<?> response, String name) {
        String setCookie = response.headers().allValues("Set-Cookie").stream()
            .filter(value -> value.startsWith(name + "="))
            .findFirst()
            .orElseThrow(() -> new AssertionError(name + " cookie was not set"));
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private String metaValue(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) {
            throw new AssertionError("CSRF metadata not found in login page");
        }
        return matcher.group(1);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

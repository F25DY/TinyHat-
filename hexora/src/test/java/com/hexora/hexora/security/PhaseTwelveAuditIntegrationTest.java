package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hexora.hexora.model.Usuario;
import com.hexora.hexora.service.Capability;
import com.hexora.hexora.service.CapabilityService;
import com.hexora.test.IsolatedMySqlTestDatabase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.hexora.hexora.repository.UsuarioRepository;

/**
 * Phase 12, F12-CA1/CA2/CA4: every required audit event is written with user, account, IP and correlation
 * id (when they apply), log files never contain credentials, and the enumeration threshold warns.
 */
@SpringBootTest(properties = {
    "hexora.network.discovery-enabled=false",
    "tinyhat.security.audit.enumeration-threshold=3",
    "tinyhat.security.ratelimit.auth.capacity=1000",
    "tinyhat.security.ratelimit.auth.refill-tokens=1000",
    "tinyhat.security.ratelimit.mfa.capacity=1000",
    "tinyhat.security.ratelimit.mfa.refill-tokens=1000",
    "tinyhat.security.ratelimit.registro.capacity=1000",
    "tinyhat.security.ratelimit.registro.refill-tokens=1000",
    "tinyhat.security.ratelimit.token.capacity=1000",
    "tinyhat.security.ratelimit.token.refill-tokens=1000",
    "tinyhat.security.ratelimit.api-general.capacity=100000",
    "tinyhat.security.ratelimit.api-general.refill-tokens=100000"
})
@AutoConfigureMockMvc
class PhaseTwelveAuditIntegrationTest extends IsolatedMySqlTestDatabase {

    private static final String PASSWORD = "Tinyhat-Fase12-Clave-2026";
    private static final Path LOG_FILE = Path.of("target", "surefire-logs", "tinyhat.log");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TotpService totpService;
    @Autowired private Clock clock;
    @Autowired private SessionRegistry sessionRegistry;
    @Autowired private SecurityTokenService tokenService;
    @Autowired private OwnerOpsService ownerOps;
    @Autowired private CapabilityService capabilityService;
    @Autowired private UsuarioRepository usuarioRepository;

    // ---------------------------------------------------------------------------------------------
    // Authentication and session events
    // ---------------------------------------------------------------------------------------------

    @Test
    void loginSuccessLoginFailureAndLogoutAreAudited() throws Exception {
        TestUser user = createUser("AUDITOR");
        String ip = randomIp();

        mockMvc.perform(post("/login").with(from(ip)).with(csrf())
                .param("email", user.email()).param("password", "incorrecta-" + UUID.randomUUID()))
            .andExpect(redirectedUrl("/login?error"));
        assertAttributed(row("LOGIN_FAIL", user), user, ip);

        MockHttpSession session = login(user, ip);
        assertAttributed(row("LOGIN_OK", user), user, ip);

        mockMvc.perform(post("/logout").session(session).with(from(ip)).with(csrf()))
            .andExpect(status().is3xxRedirection());
        assertAttributed(row("LOGOUT", user), user, ip);
    }

    @Test
    void mfaEnrollmentSuccessFailureReauthenticationAndResetAreAudited() throws Exception {
        TestUser user = createUser("AUDITOR");
        String ip = randomIp();

        MockHttpSession setup = login(user, ip);
        MvcResult enrollment = mockMvc.perform(post("/mfa/enroll/start").session(setup).with(from(ip)).with(csrf()))
            .andExpect(status().isOk()).andReturn();
        String secret = (String) enrollment.getModelAndView().getModel().get("secret");
        long step = Math.floorDiv(Instant.now().getEpochSecond(), 30);
        mockMvc.perform(post("/mfa/enroll/confirm").session(setup).with(from(ip)).with(csrf())
                .param("code", totpService.codeForStep(secret, step - 1)))
            .andExpect(status().isOk());
        assertAttributed(row("MFA_ENROLLED", user), user, ip);

        MockHttpSession session = login(user, ip);
        mockMvc.perform(post("/mfa/verify").session(session).with(from(ip)).with(csrf()).param("code", "000000"))
            .andExpect(status().isOk());
        Map<String, Object> failure = row("MFA_FAIL", user);
        assertAttributed(failure, user, ip);
        assertThat(detail(failure)).contains("\"context\": \"VERIFY\"");

        mockMvc.perform(post("/mfa/verify").session(session).with(from(ip)).with(csrf())
                .param("code", totpService.codeForStep(secret, step)))
            .andExpect(redirectedUrl("/dashboard"));
        assertAttributed(row("MFA_OK", user), user, ip);

        session.setAttribute(MfaAuthenticationService.LAST_REAUTH_ATTRIBUTE,
            Instant.now().minusSeconds(6 * 60L).toEpochMilli());
        mockMvc.perform(post("/mfa/reauth").session(session).with(from(ip)).with(csrf())
                .param("password", PASSWORD).param("code", totpService.codeForStep(secret, step + 1)))
            .andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForList(
            "SELECT detalle FROM audit_event WHERE evento = 'MFA_OK' AND usuario_id = ?", String.class, user.id()))
            .anyMatch(detail -> detail.contains("REAUTH"));

        mockMvc.perform(post("/mfa/reset").session(session).with(from(ip)).with(csrf()))
            .andExpect(redirectedUrl("/login?mfa=reset"));
        assertAttributed(row("MFA_RESET", user), user, ip);
    }

    @Test
    void loginLockoutIsThrottledAndBucketExhaustionIsRateLimited() throws Exception {
        TestUser user = createUser("AUDITOR");
        String lockedIp = randomIp();
        for (int attempt = 1; attempt <= 6; attempt++) {
            mockMvc.perform(post("/login").with(from(lockedIp)).with(csrf())
                    .param("email", user.email()).param("password", "mala-" + attempt))
                .andExpect(redirectedUrl("/login?error"));
        }
        Map<String, Object> throttled = jdbc.queryForMap(
            "SELECT * FROM audit_event WHERE evento = 'THROTTLED' AND ip = ? ORDER BY id DESC LIMIT 1", lockedIp);
        assertThat(throttled.get("correlation_id")).isNotNull();
        assertThat(((Number) throttled.get("cuenta_id")).longValue()).isPositive();
        assertThat(detail(throttled)).contains("retryAfterSeconds");

        String limitedIp = randomIp();
        int rejected = 0;
        for (int request = 1; request <= 12; request++) {
            MvcResult result = mockMvc.perform(get("/api/cves/buscar").param("q", "CVE-2021-44228")
                .with(from(limitedIp))).andReturn();
            rejected += result.getResponse().getStatus() == 429 ? 1 : 0;
        }
        assertThat(rejected).isPositive();
        Map<String, Object> limited = jdbc.queryForMap(
            "SELECT * FROM audit_event WHERE evento = 'RATE_LIMITED' AND ip = ? ORDER BY id DESC LIMIT 1", limitedIp);
        assertThat(limited.get("correlation_id")).isNotNull();
        assertThat(detail(limited)).contains("cve-search");
    }

    @Test
    void deniedAccessScopedNotFoundAndEnumerationWarningAreRecorded() throws Exception {
        TestUser client = createUser("CLIENTE_ADMIN");
        String ip = randomIp();
        mockMvc.perform(get("/dashboard").session(manualSession(client, 0)).with(from(ip)))
            .andExpect(status().isForbidden());
        assertAttributed(row("ACCESS_DENIED", client), client, ip);

        TestUser auditor = createUser("AUDITOR");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ch.qos.logback.classic.Logger detectorLogger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(EnumerationDetector.class);
        detectorLogger.addAppender(appender);
        try {
            MockHttpSession session = manualSession(auditor, 0);
            for (int attempt = 0; attempt < 5; attempt++) {
                mockMvc.perform(get("/proyectos/" + UUID.randomUUID()).session(session).with(from(ip)))
                    .andExpect(status().isNotFound());
            }
        } finally {
            detectorLogger.detachAppender(appender);
        }
        assertAttributed(row("NOT_FOUND_SCOPED", auditor), auditor, ip);
        assertThat(appender.list).filteredOn(event -> event.getLevel() == Level.WARN)
            .singleElement()
            .satisfies(event -> assertThat(event.getFormattedMessage())
                .contains("Possible resource enumeration", "userId=" + auditor.id(), "threshold=3"));
    }

    @Test
    void replacedAndExpiredSessionsAreAudited() throws Exception {
        TestUser user = createUser("AUDITOR");
        String ip = randomIp();
        MockHttpSession first = login(user, ip);
        login(user, ip);

        mockMvc.perform(get("/dashboard").session(first).with(from(ip)))
            .andExpect(redirectedUrl("/login?replaced"));
        assertAttributed(row("SESSION_REPLACED", user), user, ip);

        TestUser idle = createUser("AUDITOR");
        mockMvc.perform(get("/dashboard").session(manualSession(idle, -10 * 60_000L)).with(from(ip)))
            .andExpect(redirectedUrl("/login?expired"));
        Map<String, Object> expired = row("SESSION_EXPIRED", idle);
        assertAttributed(expired, idle, ip);
        assertThat(detail(expired)).contains("IDLE_TIMEOUT");
    }

    @Test
    void suspensionAndRoleChangesAreDetectedOnLiveSessions() throws Exception {
        String ip = randomIp();
        TestUser suspended = createUser("AUDITOR");
        MockHttpSession liveSession = manualSession(suspended, 0);
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDO' WHERE id = ?", suspended.id());
        mockMvc.perform(get("/proyectos").session(liveSession).with(from(ip)))
            .andExpect(redirectedUrl("/login?expired"));
        Map<String, Object> suspension = row("USER_SUSPENDED", suspended);
        assertAttributed(suspension, suspended, ip);
        assertThat(detail(suspension)).contains("SESSION_CHECK");

        TestUser promoted = createUser("AUDITOR");
        MockHttpSession promotedSession = manualSession(promoted, 0);
        Long organizationId = jdbc.queryForObject(
            "SELECT id_empresa FROM organizacion_cliente WHERE cuenta_id = 1 LIMIT 1", Long.class);
        jdbc.update("UPDATE usuario SET rol = 'CLIENTE_ADMIN', organizacion_id = ? WHERE id = ?",
            organizationId, promoted.id());
        mockMvc.perform(get("/proyectos").session(promotedSession).with(from(ip)))
            .andExpect(redirectedUrl("/login?expired"));
        Map<String, Object> roleChange = row("ROLE_CHANGED", promoted);
        assertAttributed(roleChange, promoted, ip);
        assertThat(detail(roleChange)).contains("\"previousRole\": \"AUDITOR\"", "\"newRole\": \"CLIENTE_ADMIN\"");
    }

    // ---------------------------------------------------------------------------------------------
    // Account lifecycle events
    // ---------------------------------------------------------------------------------------------

    @Test
    void registrationVerificationRecoveryAndInvitationFlowsAreAudited() throws Exception {
        String ip = randomIp();
        String email = "registro-" + UUID.randomUUID() + "@example.test";
        mockMvc.perform(post("/registro").with(from(ip)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Auditor de prueba\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk());
        Usuario registered = usuarioRepository.findByEmailNormalizado(email).orElseThrow();
        TestUser registeredUser = new TestUser(email, registered.getId(), registered.getPublicId(),
            registered.getCuentaId(), new TinyHatPrincipal(registered, true));
        assertAttributed(row("USER_CREATED", registeredUser), registeredUser, ip);

        mockMvc.perform(get("/verificar/" + tokenService.createVerificationToken(registered).rawToken()).with(from(ip)))
            .andExpect(status().is3xxRedirection());
        assertAttributed(row("USER_VERIFIED", registeredUser), registeredUser, ip);

        String newPassword = "Otra-Clave-Larga-2026-Fase12";
        mockMvc.perform(post("/recuperar/" + tokenService.createPasswordResetToken(registered).rawToken())
                .with(from(ip)).with(csrf()).param("password", newPassword))
            .andExpect(status().is3xxRedirection());
        assertAttributed(row("PASSWORD_RESET", registeredUser), registeredUser, ip);

        TestUser auditor = createUser("AUDITOR");
        Map<String, Object> organization = jdbc.queryForMap(
            "SELECT id_empresa, public_id FROM organizacion_cliente WHERE cuenta_id = 1 LIMIT 1");
        String invitedEmail = "invitado-" + UUID.randomUUID() + "@example.test";
        mockMvc.perform(post("/api/organizaciones/" + organization.get("public_id") + "/invitaciones")
                .session(manualSession(auditor, 0)).with(from(ip)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + invitedEmail + "\"}"))
            .andExpect(status().isCreated());
        assertAttributed(row("USER_INVITED", auditor), auditor, ip);

        Usuario invited = usuarioRepository.findByEmailNormalizado(invitedEmail).orElseThrow();
        String invitationToken = tokenService.createInvitationToken(
            invited, ((Number) organization.get("id_empresa")).longValue(), invited.getCuentaId()).rawToken();
        mockMvc.perform(post("/invitacion/" + invitationToken).with(from(ip)).with(csrf())
                .param("password", newPassword))
            .andExpect(status().is3xxRedirection());
        TestUser invitedUser = new TestUser(invitedEmail, invited.getId(), invited.getPublicId(),
            invited.getCuentaId(), new TinyHatPrincipal(invited, true));
        Map<String, Object> passwordChanged = row("PASSWORD_CHANGED", invitedUser);
        assertAttributed(passwordChanged, invitedUser, ip);
        assertThat(detail(passwordChanged)).contains("INVITATION_ACCEPTED");
    }

    @Test
    void ownerCommandsAreAuditedAsUserStateCapabilityAndOperationEvents() {
        TestUser user = createUser("AUDITOR");
        String accountPublicId = jdbc.queryForObject("SELECT public_id FROM cuenta WHERE id = 1", String.class);

        assertThat(ownerOps.suspendUsuario(user.publicId())).isTrue();
        Map<String, Object> suspended = row("USER_SUSPENDED", user);
        assertThat(((Number) suspended.get("cuenta_id")).longValue()).isEqualTo(1L);
        assertThat(detail(suspended)).contains("OPS_CLI");
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE evento = 'OPS_COMMAND' AND JSON_UNQUOTE(JSON_EXTRACT(detalle, '$.target')) = ?",
            Integer.class, user.publicId())).isEqualTo(1);

        assertThat(ownerOps.reactivateUsuario(user.publicId())).isTrue();
        assertThat(row("USER_REACTIVATED", user)).isNotNull();
        assertThat(ownerOps.reactivateUsuario(user.publicId())).as("an active user cannot be 'reactivated'").isFalse();

        try {
            assertThat(ownerOps.setCapability(accountPublicId, "informes_pdf", false)).isTrue();
            Map<String, Object> capability = jdbc.queryForMap(
                "SELECT * FROM audit_event WHERE evento = 'CAPABILITY_CHANGED' ORDER BY id DESC LIMIT 1");
            assertThat(((Number) capability.get("cuenta_id")).longValue()).isEqualTo(1L);
            assertThat(detail(capability)).contains("informes_pdf", "\"enabled\": false");
            assertThat(capabilityService.isEnabled(1, Capability.INFORMES_PDF)).isFalse();
        } finally {
            ownerOps.setCapability(accountPublicId, "informes_pdf", true);
        }
        assertThat(capabilityService.isEnabled(1, Capability.INFORMES_PDF)).isTrue();
    }

    @Test
    void startupConfigurationIsRecordedWithoutSecrets() {
        List<String> details = jdbc.queryForList(
            "SELECT CAST(detalle AS CHAR) FROM audit_event WHERE evento = 'STARTUP_CONFIG'", String.class);
        assertThat(details).isNotEmpty();
        assertThat(details.get(0)).contains("profiles", "https", "mfaRequired", "cspReportOnly", "separateFlywayUser");
        assertThat(details.get(0).toLowerCase()).doesNotContain("password", "secret", "token", "jdbc:", "tinyhat_mfa");
    }

    // ---------------------------------------------------------------------------------------------
    // F12-CA2: log hygiene
    // ---------------------------------------------------------------------------------------------

    @Test
    void logFilesNeverContainPasswordsTotpCodesSessionIdsOrTokensAfterACompleteLoginFlow() throws Exception {
        TestUser user = createUser("AUDITOR");
        String ip = randomIp();
        List<String> secrets = new ArrayList<>();
        secrets.add(PASSWORD);
        long logOffset = Files.exists(LOG_FILE) ? Files.size(LOG_FILE) : 0L;
        // Mock session ids are tiny counters; a real container id is long and random, so the canary uses one.
        String realisticSessionId = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        secrets.add(realisticSessionId);

        MockHttpSession session = login(user, ip);
        MvcResult enrollment = mockMvc.perform(post("/mfa/enroll/start").session(session).with(from(ip)).with(csrf()))
            .andReturn();
        String secret = (String) enrollment.getModelAndView().getModel().get("secret");
        String totp = totpService.codeForStep(secret, Math.floorDiv(Instant.now().getEpochSecond(), 30));
        secrets.add(secret);
        MvcResult confirmation = mockMvc.perform(post("/mfa/enroll/confirm").session(session).with(from(ip))
            .with(csrf()).param("code", totp)).andReturn();
        @SuppressWarnings("unchecked")
        List<String> recoveryCodes = (List<String>) confirmation.getModelAndView().getModel().get("recoveryCodes");
        secrets.addAll(recoveryCodes);

        String resetToken = tokenService.createPasswordResetToken(
            usuarioRepository.findByEmailNormalizado(user.email()).orElseThrow()).rawToken();
        secrets.add(resetToken);

        // Canary: a careless log statement must still come out masked through the real logback configuration.
        String canaryPassword = "canary-" + UUID.randomUUID();
        org.slf4j.Logger canary = LoggerFactory.getLogger("com.hexora.hexora.security.LogHygieneCanary");
        canary.info("probe password={} Authorization: Bearer abc.def.ghi Cookie: TINYHAT_SID={} link=/recuperar/{} code={}",
            canaryPassword, realisticSessionId, resetToken, totp);
        // The console notifier prints one-time links by design, but only to the console, never to the file.
        new ConsoleNotifier().sendPasswordResetEmail("nadie@example.test", "https://localhost:8443/recuperar/" + resetToken);
        secrets.add(canaryPassword);
        secrets.add(totp);

        byte[] allBytes = Files.exists(LOG_FILE) ? Files.readAllBytes(LOG_FILE) : new byte[0];
        String logContent = new String(allBytes, (int) Math.min(logOffset, allBytes.length),
            allBytes.length - (int) Math.min(logOffset, allBytes.length), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(logContent).as("the log file must exist and contain the canary line").contains("probe password=***");
        for (String secretValue : secrets) {
            assertThat(logContent).as("log must not contain a credential of %d chars", secretValue.length())
                .doesNotContain(secretValue);
        }
        assertThat(logContent).doesNotContain("[CONSOLE NOTIFIER]");
    }

    // ---------------------------------------------------------------------------------------------
    // Catalogue versus emitters
    // ---------------------------------------------------------------------------------------------

    @Test
    void everyRequiredEventHasAnEmitterAndReservedEventsHaveNone() throws IOException {
        String sources;
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            StringBuilder all = new StringBuilder();
            for (Path file : files.filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.endsWith("AuditEventType.java")).toList()) {
                all.append(Files.readString(file)).append('\n');
            }
            sources = all.toString();
        }
        for (AuditEventType type : AuditEventType.REQUIRED_BY_PLAN) {
            boolean emitted = sources.contains("\"" + type.name() + "\"") || sources.contains("AuditEventType." + type.name());
            if (AuditEventType.RESERVED.contains(type)) {
                assertThat(emitted).as("%s is reserved and must have no emitter in v1", type).isFalse();
            } else {
                assertThat(emitted).as("%s must have an emitter in the application code", type).isTrue();
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private record TestUser(String email, long id, String publicId, long accountId, TinyHatPrincipal principal) {
    }

    private TestUser createUser(String role) {
        String email = "fase12-" + UUID.randomUUID() + "@example.test";
        Long organizationId = "CLIENTE_ADMIN".equals(role)
            ? jdbc.queryForObject("SELECT id_empresa FROM organizacion_cliente WHERE cuenta_id = 1 LIMIT 1", Long.class)
            : null;
        jdbc.update("INSERT INTO usuario (public_id, cuenta_id, organizacion_id, rol, estado, email_normalizado, "
                + "password_hash) VALUES (?, 1, ?, ?, 'ACTIVO', ?, ?)",
            UUID.randomUUID().toString(), organizationId, role, email, passwordEncoder.encode(PASSWORD));
        Usuario user = usuarioRepository.findByEmailNormalizado(email).orElseThrow();
        return new TestUser(email, user.getId(), user.getPublicId(), 1L, new TinyHatPrincipal(user, true));
    }

    private MockHttpSession login(TestUser user, String ip) throws Exception {
        MvcResult result = mockMvc.perform(post("/login").with(from(ip)).with(csrf())
                .param("email", user.email()).param("password", PASSWORD))
            .andExpect(status().isFound())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession manualSession(TestUser user, long lastActivityOffsetMillis) {
        MockHttpSession session = new MockHttpSession();
        long now = clock.millis();
        session.setAttribute(SessionActivityFilter.CREATED_AT_ATTRIBUTE, now + Math.min(0, lastActivityOffsetMillis));
        session.setAttribute(SessionActivityFilter.ORIGINAL_CREATED_AT_ATTRIBUTE,
            now + Math.min(0, lastActivityOffsetMillis));
        session.setAttribute(SessionActivityFilter.LAST_ACTIVITY_ATTRIBUTE, now + lastActivityOffsetMillis);
        session.setAttribute(MfaAuthenticationService.LAST_REAUTH_ATTRIBUTE, now);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            user.principal(), null, user.principal().getAuthorities()));
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
        sessionRegistry.registerNewSession(session.getId(), user.principal());
        return session;
    }

    private Map<String, Object> row(String event, TestUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, evento, usuario_id, cuenta_id, ip, correlation_id, CAST(detalle AS CHAR) AS detalle_texto "
                + "FROM audit_event WHERE evento = ? AND usuario_id = ? ORDER BY id DESC", event, user.id());
        assertThat(rows).as("audit rows for %s of user %d", event, user.id()).isNotEmpty();
        return rows.get(0);
    }

    private void assertAttributed(Map<String, Object> row, TestUser user, String ip) {
        assertThat(((Number) row.get("usuario_id")).longValue()).isEqualTo(user.id());
        assertThat(((Number) row.get("cuenta_id")).longValue()).isEqualTo(user.accountId());
        assertThat(row.get("ip")).isEqualTo(ip);
        assertThat(row.get("correlation_id")).as("correlation id of %s", row.get("evento")).isNotNull();
    }

    private static String detail(Map<String, Object> row) {
        Object text = row.containsKey("detalle_texto") ? row.get("detalle_texto") : row.get("detalle");
        return text == null ? "" : text.toString();
    }

    private static String randomIp() {
        return "198.51.100." + (1 + Math.abs(UUID.randomUUID().hashCode() % 250));
    }

    private static RequestPostProcessor from(String ip) {
        return (MockHttpServletRequest request) -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}

package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hexora.test.IsolatedMySqlTestDatabase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
    "hexora.network.discovery-enabled=false",
    "tinyhat.security.ratelimit.auth.capacity=100",
    "tinyhat.security.ratelimit.auth.refill-tokens=100",
    "tinyhat.security.ratelimit.mfa.capacity=100",
    "tinyhat.security.ratelimit.mfa.refill-tokens=100"
})
@AutoConfigureMockMvc
class MfaFlowIntegrationTest extends IsolatedMySqlTestDatabase {

    private static final String PASSWORD = "mfa-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpService totpService;

    private String email;
    private Long userId;
    private String clientIp;

    @BeforeEach
    void createAuditor() {
        email = "mfa-" + UUID.randomUUID() + "@example.test";
        clientIp = "198.51.100." + (1 + Math.abs(UUID.randomUUID().hashCode() % 250));
        jdbcTemplate.update(
            "INSERT INTO usuario "
                + "(public_id, cuenta_id, rol, estado, email_normalizado, password_hash) "
                + "VALUES (?, 1, 'AUDITOR', 'ACTIVO', ?, ?)",
            UUID.randomUUID().toString(),
            email,
            passwordEncoder.encode(PASSWORD)
        );
        userId = jdbcTemplate.queryForObject(
            "SELECT id FROM usuario WHERE email_normalizado = ?",
            Long.class,
            email
        );
    }

    @Test
    void enrollmentEncryptsTheSecretGatesBusinessRoutesAndRotatesSessionAfterTotp() throws Exception {
        MockHttpSession session = login();
        String oldSessionId = session.getId();

        mockMvc.perform(get("/api/network/status").session(session).with(fromClientIp()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("MFA_REQUIRED"));
        mockMvc.perform(get("/dashboard").session(session).with(fromClientIp()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/mfa/enroll"));

        MvcResult enrollment = mockMvc.perform(post("/mfa/enroll/start")
                .session(session)
                .with(fromClientIp())
                .with(csrf()))
            .andExpect(status().isOk())
            .andReturn();
        String secret = (String) enrollment.getModelAndView().getModel().get("secret");
        assertThat(secret).isNotBlank();
        assertThat((String) enrollment.getModelAndView().getModel().get("provisioningUri"))
            .startsWith("otpauth://totp/")
            .contains("algorithm=SHA1", "digits=6", "period=30");

        byte[] ciphertext = jdbcTemplate.queryForObject(
            "SELECT secreto_cifrado FROM mfa_credencial WHERE usuario_id = ?",
            byte[].class,
            userId
        );
        byte[] nonce = jdbcTemplate.queryForObject(
            "SELECT nonce FROM mfa_credencial WHERE usuario_id = ?",
            byte[].class,
            userId
        );
        assertThat(ciphertext).hasSize(36);
        assertThat(nonce).hasSize(12);
        assertThat(new String(ciphertext, java.nio.charset.StandardCharsets.UTF_8)).doesNotContain(secret);

        long step = Math.floorDiv(Instant.now().getEpochSecond(), 30);
        MvcResult confirmation = mockMvc.perform(post("/mfa/enroll/confirm")
                .session(session)
                .with(fromClientIp())
                .with(csrf())
                .param("code", totpService.codeForStep(secret, step)))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(session.getId()).isNotEqualTo(oldSessionId);
        assertThat((Boolean) jdbcTemplate.queryForObject(
            "SELECT activo FROM mfa_credencial WHERE usuario_id = ?",
            Boolean.class,
            userId
        )).isTrue();
        mockMvc.perform(post("/mfa/reauth")
                .session(session)
                .with(fromClientIp())
                .with(csrf())
                .param("password", PASSWORD)
                .param("code", totpService.codeForStep(secret, step)))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("mfa/reauth"));
        @SuppressWarnings("unchecked")
        List<String> recoveryCodes = (List<String>) confirmation.getModelAndView().getModel().get("recoveryCodes");
        assertThat(recoveryCodes).hasSize(10);
        String firstCodeHash = jdbcTemplate.queryForObject(
            "SELECT hash FROM mfa_codigo_recuperacion WHERE usuario_id = ? ORDER BY id LIMIT 1",
            String.class,
            userId
        );
        assertThat(firstCodeHash).doesNotContain(recoveryCodes.getFirst());
        assertThat(passwordEncoder.matches(recoveryCodes.getFirst().replace("-", ""), firstCodeHash)).isTrue();

        mockMvc.perform(get("/dashboard").session(session).with(fromClientIp()))
            .andExpect(status().isOk());
    }

    @Test
    void recoveryCodesCanOnlyBeUsedOnceAndFiveInvalidTotpAttemptsEndTheSession() throws Exception {
        List<String> recoveryCodes = enrollAndGetRecoveryCodes();
        String recoveryCode = recoveryCodes.getFirst();

        MockHttpSession firstMfaSession = login();
        mockMvc.perform(post("/mfa/verify")
                .session(firstMfaSession)
                .with(fromClientIp())
                .with(csrf())
                .param("code", recoveryCode))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/dashboard"));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT usado_en IS NOT NULL FROM mfa_codigo_recuperacion "
                + "WHERE usuario_id = ? ORDER BY id LIMIT 1",
            Boolean.class,
            userId
        )).isTrue();

        mockMvc.perform(post("/logout").session(firstMfaSession).with(fromClientIp()).with(csrf()))
            .andExpect(status().is3xxRedirection());

        MockHttpSession replaySession = login();
        mockMvc.perform(post("/mfa/verify")
                .session(replaySession)
                .with(fromClientIp())
                .with(csrf())
                .param("code", recoveryCode))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name("mfa/verify"));

        mockMvc.perform(post("/logout").session(replaySession).with(fromClientIp()).with(csrf()))
            .andExpect(status().is3xxRedirection());
        MockHttpSession lockedSession = login();
        for (int attempt = 1; attempt <= 5; attempt++) {
            var result = mockMvc.perform(post("/mfa/verify")
                    .session(lockedSession)
                    .with(fromClientIp())
                    .with(csrf())
                    .param("code", "invalid"))
                .andReturn();
            if (attempt < 5) {
                assertThat(result.getResponse().getStatus()).isEqualTo(200);
            } else {
                assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/login?mfa=locked");
            }
        }
        assertThat(lockedSession.isInvalid()).isTrue();
    }

    @Test
    void criticalMfaResetRequiresRecentReauthenticationAndIsAudited() throws Exception {
        MockHttpSession setupSession = login();
        MvcResult enrollment = mockMvc.perform(post("/mfa/enroll/start")
                .session(setupSession)
                .with(fromClientIp())
                .with(csrf()))
            .andExpect(status().isOk())
            .andReturn();
        String secret = (String) enrollment.getModelAndView().getModel().get("secret");
        long step = Math.floorDiv(Instant.now().getEpochSecond(), 30);
        mockMvc.perform(post("/mfa/enroll/confirm")
                .session(setupSession)
                .with(fromClientIp())
                .with(csrf())
                .param("code", totpService.codeForStep(secret, step - 1)))
            .andExpect(status().isOk());

        MockHttpSession session = login();
        mockMvc.perform(post("/mfa/verify")
                .session(session)
                .with(fromClientIp())
                .with(csrf())
                .param("code", totpService.codeForStep(secret, step)))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/dashboard"));
        session.setAttribute(
            MfaAuthenticationService.LAST_REAUTH_ATTRIBUTE,
            Instant.now().minusSeconds(6 * 60L).toEpochMilli()
        );
        mockMvc.perform(get("/mfa/reset").session(session).with(fromClientIp()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/mfa/reauth?required"));
        mockMvc.perform(post("/mfa/reauth")
                .session(session)
                .with(fromClientIp())
                .with(csrf())
                .param("password", PASSWORD)
                .param("code", totpService.codeForStep(secret, step + 1)))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/mfa/reset"));

        mockMvc.perform(post("/mfa/reset").session(session).with(fromClientIp()).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?mfa=reset"));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mfa_credencial WHERE usuario_id = ?",
            Integer.class,
            userId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE evento = 'MFA_RESET' AND usuario_id = ?",
            Integer.class,
            userId
        )).isEqualTo(1);
        assertThat(session.isInvalid()).isTrue();
    }

    private List<String> enrollAndGetRecoveryCodes() throws Exception {
        MockHttpSession session = login();
        MvcResult enrollment = mockMvc.perform(post("/mfa/enroll/start")
                .session(session)
                .with(fromClientIp())
                .with(csrf()))
            .andExpect(status().isOk())
            .andReturn();
        String secret = (String) enrollment.getModelAndView().getModel().get("secret");
        String code = totpService.codeForStep(secret, Math.floorDiv(Instant.now().getEpochSecond(), 30));
        MvcResult confirmation = mockMvc.perform(post("/mfa/enroll/confirm")
                .session(session)
                .with(fromClientIp())
                .with(csrf())
                .param("code", code))
            .andExpect(status().isOk())
            .andReturn();
        @SuppressWarnings("unchecked")
        List<String> recoveryCodes = (List<String>) confirmation.getModelAndView().getModel().get("recoveryCodes");
        return recoveryCodes;
    }

    private MockHttpSession login() throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                .with(fromClientIp())
                .with(csrf())
                .param("email", email)
                .param("password", PASSWORD))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/dashboard"))
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private RequestPostProcessor fromClientIp() {
        return (MockHttpServletRequest request) -> {
            request.setRemoteAddr(clientIp);
            return request;
        };
    }
}

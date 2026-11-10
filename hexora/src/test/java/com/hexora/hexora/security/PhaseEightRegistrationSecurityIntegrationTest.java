package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hexora.hexora.model.Cuenta;
import com.hexora.hexora.model.Empresa;
import com.hexora.hexora.model.TokenSeguridad;
import com.hexora.hexora.model.Usuario;
import com.hexora.hexora.repository.CuentaRepository;
import com.hexora.hexora.repository.EmpresaRepository;
import com.hexora.hexora.repository.TokenSeguridadRepository;
import com.hexora.hexora.repository.UsuarioRepository;
import com.hexora.test.IsolatedMySqlTestDatabase;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "hexora.network.discovery-enabled=false",
    "tinyhat.security.mfa.required=false",
    "tinyhat.security.ratelimit.auth.capacity=100",
    "tinyhat.security.ratelimit.auth.refill-tokens=100",
    "tinyhat.security.ratelimit.registro.capacity=100",
    "tinyhat.security.ratelimit.registro.refill-tokens=100",
    "tinyhat.security.ratelimit.token.capacity=100",
    "tinyhat.security.ratelimit.token.refill-tokens=100"
})
@AutoConfigureMockMvc
class PhaseEightRegistrationSecurityIntegrationTest extends IsolatedMySqlTestDatabase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CuentaRepository cuentaRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private TokenSeguridadRepository tokenRepository;

    @Autowired
    private SecurityTokenService tokenService;

    @Autowired
    private OwnerOpsService ownerOpsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SessionRegistry sessionRegistry;

    @Test
    void f8ca1_registrationIgnoresSubmittedRoleAndAccountId() throws Exception {
        String email = "auditor-ca1-" + UUID.randomUUID() + "@example.com";
        String payload = objectMapper.writeValueAsString(Map.of(
            "nombre", "Auditor Independiente",
            "email", email,
            "password", "VeryStrongPassword123!",
            "rol", "ADMIN",
            "cuentaId", 99999
        ));

        mockMvc.perform(post("/registro")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());

        Usuario user = usuarioRepository.findByEmailNormalizado(email).orElseThrow();
        assertThat(user.getRol()).isEqualTo("AUDITOR");
        assertThat(user.getEstado()).isEqualTo("PENDIENTE_VERIFICACION");
        assertThat(user.getCuentaId()).isNotEqualTo(99999L);

        Cuenta cuenta = cuentaRepository.findById(user.getCuentaId()).orElseThrow();
        assertThat(cuenta.getTipo()).isEqualTo("AUDITOR");
        assertThat(cuenta.getEstado()).isEqualTo("ACTIVA");
    }

    @Test
    void f8ca2_registrationResponseIsIdenticalForNewAndExistingEmail() throws Exception {
        String email = "duplicate-test-" + UUID.randomUUID() + "@example.com";
        String payload = objectMapper.writeValueAsString(Map.of(
            "nombre", "Auditor Unico",
            "email", email,
            "password", "VeryStrongPassword123!"
        ));

        MvcResult firstResult = mockMvc.perform(post("/registro")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andReturn();

        MvcResult secondResult = mockMvc.perform(post("/registro")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(firstResult.getResponse().getContentAsString())
            .isEqualTo(secondResult.getResponse().getContentAsString());
    }

    @Test
    void f8ca5_unverifiedUserCannotLoginUntilVerified() throws Exception {
        String email = "unverified-" + UUID.randomUUID() + "@example.com";
        String password = "SecureAuditorPassword123!";

        mockMvc.perform(post("/registro")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "nombre", "Auditor Pendiente",
                    "email", email,
                    "password", password
                ))))
            .andExpect(status().isOk());

        // 1. Attempt login while PENDIENTE_VERIFICACION: must fail
        mockMvc.perform(post("/login")
                .with(csrf())
                .param("email", email)
                .param("password", password))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error"));

        // 2. Find verification token generated for this user
        Usuario user = usuarioRepository.findByEmailNormalizado(email).orElseThrow();
        TokenSeguridad tokenEntity = tokenRepository.findByUsuarioIdAndUsadoEnIsNull(user.getId())
            .stream()
            .filter(t -> "VERIFICACION".equals(t.getTipo()))
            .findFirst()
            .orElseThrow();

        // 3. Verify using the token service's verification flow (using the raw token)
        SecurityTokenService.GeneratedToken freshToken = tokenService.createVerificationToken(user);
        mockMvc.perform(get("/verificar/" + freshToken.rawToken()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?verified"));

        Usuario verifiedUser = usuarioRepository.findById(user.getId()).orElseThrow();
        assertThat(verifiedUser.getEstado()).isEqualTo("ACTIVO");

        // 4. Now login succeeds
        mockMvc.perform(post("/login")
                .with(csrf())
                .param("email", email)
                .param("password", password))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void f8ca6_auditorCannotInviteForAnotherAccountOrganization() throws Exception {
        // Setup Account 1 and Account 2 with organizations
        Cuenta cuenta1 = cuentaRepository.save(new Cuenta("Cuenta Auditor 1", "AUDITOR", "ACTIVA"));
        Cuenta cuenta2 = cuentaRepository.save(new Cuenta("Cuenta Auditor 2", "AUDITOR", "ACTIVA"));

        Empresa newOrg1 = new Empresa();
        newOrg1.setNombre("Org Cuenta 1");
        newOrg1.setCuentaId(cuenta1.getId());
        newOrg1.setPublicId(UUID.randomUUID().toString());
        Empresa org1 = TenantContext.runAs(cuenta1.getId(), () -> empresaRepository.save(newOrg1));

        Empresa newOrg2 = new Empresa();
        newOrg2.setNombre("Org Cuenta 2");
        newOrg2.setCuentaId(cuenta2.getId());
        newOrg2.setPublicId(UUID.randomUUID().toString());
        Empresa org2 = TenantContext.runAs(cuenta2.getId(), () -> empresaRepository.save(newOrg2));

        // Auditor in Cuenta 1
        Usuario newAuditor1 = new Usuario();
        newAuditor1.setCuentaId(cuenta1.getId());
        newAuditor1.setRol("AUDITOR");
        newAuditor1.setEstado("ACTIVO");
        newAuditor1.setEmailNormalizado("auditor1-" + UUID.randomUUID() + "@example.com");
        newAuditor1.setPasswordHash(passwordEncoder.encode("AuditorPassword123!"));
        Usuario auditor1 = TenantContext.runAs(cuenta1.getId(), () -> usuarioRepository.save(newAuditor1));

        TinyHatPrincipal principal = new TinyHatPrincipal(auditor1, true);

        MockHttpSession session = new MockHttpSession();
        long now = clock.millis();
        session.setAttribute(SessionActivityFilter.CREATED_AT_ATTRIBUTE, now);
        session.setAttribute(SessionActivityFilter.ORIGINAL_CREATED_AT_ATTRIBUTE, now);
        session.setAttribute(SessionActivityFilter.LAST_ACTIVITY_ATTRIBUTE, now);
        session.setAttribute(MfaAuthenticationService.LAST_REAUTH_ATTRIBUTE, now);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()
            )
        );
        session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
        sessionRegistry.registerNewSession(session.getId(), principal);

        // 1. Inviting for org1 (same account) succeeds and creates CLIENTE_ADMIN
        String invitedEmail = "cliente-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/organizaciones/" + org1.getPublicId() + "/invitaciones")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", invitedEmail))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("INVITED"))
            .andExpect(jsonPath("$.role").value("CLIENTE_ADMIN"));

        Usuario invitedUser = usuarioRepository.findByEmailNormalizado(invitedEmail).orElseThrow();
        assertThat(invitedUser.getRol()).isEqualTo("CLIENTE_ADMIN");
        assertThat(invitedUser.getEstado()).isEqualTo("INVITADO");
        assertThat(invitedUser.getOrganizacionId()).isEqualTo(org1.getId());
        assertThat(invitedUser.getCuentaId()).isEqualTo(cuenta1.getId());

        // 2. Inviting for org2 (different account) returns 404 (ScopedNotFoundException)
        mockMvc.perform(post("/api/organizaciones/" + org2.getPublicId() + "/invitaciones")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "other-" + UUID.randomUUID() + "@example.com"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void f8ca7_passwordResetExpiresActiveSessions() throws Exception {
        Cuenta cuenta = cuentaRepository.save(new Cuenta("Cuenta Reset Test", "AUDITOR", "ACTIVA"));
        String email = "reset-user-" + UUID.randomUUID() + "@example.com";
        String oldPassword = "InitialPassword123!";

        Usuario newUser = new Usuario();
        newUser.setCuentaId(cuenta.getId());
        newUser.setRol("AUDITOR");
        newUser.setEstado("ACTIVO");
        newUser.setEmailNormalizado(email);
        newUser.setPasswordHash(passwordEncoder.encode(oldPassword));
        Usuario user = TenantContext.runAs(cuenta.getId(), () -> usuarioRepository.save(newUser));

        TinyHatPrincipal principal = new TinyHatPrincipal(user, true);

        // Active session
        MockHttpSession session = new MockHttpSession();
        long now = clock.millis();
        session.setAttribute(SessionActivityFilter.CREATED_AT_ATTRIBUTE, now);
        session.setAttribute(SessionActivityFilter.ORIGINAL_CREATED_AT_ATTRIBUTE, now);
        session.setAttribute(SessionActivityFilter.LAST_ACTIVITY_ATTRIBUTE, now);
        session.setAttribute(MfaAuthenticationService.LAST_REAUTH_ATTRIBUTE, now);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()
            )
        );
        session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
        sessionRegistry.registerNewSession(session.getId(), principal);

        // Generate password reset token
        SecurityTokenService.GeneratedToken resetToken = tokenService.createPasswordResetToken(user);

        // Perform password reset
        String newPassword = "BrandNewPassword123!";
        mockMvc.perform(post("/recuperar/" + resetToken.rawToken())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("password", newPassword))))
            .andExpect(status().isOk());

        // Previous session must now be expired / rejected on protected route
        mockMvc.perform(get("/dashboard").session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?replaced"));
    }

    @Test
    void f8ca8_passwordPolicyRejectsShortOrExcessiveBytesOrCommonPasswords() throws Exception {
        // 73 bytes password
        String seventyThreeBytes = "P".repeat(73);
        mockMvc.perform(post("/registro")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "nombre", "Test",
                    "email", "policy-test1-" + UUID.randomUUID() + "@example.com",
                    "password", seventyThreeBytes
                ))))
            .andExpect(status().isBadRequest());

        // Common password
        mockMvc.perform(post("/registro")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "nombre", "Test",
                    "email", "policy-test2-" + UUID.randomUUID() + "@example.com",
                    "password", "password1234"
                ))))
            .andExpect(status().isBadRequest());

        // Password matching email
        String email = "matching-pass-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/registro")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "nombre", "Test",
                    "email", email,
                    "password", email
                ))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void f8ca10_suspendingAccountViaCommandPreventsLoginAndExpiresSessions() throws Exception {
        Cuenta cuenta = cuentaRepository.save(new Cuenta("Cuenta Suspender Test", "AUDITOR", "ACTIVA"));
        String email = "suspend-auditor-" + UUID.randomUUID() + "@example.com";
        String password = "AuditorPassword123!";

        Usuario newSuspendUser = new Usuario();
        newSuspendUser.setCuentaId(cuenta.getId());
        newSuspendUser.setRol("AUDITOR");
        newSuspendUser.setEstado("ACTIVO");
        newSuspendUser.setEmailNormalizado(email);
        newSuspendUser.setPasswordHash(passwordEncoder.encode(password));
        Usuario user = TenantContext.runAs(cuenta.getId(), () -> usuarioRepository.save(newSuspendUser));

        TinyHatPrincipal principal = new TinyHatPrincipal(user, true);

        MockHttpSession session = new MockHttpSession();
        long now = clock.millis();
        session.setAttribute(SessionActivityFilter.CREATED_AT_ATTRIBUTE, now);
        session.setAttribute(SessionActivityFilter.ORIGINAL_CREATED_AT_ATTRIBUTE, now);
        session.setAttribute(SessionActivityFilter.LAST_ACTIVITY_ATTRIBUTE, now);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()
            )
        );
        session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
        sessionRegistry.registerNewSession(session.getId(), principal);

        MockHttpSession crossInstanceSession = new MockHttpSession();
        crossInstanceSession.setAttribute(SessionActivityFilter.CREATED_AT_ATTRIBUTE, now);
        crossInstanceSession.setAttribute(SessionActivityFilter.ORIGINAL_CREATED_AT_ATTRIBUTE, now);
        crossInstanceSession.setAttribute(SessionActivityFilter.LAST_ACTIVITY_ATTRIBUTE, now);
        SecurityContext crossInstanceSecurityContext = SecurityContextHolder.createEmptyContext();
        crossInstanceSecurityContext.setAuthentication(
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()
            )
        );
        crossInstanceSession.setAttribute("SPRING_SECURITY_CONTEXT", crossInstanceSecurityContext);

        // Verify session works before suspension
        mockMvc.perform(get("/dashboard").session(session))
            .andExpect(status().isOk());

        // Suspend account via owner command
        boolean suspended = ownerOpsService.suspendCuenta(cuenta.getPublicId());
        assertThat(suspended).isTrue();

        Cuenta updatedCuenta = cuentaRepository.findById(cuenta.getId()).orElseThrow();
        assertThat(updatedCuenta.getEstado()).isEqualTo("SUSPENDIDA");
        assertThat(cuentaRepository.findSessionsRevokedAtById(cuenta.getId())).isPresent();

        // 1. Existing session is expired and rejected
        mockMvc.perform(get("/dashboard").session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?replaced"));

        // 2. New login attempt is rejected
        mockMvc.perform(post("/login")
                .with(csrf())
                .param("email", email)
                .param("password", password))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error"));

        assertThat(ownerOpsService.reactivateCuenta(cuenta.getPublicId())).isTrue();
        mockMvc.perform(get("/dashboard").session(crossInstanceSession))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?expired"));
    }
}

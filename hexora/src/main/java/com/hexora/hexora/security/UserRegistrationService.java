package com.hexora.hexora.security;

import com.hexora.hexora.dto.RegistroForm;
import com.hexora.hexora.model.CapacidadCuenta;
import com.hexora.hexora.model.CapacidadCuentaId;
import com.hexora.hexora.model.Cuenta;
import com.hexora.hexora.model.Empresa;
import com.hexora.hexora.model.TokenSeguridad;
import com.hexora.hexora.model.Usuario;
import com.hexora.hexora.repository.CapacidadCuentaRepository;
import com.hexora.hexora.repository.CuentaRepository;
import com.hexora.hexora.repository.UsuarioRepository;
import com.hexora.hexora.service.Capability;
import com.hexora.hexora.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRegistrationService {

    public record RegistrationResult(boolean success, String message) {}

    private static final String DUMMY_BCRYPT_HASH =
        "{bcrypt}$2a$12$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW";

    private final CuentaRepository cuentaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CapacidadCuentaRepository capacidadCuentaRepository;
    private final SecurityTokenService tokenService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PasswordEncoder passwordEncoder;
    private final Notifier notifier;
    private final AuditService auditService;
    private final SessionRegistry sessionRegistry;
    private final SessionTerminationEventPublisher sessionTerminationEventPublisher;
    private final TinyHatSecurityProperties securityProperties;
    private final PermissionService permissionService;
    private final String publicBaseUrl;

    public UserRegistrationService(
        CuentaRepository cuentaRepository,
        UsuarioRepository usuarioRepository,
        CapacidadCuentaRepository capacidadCuentaRepository,
        SecurityTokenService tokenService,
        PasswordPolicyValidator passwordPolicyValidator,
        PasswordEncoder passwordEncoder,
        Notifier notifier,
        AuditService auditService,
        SessionRegistry sessionRegistry,
        SessionTerminationEventPublisher sessionTerminationEventPublisher,
        TinyHatSecurityProperties securityProperties,
        PermissionService permissionService,
        @org.springframework.beans.factory.annotation.Value(
            "${tinyhat.security.public-base-url:http://127.0.0.1:8080}"
        ) String publicBaseUrl
    ) {
        this.cuentaRepository = cuentaRepository;
        this.usuarioRepository = usuarioRepository;
        this.capacidadCuentaRepository = capacidadCuentaRepository;
        this.tokenService = tokenService;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.passwordEncoder = passwordEncoder;
        this.notifier = notifier;
        this.auditService = auditService;
        this.sessionRegistry = sessionRegistry;
        this.sessionTerminationEventPublisher = sessionTerminationEventPublisher;
        this.securityProperties = securityProperties;
        this.permissionService = permissionService;
        this.publicBaseUrl = validatePublicBaseUrl(publicBaseUrl);
    }

    @Transactional
    public RegistrationResult registerAuditor(RegistroForm form, HttpServletRequest request) {
        if (!securityProperties.getRegistration().isEnabled()) {
            throw new ScopedNotFoundException();
        }
        Objects.requireNonNull(form, "Form is required");
        passwordPolicyValidator.validate(form.getPassword(), form.getEmail());

        String emailNormalizado = form.getEmail().trim().toLowerCase(Locale.ROOT);
        Optional<Usuario> existing = usuarioRepository.findByEmailNormalizado(emailNormalizado);

        if (existing.isPresent()) {
            // Equalize response timing with a simulated password verification
            passwordEncoder.matches(form.getPassword(), DUMMY_BCRYPT_HASH);
            notifier.sendAccountAlreadyExistsNotification(emailNormalizado);
            return new RegistrationResult(
                true,
                "Si la dirección de correo es válida, recibirás instrucciones para verificar tu cuenta."
            );
        }

        // Create new Cuenta for the auditor
        Cuenta cuenta = new Cuenta();
        String accountName = form.getNombre() != null && !form.getNombre().isBlank()
            ? form.getNombre().trim()
            : "Cuenta " + emailNormalizado;
        cuenta.setNombre(accountName);
        cuenta.setTipo("AUDITOR");
        cuenta.setEstado("ACTIVA");
        Cuenta savedCuenta = cuentaRepository.save(cuenta);

        // Initialize capabilities: all true
        for (Capability cap : Capability.values()) {
            CapacidadCuenta capacidad = new CapacidadCuenta();
            capacidad.setId(new CapacidadCuentaId(savedCuenta.getId(), cap.getDatabaseValue()));
            capacidad.setHabilitada(true);
            capacidadCuentaRepository.save(capacidad);
        }

        return TenantContext.runAs(savedCuenta.getId(), () -> {
            Usuario usuario = new Usuario();
            usuario.setCuentaId(savedCuenta.getId());
            usuario.setOrganizacionId(null);
            usuario.setRol("AUDITOR");
            usuario.setEstado("PENDIENTE_VERIFICACION");
            usuario.setEmailNormalizado(emailNormalizado);
            usuario.setPasswordHash(passwordEncoder.encode(form.getPassword()));

            Usuario savedUser = usuarioRepository.saveAndFlush(usuario);

            SecurityTokenService.GeneratedToken token = tokenService.createVerificationToken(savedUser);
            String verificationUrl = buildUrl(request, "/verificar/" + token.rawToken());
            notifier.sendVerificationEmail(emailNormalizado, verificationUrl);
            auditService.userCreated(savedUser, request);

            return new RegistrationResult(
                true,
                "Si la dirección de correo es válida, recibirás instrucciones para verificar tu cuenta."
            );
        });
    }

    @Transactional
    public boolean verifyEmail(String rawToken, HttpServletRequest request) {
        Optional<TokenSeguridad> tokenOpt = tokenService.consumeToken(rawToken, SecurityTokenService.TIPO_VERIFICACION);
        if (tokenOpt.isEmpty()) {
            return false;
        }
        TokenSeguridad token = tokenOpt.get();

        return TenantContext.runAs(token.getCuentaId(), () -> {
            Usuario usuario = usuarioRepository.findById(token.getUsuarioId()).orElse(null);
            if (usuario == null) {
                return false;
            }

            usuario.setEstado("ACTIVO");
            usuarioRepository.saveAndFlush(usuario);
            auditService.userVerified(usuario, request);
            return true;
        });
    }

    @Transactional
    public Usuario inviteClientAdmin(
        String orgPublicId,
        String email,
        TinyHatPrincipal auditorPrincipal,
        HttpServletRequest request
    ) {
        Empresa org = permissionService.requireOrganizacion(auditorPrincipal, orgPublicId);
        String emailNormalizado = email.trim().toLowerCase(Locale.ROOT);

        Optional<Usuario> existing = usuarioRepository.findByEmailNormalizado(emailNormalizado);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Ya existe un usuario registrado con este correo");
        }

        return TenantContext.runAs(org.getCuentaId(), () -> {
            Usuario usuario = new Usuario();
            usuario.setCuentaId(org.getCuentaId());
            usuario.setOrganizacionId(org.getId());
            usuario.setRol("CLIENTE_ADMIN");
            usuario.setEstado("INVITADO");
            usuario.setEmailNormalizado(emailNormalizado);
            usuario.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));

            Usuario savedUser = usuarioRepository.saveAndFlush(usuario);

            SecurityTokenService.GeneratedToken token = tokenService.createInvitationToken(
                savedUser, org.getId(), org.getCuentaId()
            );
            String invitationUrl = buildUrl(request, "/invitacion/" + token.rawToken());
            notifier.sendInvitationEmail(emailNormalizado, invitationUrl, org.getNombre());
            auditService.userInvited(savedUser, auditorPrincipal, request);

            return savedUser;
        });
    }

    @Transactional
    public boolean acceptInvitation(String rawToken, String newPassword, HttpServletRequest request) {
        TokenSeguridad token = tokenService.findValidToken(rawToken, SecurityTokenService.TIPO_INVITACION)
            .orElseThrow(() -> new IllegalArgumentException("El token de invitación no es válido o ha expirado"));

        passwordPolicyValidator.validate(newPassword, token.getEmail());

        TokenSeguridad consumed = tokenService.consumeToken(rawToken, SecurityTokenService.TIPO_INVITACION)
            .orElseThrow(() -> new IllegalArgumentException("El token de invitación ya fue utilizado o ha expirado"));

        return TenantContext.runAs(consumed.getCuentaId(), () -> {
            Usuario usuario = usuarioRepository.findById(consumed.getUsuarioId())
                .orElseThrow(() -> new IllegalStateException("El usuario asociado al token no existe"));

            usuario.setPasswordHash(passwordEncoder.encode(newPassword));
            usuario.setEstado("ACTIVO");
            usuarioRepository.saveAndFlush(usuario);
            tokenService.invalidatePendingTokensForUser(usuario.getId());
            auditService.passwordChanged(usuario, request, "INVITATION_ACCEPTED");

            return true;
        });
    }

    @Transactional
    public boolean requestPasswordReset(String email, HttpServletRequest request) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String emailNormalizado = email.trim().toLowerCase(Locale.ROOT);
        Optional<Usuario> userOpt = usuarioRepository.findByEmailNormalizado(emailNormalizado);

        if (userOpt.isEmpty()) {
            passwordEncoder.matches("dummy-password", DUMMY_BCRYPT_HASH);
            return true;
        }

        Usuario user = userOpt.get();
        SecurityTokenService.GeneratedToken token = tokenService.createPasswordResetToken(user);
        String resetUrl = buildUrl(request, "/recuperar/" + token.rawToken());
        notifier.sendPasswordResetEmail(emailNormalizado, resetUrl);
        return true;
    }

    @Transactional
    public boolean resetPassword(String rawToken, String newPassword, HttpServletRequest request) {
        TokenSeguridad token = tokenService.findValidToken(rawToken, SecurityTokenService.TIPO_RECUPERACION)
            .orElseThrow(() -> new IllegalArgumentException("El token de recuperación no es válido o ha expirado"));

        passwordPolicyValidator.validate(newPassword, token.getEmail());

        TokenSeguridad consumed = tokenService.consumeToken(rawToken, SecurityTokenService.TIPO_RECUPERACION)
            .orElseThrow(() -> new IllegalArgumentException("El token de recuperación ya fue utilizado o ha expirado"));

        return TenantContext.runAs(consumed.getCuentaId(), () -> {
            Usuario usuario = usuarioRepository.findById(consumed.getUsuarioId())
                .orElseThrow(() -> new IllegalStateException("El usuario asociado al token no existe"));

            usuario.setPasswordHash(passwordEncoder.encode(newPassword));
            usuarioRepository.saveAndFlush(usuario);

            expireSessionsForUser(usuario.getId());
            tokenService.invalidatePendingTokensForUser(usuario.getId());
            auditService.passwordReset(usuario, request);

            return true;
        });
    }

    public void expireSessionsForUser(Long userId) {
        if (userId == null) {
            return;
        }
        for (Object principalObj : sessionRegistry.getAllPrincipals()) {
            if (principalObj instanceof TinyHatPrincipal principal && Objects.equals(principal.getUserId(), userId)) {
                for (SessionInformation sessionInfo : sessionRegistry.getAllSessions(principalObj, false)) {
                    sessionTerminationEventPublisher.expireAndPublish(sessionInfo);
                }
            }
        }
    }

    private String buildUrl(HttpServletRequest request, String path) {
        String contextPath = request == null ? "" : request.getContextPath();
        return publicBaseUrl + contextPath + path;
    }

    private String validatePublicBaseUrl(String value) {
        URI uri = URI.create(value);
        if (!uri.isAbsolute()
            || uri.getHost() == null
            || uri.getUserInfo() != null
            || uri.getQuery() != null
            || uri.getFragment() != null
            || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalStateException("tinyhat.security.public-base-url must be an absolute HTTP(S) URL");
        }
        String normalized = uri.toString();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}

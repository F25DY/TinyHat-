package com.hexora.hexora.security;

import com.hexora.hexora.model.Cuenta;
import com.hexora.hexora.model.Usuario;
import com.hexora.hexora.repository.CuentaRepository;
import com.hexora.hexora.repository.UsuarioRepository;
import com.hexora.hexora.service.Capability;
import com.hexora.hexora.service.CapabilityService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerOpsService {

    private final CuentaRepository cuentaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CapabilityService capabilityService;
    private final SessionRegistry sessionRegistry;
    private final SessionTerminationEventPublisher sessionTerminationEventPublisher;
    private final AuditService auditService;
    private final Clock clock;

    public OwnerOpsService(
        CuentaRepository cuentaRepository,
        UsuarioRepository usuarioRepository,
        CapabilityService capabilityService,
        SessionRegistry sessionRegistry,
        SessionTerminationEventPublisher sessionTerminationEventPublisher,
        AuditService auditService,
        Clock clock
    ) {
        this.cuentaRepository = cuentaRepository;
        this.usuarioRepository = usuarioRepository;
        this.capabilityService = capabilityService;
        this.sessionRegistry = sessionRegistry;
        this.sessionTerminationEventPublisher = sessionTerminationEventPublisher;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public boolean suspendCuenta(String publicId) {
        Objects.requireNonNull(publicId, "publicId is required");
        Cuenta cuenta = cuentaRepository.findByPublicId(publicId).orElse(null);
        if (cuenta == null) {
            auditService.opsCommand("SUSPEND_CUENTA", publicId, "NOT_FOUND");
            return false;
        }
        cuenta.setEstado("SUSPENDIDA");
        cuentaRepository.saveAndFlush(cuenta);
        int updatedRows = cuentaRepository.markSessionsRevoked(cuenta.getId(), LocalDateTime.now(clock));
        if (updatedRows != 1) {
            throw new IllegalStateException("Could not persist the account session revocation timestamp");
        }
        expireSessionsForAccount(cuenta.getId());
        auditService.opsCommand("SUSPEND_CUENTA", publicId, "OK");
        return true;
    }

    @Transactional
    public boolean reactivateCuenta(String publicId) {
        Objects.requireNonNull(publicId, "publicId is required");
        Cuenta cuenta = cuentaRepository.findByPublicId(publicId).orElse(null);
        if (cuenta == null) {
            auditService.opsCommand("REACTIVATE_CUENTA", publicId, "NOT_FOUND");
            return false;
        }
        cuenta.setEstado("ACTIVA");
        cuentaRepository.save(cuenta);
        auditService.opsCommand("REACTIVATE_CUENTA", publicId, "OK");
        return true;
    }

    @Transactional
    public boolean suspendUsuario(String publicId) {
        return changeUsuarioState(publicId, true);
    }

    @Transactional
    public boolean reactivateUsuario(String publicId) {
        return changeUsuarioState(publicId, false);
    }

    private boolean changeUsuarioState(String publicId, boolean suspend) {
        Objects.requireNonNull(publicId, "publicId is required");
        String command = suspend ? "SUSPEND_USUARIO" : "REACTIVATE_USUARIO";
        Usuario usuario = usuarioRepository.findByPublicId(publicId).orElse(null);
        if (usuario == null) {
            auditService.opsCommand(command, publicId, "NOT_FOUND");
            return false;
        }
        if (!suspend && !"SUSPENDIDO".equals(usuario.getEstado())) {
            // Only a suspended user can be reactivated; this never activates pending or invited users.
            auditService.opsCommand(command, publicId, "INVALID_STATE");
            return false;
        }
        String newState = suspend ? "SUSPENDIDO" : "ACTIVO";
        TenantContext.runAs(usuario.getCuentaId(), () -> {
            usuario.setEstado(newState);
            usuarioRepository.saveAndFlush(usuario);
        });
        if (suspend) {
            sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> principal instanceof TinyHatPrincipal known
                    && Objects.equals(known.getUserId(), usuario.getId()))
                .forEach(principal -> sessionRegistry.getAllSessions(principal, false)
                    .forEach(sessionTerminationEventPublisher::expireAndPublish));
        }
        auditService.userSuspendedByOps(usuario, suspend);
        auditService.opsCommand(command, publicId, "OK");
        return true;
    }

    @Transactional
    public boolean setCapability(String accountPublicId, String capabilityName, boolean enabled) {
        Objects.requireNonNull(accountPublicId, "account public ID is required");
        Capability capability = java.util.Arrays.stream(Capability.values())
            .filter(candidate -> candidate.getDatabaseValue().equals(capabilityName))
            .findFirst()
            .orElse(null);
        Cuenta cuenta = cuentaRepository.findByPublicId(accountPublicId).orElse(null);
        if (cuenta == null || capability == null) {
            auditService.opsCommand("SET_CAPABILITY", accountPublicId, "NOT_FOUND");
            return false;
        }
        boolean changed = capabilityService.setEnabled(cuenta.getId(), capability, enabled);
        if (changed) {
            auditService.capabilityChanged(cuenta.getId(), capability.getDatabaseValue(), enabled);
        }
        auditService.opsCommand("SET_CAPABILITY", accountPublicId, changed ? "OK" : "UNCHANGED");
        return true;
    }

    public void expireSessionsForAccount(Long cuentaId) {
        if (cuentaId == null) {
            return;
        }
        for (Object principalObj : sessionRegistry.getAllPrincipals()) {
            if (principalObj instanceof TinyHatPrincipal principal && Objects.equals(principal.getCuentaId(), cuentaId)) {
                for (SessionInformation sessionInfo : sessionRegistry.getAllSessions(principalObj, false)) {
                    sessionTerminationEventPublisher.expireAndPublish(sessionInfo);
                }
            }
        }
    }
}

package com.hexora.hexora.security;

import com.hexora.hexora.model.Cuenta;
import com.hexora.hexora.repository.CuentaRepository;
import com.hexora.hexora.repository.UsuarioRepository;
import java.util.Objects;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerOpsService {

    private final CuentaRepository cuentaRepository;
    private final UsuarioRepository usuarioRepository;
    private final SessionRegistry sessionRegistry;
    private final AuditService auditService;

    public OwnerOpsService(
        CuentaRepository cuentaRepository,
        UsuarioRepository usuarioRepository,
        SessionRegistry sessionRegistry,
        AuditService auditService
    ) {
        this.cuentaRepository = cuentaRepository;
        this.usuarioRepository = usuarioRepository;
        this.sessionRegistry = sessionRegistry;
        this.auditService = auditService;
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
        cuentaRepository.save(cuenta);
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

    public void expireSessionsForAccount(Long cuentaId) {
        if (cuentaId == null) {
            return;
        }
        for (Object principalObj : sessionRegistry.getAllPrincipals()) {
            if (principalObj instanceof TinyHatPrincipal principal && Objects.equals(principal.getCuentaId(), cuentaId)) {
                for (SessionInformation sessionInfo : sessionRegistry.getAllSessions(principalObj, false)) {
                    sessionInfo.expireNow();
                }
            }
        }
    }
}


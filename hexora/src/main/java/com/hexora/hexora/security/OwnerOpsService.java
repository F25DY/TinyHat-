package com.hexora.hexora.security;

import com.hexora.hexora.model.Cuenta;
import com.hexora.hexora.repository.CuentaRepository;
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
    private final SessionRegistry sessionRegistry;
    private final AuditService auditService;
    private final Clock clock;

    public OwnerOpsService(
        CuentaRepository cuentaRepository,
        SessionRegistry sessionRegistry,
        AuditService auditService,
        Clock clock
    ) {
        this.cuentaRepository = cuentaRepository;
        this.sessionRegistry = sessionRegistry;
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

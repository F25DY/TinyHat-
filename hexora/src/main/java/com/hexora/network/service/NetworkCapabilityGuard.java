package com.hexora.network.service;

import com.hexora.hexora.model.Cuenta;
import com.hexora.hexora.model.Usuario;
import com.hexora.hexora.repository.CuentaRepository;
import com.hexora.hexora.repository.UsuarioRepository;
import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.service.Capability;
import com.hexora.hexora.service.CapabilityService;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("networkCapabilityGuard")
public class NetworkCapabilityGuard {

    private final CapabilityService capabilityService;
    private final UsuarioRepository usuarioRepository;
    private final CuentaRepository cuentaRepository;

    public NetworkCapabilityGuard(
        CapabilityService capabilityService,
        UsuarioRepository usuarioRepository,
        CuentaRepository cuentaRepository
    ) {
        this.capabilityService = capabilityService;
        this.usuarioRepository = usuarioRepository;
        this.cuentaRepository = cuentaRepository;
    }

    public boolean isEnabled(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof TinyHatPrincipal principal)
            || !principal.isEnabled()
            || !"AUDITOR".equals(principal.getRol())) {
            return false;
        }
        Usuario user = usuarioRepository.findByIdAndCuentaId(principal.getUserId(), principal.getCuentaId())
            .filter(candidate -> "ACTIVO".equals(candidate.getEstado())
                && principal.getRol().equals(candidate.getRol())
                && Objects.equals(principal.getOrganizacionId(), candidate.getOrganizacionId()))
            .orElse(null);
        Cuenta account = cuentaRepository.findById(principal.getCuentaId())
            .filter(candidate -> "ACTIVA".equals(candidate.getEstado()))
            .orElse(null);
        if (user == null || account == null) {
            return false;
        }
        return capabilityService.isEnabled(principal.getCuentaId(), Capability.MODULO_RED);
    }

    public void requireEnabled(long accountId) {
        if (!capabilityService.isEnabled(accountId, Capability.MODULO_RED)) {
            throw new NetworkCapabilityDisabledException();
        }
    }
}

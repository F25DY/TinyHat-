package com.hexora.network.service;

import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.service.Capability;
import com.hexora.hexora.service.CapabilityService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("networkCapabilityGuard")
public class NetworkCapabilityGuard {

    private final CapabilityService capabilityService;

    public NetworkCapabilityGuard(CapabilityService capabilityService) {
        this.capabilityService = capabilityService;
    }

    public boolean isEnabled(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof TinyHatPrincipal principal)
            || !principal.isEnabled()
            || !"AUDITOR".equals(principal.getRol())) {
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

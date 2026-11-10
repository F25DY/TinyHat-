package com.hexora.network.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.service.Capability;
import com.hexora.hexora.service.CapabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class NetworkCapabilityGuardTest {

    @Test
    void deniesMissingOrDisabledModuleForApiAndBackgroundScans() {
        CapabilityService capabilityService = mock(CapabilityService.class);
        NetworkCapabilityGuard guard = new NetworkCapabilityGuard(capabilityService);
        TinyHatPrincipal principal = mock(TinyHatPrincipal.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.isEnabled()).thenReturn(true);
        when(principal.getRol()).thenReturn("AUDITOR");
        when(principal.getCuentaId()).thenReturn(12L);
        when(capabilityService.isEnabled(12L, Capability.MODULO_RED)).thenReturn(false);

        assertThat(guard.isEnabled(authentication)).isFalse();
    }

    @Test
    void rejectsUnauthenticatedOrNonAuditorPrincipal() {
        NetworkCapabilityGuard guard = new NetworkCapabilityGuard(mock(CapabilityService.class));

        assertThat(guard.isEnabled(null)).isFalse();

        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("not-a-principal");
        assertThat(guard.isEnabled(authentication)).isFalse();
    }
}

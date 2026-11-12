package com.hexora.network.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.model.Cuenta;
import com.hexora.hexora.model.Usuario;
import com.hexora.hexora.repository.CuentaRepository;
import com.hexora.hexora.repository.UsuarioRepository;
import com.hexora.hexora.service.Capability;
import com.hexora.hexora.service.CapabilityService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class NetworkCapabilityGuardTest {

    @Test
    void deniesMissingOrDisabledModuleForApiAndBackgroundScans() {
        CapabilityService capabilityService = mock(CapabilityService.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        CuentaRepository cuentaRepository = mock(CuentaRepository.class);
        NetworkCapabilityGuard guard =
            new NetworkCapabilityGuard(capabilityService, usuarioRepository, cuentaRepository);
        TinyHatPrincipal principal = mock(TinyHatPrincipal.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.isEnabled()).thenReturn(true);
        when(principal.getRol()).thenReturn("AUDITOR");
        when(principal.getCuentaId()).thenReturn(12L);
        when(principal.getUserId()).thenReturn(34L);
        when(principal.getOrganizacionId()).thenReturn(56L);
        Usuario user = mock(Usuario.class);
        when(user.getEstado()).thenReturn("ACTIVO");
        when(user.getRol()).thenReturn("AUDITOR");
        when(user.getOrganizacionId()).thenReturn(56L);
        Cuenta account = mock(Cuenta.class);
        when(account.getEstado()).thenReturn("ACTIVA");
        when(usuarioRepository.findByIdAndCuentaId(34L, 12L)).thenReturn(Optional.of(user));
        when(cuentaRepository.findById(12L)).thenReturn(Optional.of(account));
        when(capabilityService.isEnabled(12L, Capability.MODULO_RED)).thenReturn(false);

        assertThat(guard.isEnabled(authentication)).isFalse();
    }

    @Test
    void rejectsUnauthenticatedOrNonAuditorPrincipal() {
        NetworkCapabilityGuard guard = new NetworkCapabilityGuard(
            mock(CapabilityService.class),
            mock(UsuarioRepository.class),
            mock(CuentaRepository.class));

        assertThat(guard.isEnabled(null)).isFalse();

        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("not-a-principal");
        assertThat(guard.isEnabled(authentication)).isFalse();
    }

    @Test
    void rejectsSessionsAfterTheUsersRoleChanges() {
        CapabilityService capabilityService = mock(CapabilityService.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        CuentaRepository cuentaRepository = mock(CuentaRepository.class);
        NetworkCapabilityGuard guard =
            new NetworkCapabilityGuard(capabilityService, usuarioRepository, cuentaRepository);
        TinyHatPrincipal principal = mock(TinyHatPrincipal.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.isEnabled()).thenReturn(true);
        when(principal.getRol()).thenReturn("AUDITOR");
        when(principal.getCuentaId()).thenReturn(12L);
        when(principal.getUserId()).thenReturn(34L);
        when(principal.getOrganizacionId()).thenReturn(56L);
        Usuario user = mock(Usuario.class);
        when(user.getEstado()).thenReturn("ACTIVO");
        when(user.getRol()).thenReturn("CLIENTE_ADMIN");
        when(user.getOrganizacionId()).thenReturn(56L);
        when(usuarioRepository.findByIdAndCuentaId(34L, 12L)).thenReturn(Optional.of(user));

        assertThat(guard.isEnabled(authentication)).isFalse();
    }
}

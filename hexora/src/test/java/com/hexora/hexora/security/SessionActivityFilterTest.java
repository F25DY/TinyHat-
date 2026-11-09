package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexora.hexora.model.Cuenta;
import com.hexora.hexora.model.Usuario;
import com.hexora.hexora.repository.CuentaRepository;
import com.hexora.hexora.repository.UsuarioRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import tools.jackson.databind.ObjectMapper;

class SessionActivityFilterTest {

    private static final long START = 1_800_000_000_000L;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void expiresApiSessionAfterFiveMinutesOfInactivity() throws Exception {
        Fixture fixture = new Fixture();
        fixture.clock.advanceMillis(5 * 60_000L);

        MockHttpServletResponse response = fixture.filterRequest("/api/network/status");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("SESSION_EXPIRED");
        assertThat(fixture.session.isInvalid()).isTrue();
        verify(fixture.auditService).sessionExpired(fixture.principal, fixture.request, "IDLE_TIMEOUT");
    }

    @Test
    void pollingDoesNotRefreshActivityButAnInteractiveRequestDoes() throws Exception {
        Fixture fixture = new Fixture();
        fixture.clock.advanceMillis(4 * 60_000L);
        fixture.filterRequest("/api/poll/events");
        assertThat(fixture.session.getAttribute(SessionActivityFilter.LAST_ACTIVITY_ATTRIBUTE))
            .isEqualTo(START);

        fixture.filterRequest("/dashboard");
        assertThat(fixture.session.getAttribute(SessionActivityFilter.LAST_ACTIVITY_ATTRIBUTE))
            .isEqualTo(START + 4 * 60_000L);

        fixture.clock.advanceMillis(4 * 60_000L);
        MockHttpServletResponse response = fixture.filterRequest("/api/poll/events");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(fixture.session.getAttribute(SessionActivityFilter.LAST_ACTIVITY_ATTRIBUTE))
            .isEqualTo(START + 4 * 60_000L);
    }

    @Test
    void continuousPollingStillExpiresAfterTheIdleLimit() throws Exception {
        Fixture fixture = new Fixture();
        MockHttpServletResponse response = null;
        for (int minute = 1; minute <= 10; minute++) {
            fixture.clock.advanceMillis(60_000L);
            response = fixture.filterRequest("/api/poll/events");
            if (response.getStatus() == 401) {
                break;
            }
        }

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("SESSION_EXPIRED");
        assertThat(fixture.session.isInvalid()).isTrue();
        verify(fixture.auditService).sessionExpired(fixture.principal, fixture.request, "IDLE_TIMEOUT");
    }

    @Test
    void absoluteTimeoutExpiresDespiteRecentActivity() throws Exception {
        Fixture fixture = new Fixture();
        fixture.session.setAttribute(
            SessionActivityFilter.LAST_ACTIVITY_ATTRIBUTE, START + 119 * 60_000L);
        fixture.clock.advanceMillis(120 * 60_000L);

        MockHttpServletResponse response = fixture.filterRequest("/dashboard");

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?expired");
        verify(fixture.auditService).sessionExpired(fixture.principal, fixture.request, "ABSOLUTE_TIMEOUT");
    }

    @Test
    void suspendedUserIsRevokedOnTheNextRequest() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.user.getEstado()).thenReturn("SUSPENDIDO");

        MockHttpServletResponse response = fixture.filterRequest("/api/network/status");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("SESSION_EXPIRED");
        verify(fixture.auditService).sessionExpired(fixture.principal, fixture.request, "PRINCIPAL_REVOKED");
    }

    private static final class Fixture {
        private final MutableClock clock = new MutableClock(START);
        private final TinyHatSecurityProperties properties = new TinyHatSecurityProperties();
        private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        private final CuentaRepository cuentaRepository = mock(CuentaRepository.class);
        private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);
        private final AuditService auditService = mock(AuditService.class);
        private final Usuario user = mock(Usuario.class);
        private final Cuenta account = mock(Cuenta.class);
        private final TinyHatPrincipal principal = mock(TinyHatPrincipal.class);
        private final SessionActivityFilter filter;
        private final MockHttpServletRequest request = new MockHttpServletRequest();
        private final MockHttpSession session = new MockHttpSession();

        private Fixture() {
            when(principal.getUserId()).thenReturn(10L);
            when(principal.getCuentaId()).thenReturn(20L);
            when(principal.getRol()).thenReturn("AUDITOR");
            when(principal.getOrganizacionId()).thenReturn(null);
            when(user.getEstado()).thenReturn("ACTIVO");
            when(user.getRol()).thenReturn("AUDITOR");
            when(user.getOrganizacionId()).thenReturn(null);
            when(account.getEstado()).thenReturn("ACTIVA");
            when(usuarioRepository.findByIdAndCuentaId(10L, 20L)).thenReturn(Optional.of(user));
            when(cuentaRepository.findById(20L)).thenReturn(Optional.of(account));
            when(sessionRegistry.getAllSessions(principal, false)).thenReturn(List.of());

            properties.getSession().setPollPaths(List.of("/api/poll/**"));
            filter = new SessionActivityFilter(
                properties,
                clock,
                usuarioRepository,
                cuentaRepository,
                sessionRegistry,
                auditService,
                new ObjectMapper());
            session.setAttribute(SessionActivityFilter.CREATED_AT_ATTRIBUTE, START);
            session.setAttribute(SessionActivityFilter.LAST_ACTIVITY_ATTRIBUTE, START);
            request.setSession(session);
            SecurityContextHolder.getContext().setAuthentication(authentication(principal));
        }

        private MockHttpServletResponse filterRequest(String path) throws ServletException, IOException {
            request.setRequestURI(path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean continued = new AtomicBoolean();
            filter.doFilter(request, response, (servletRequest, servletResponse) ->
                continued.set(true));
            if (response.getStatus() == 200) {
                assertThat(continued).isTrue();
            }
            return response;
        }
    }

    private static Authentication authentication(TinyHatPrincipal principal) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        return authentication;
    }

    private static final class MutableClock extends Clock {
        private long millis;

        private MutableClock(long millis) {
            this.millis = millis;
        }

        private void advanceMillis(long amount) {
            millis += amount;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis() {
            return millis;
        }
    }
}

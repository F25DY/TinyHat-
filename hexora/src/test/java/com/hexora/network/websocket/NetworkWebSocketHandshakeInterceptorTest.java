package com.hexora.network.websocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.security.TinyHatSecurityProperties;
import com.hexora.network.service.NetworkCapabilityGuard;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

class NetworkWebSocketHandshakeInterceptorTest {

    private static final String ALLOWED_ORIGIN = "https://hexora.example";

    private final NetworkCapabilityGuard capabilityGuard = mock(NetworkCapabilityGuard.class);
    private final TinyHatSecurityProperties securityProperties = new TinyHatSecurityProperties();
    private NetworkWebSocketHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        securityProperties.getWs().setAllowedOrigins(List.of(ALLOWED_ORIGIN));
        interceptor = new NetworkWebSocketHandshakeInterceptor(capabilityGuard, securityProperties);
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptsOnlyAnAuthenticatedMfaCompleteSessionFromAnAllowedOrigin() throws Exception {
        when(capabilityGuard.isEnabled(any())).thenReturn(true);
        Authentication authentication = authentication(true, false);
        MockHttpSession session = new MockHttpSession();
        org.springframework.security.core.context.SecurityContext context =
            org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        var attributes = new HashMap<String, Object>();
        assertTrue(handshake(ALLOWED_ORIGIN, session, attributes));
        org.junit.jupiter.api.Assertions.assertEquals(session.getId(),
            attributes.get(org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor
                .HTTP_SESSION_ID_ATTR_NAME));
    }

    @Test
    void rejectsUnlistedOrMissingOriginsBeforeCheckingAuthentication() throws Exception {
        MockHttpSession session = authenticatedSession(authentication(true, false));

        assertFalse(handshake("https://attacker.example", session, new HashMap<>()));
        assertFalse(handshake(null, session, new HashMap<>()));

        verify(capabilityGuard, never()).isEnabled(any());
    }

    @Test
    void rejectsMissingSessionAndPreMfaAuthenticationDuringTheHandshake() throws Exception {
        assertFalse(handshake(ALLOWED_ORIGIN, null, new HashMap<>()));
        assertFalse(handshake(ALLOWED_ORIGIN, authenticatedSession(authentication(true, true)), new HashMap<>()));

        verify(capabilityGuard, never()).isEnabled(any());
    }

    @Test
    void rejectsDisabledUsersAndUsersWithoutTheNetworkCapability() throws Exception {
        when(capabilityGuard.isEnabled(any())).thenReturn(false);
        assertFalse(handshake(ALLOWED_ORIGIN,
            authenticatedSession(authentication(false, false)), new HashMap<>()));
        assertFalse(handshake(ALLOWED_ORIGIN,
            authenticatedSession(authentication(true, false)), new HashMap<>()));
        verify(capabilityGuard).isEnabled(any());
    }

    @Test
    void rejectedHandshakesAnswerForbiddenInsteadOfAnEmptySuccess() throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setMethod("GET");
        servletRequest.addHeader("Origin", "https://evil.example");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        assertFalse(interceptor.beforeHandshake(
            new ServletServerHttpRequest(servletRequest),
            new ServletServerHttpResponse(servletResponse),
            mock(WebSocketHandler.class),
            new HashMap<>()));
        org.junit.jupiter.api.Assertions.assertEquals(403, servletResponse.getStatus());
    }

    private boolean handshake(String origin, HttpSession session, java.util.Map<String, Object> attributes)
        throws Exception {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setMethod("GET");
        if (origin != null) {
            servletRequest.addHeader("Origin", origin);
        }
        if (session != null) {
            servletRequest.setSession((MockHttpSession) session);
        }
        return interceptor.beforeHandshake(
            new ServletServerHttpRequest(servletRequest),
            new ServletServerHttpResponse(new MockHttpServletResponse()),
            mock(WebSocketHandler.class),
            attributes);
    }

    private MockHttpSession authenticatedSession(Authentication authentication) {
        MockHttpSession session = new MockHttpSession();
        org.springframework.security.core.context.SecurityContext context =
            org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private Authentication authentication(boolean enabled, boolean preMfa) {
        TinyHatPrincipal principal = mock(TinyHatPrincipal.class);
        when(principal.isEnabled()).thenReturn(enabled);
        when(principal.getRol()).thenReturn("AUDITOR");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(authentication.isAuthenticated()).thenReturn(true);
        org.mockito.Mockito.doReturn(
            preMfa
            ? List.of(new SimpleGrantedAuthority("ROLE_AUDITOR"), new SimpleGrantedAuthority("ROLE_PRE_MFA"))
            : List.of(new SimpleGrantedAuthority("ROLE_AUDITOR"))
        ).when(authentication).getAuthorities();
        return authentication;
    }
}

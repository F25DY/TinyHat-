package com.hexora.network.websocket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.hexora.hexora.security.TenantContext;
import com.hexora.hexora.security.ScopedNotFoundException;
import com.hexora.hexora.security.SessionTerminatedEvent;
import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.security.TinyHatSecurityProperties;
import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.service.PermissionService;
import com.hexora.network.service.NetworkCapabilityGuard;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.ServletWebSocketHandlerRegistration;
import org.springframework.web.socket.server.HandshakeInterceptor;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.time.Duration;

class NetworkMonitorWebSocketHandlerTest {

    @Test
    void publishesOnlyToSessionsInTheActiveTenant() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"ok\":true}");
        NetworkCapabilityGuard capabilityGuard = mock(NetworkCapabilityGuard.class);
        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.proyectosVisibles(any())).thenReturn(List.of(visibleProject()));
        NetworkMonitorWebSocketHandler handler =
            new NetworkMonitorWebSocketHandler(objectMapper, capabilityGuard, permissionService);
        WebSocketSession firstTenantSession = authenticatedSession("first", 1L);
        WebSocketSession secondTenantSession = authenticatedSession("second", 2L);
        when(capabilityGuard.isEnabled(any())).thenReturn(true);

        handler.afterConnectionEstablished(firstTenantSession);
        handler.afterConnectionEstablished(secondTenantSession);

        TenantContext.runAs(1L, () -> handler.publish("project-1", new Object()));

        verify(firstTenantSession).sendMessage(any(TextMessage.class));
        verify(secondTenantSession, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void closesWebSocketSessionsWhenTheirHttpSessionIsTerminated() throws Exception {
        NetworkCapabilityGuard capabilityGuard = mock(NetworkCapabilityGuard.class);
        when(capabilityGuard.isEnabled(any())).thenReturn(true);
        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.proyectosVisibles(any())).thenReturn(List.of(visibleProject()));
        NetworkMonitorWebSocketHandler handler =
            new NetworkMonitorWebSocketHandler(mock(ObjectMapper.class), capabilityGuard, permissionService);
        WebSocketSession matchingSession = authenticatedSession("matching", 1L);
        WebSocketSession otherSession = authenticatedSession("other", 1L);
        when(matchingSession.getAttributes()).thenReturn(
            java.util.Map.of(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME, "http-session-1"));
        when(otherSession.getAttributes()).thenReturn(
            java.util.Map.of(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME, "http-session-2"));
        handler.afterConnectionEstablished(matchingSession);
        handler.afterConnectionEstablished(otherSession);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
            handler.closeSessionsForTerminatedHttpSession(new SessionTerminatedEvent("http-session-1")));

        verify(matchingSession).close(org.springframework.web.socket.CloseStatus.NORMAL);
        verify(otherSession, never()).close(any());
    }

    @Test
    void closesConnectionsWithoutCompletedMfa() throws Exception {
        NetworkCapabilityGuard capabilityGuard = mock(NetworkCapabilityGuard.class);
        when(capabilityGuard.isEnabled(any())).thenReturn(true);
        PermissionService permissionService = mock(PermissionService.class);
        NetworkMonitorWebSocketHandler handler =
            new NetworkMonitorWebSocketHandler(mock(ObjectMapper.class), capabilityGuard, permissionService);
        WebSocketSession session = authenticatedSession("pre-mfa", 1L);
        Authentication authentication = (Authentication) session.getPrincipal();
        org.mockito.Mockito.doReturn(List.of(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_AUDITOR"),
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PRE_MFA")
        )).when(authentication).getAuthorities();

        handler.afterConnectionEstablished(session);

        verify(session).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
        verify(permissionService, never()).proyectosVisibles(any());
    }

    @Test
    void closesConnectionsForDisabledPrincipals() throws Exception {
        NetworkCapabilityGuard capabilityGuard = mock(NetworkCapabilityGuard.class);
        when(capabilityGuard.isEnabled(any())).thenReturn(true);
        PermissionService permissionService = mock(PermissionService.class);
        NetworkMonitorWebSocketHandler handler =
            new NetworkMonitorWebSocketHandler(mock(ObjectMapper.class), capabilityGuard, permissionService);
        WebSocketSession session = authenticatedSession("disabled", 1L, false);

        handler.afterConnectionEstablished(session);

        verify(session).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
        verify(permissionService, never()).proyectosVisibles(any());
    }

    @Test
    void closesConnectionsWithoutNetworkCapability() throws Exception {
        NetworkCapabilityGuard capabilityGuard = mock(NetworkCapabilityGuard.class);
        when(capabilityGuard.isEnabled(any())).thenReturn(false);
        PermissionService permissionService = mock(PermissionService.class);
        NetworkMonitorWebSocketHandler handler =
            new NetworkMonitorWebSocketHandler(mock(ObjectMapper.class), capabilityGuard, permissionService);
        WebSocketSession session = authenticatedSession("no-capability", 1L);

        handler.afterConnectionEstablished(session);

        verify(session).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
        verify(permissionService, never()).proyectosVisibles(any());
    }

    @Test
    void closesConnectionsAfterThePerConnectionMessageLimit() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"ok\":true}");
        NetworkCapabilityGuard capabilityGuard = mock(NetworkCapabilityGuard.class);
        when(capabilityGuard.isEnabled(any())).thenReturn(true);
        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.proyectosVisibles(any())).thenReturn(List.of(visibleProject()));
        NetworkMonitorWebSocketHandler handler =
            new NetworkMonitorWebSocketHandler(objectMapper, capabilityGuard, permissionService);
        WebSocketSession session = authenticatedSession("message-limit", 1L);
        handler.afterConnectionEstablished(session);

        for (int message = 0; message < 1_000; message++) {
            TenantContext.runAs(1L, () -> handler.publish("project-1", new Object()));
        }
        TenantContext.runAs(1L, () -> handler.publish("project-1", new Object()));

        verify(session).close(argThat(status -> status.getCode() == 1008));
        verify(session, org.mockito.Mockito.times(1_000)).sendMessage(any(TextMessage.class));
    }

    @Test
    void rejectsClientMessagesAndClosesOversizedMessages() throws Exception {
        NetworkMonitorWebSocketHandler handler =
            new NetworkMonitorWebSocketHandler(
                mock(ObjectMapper.class),
                mock(NetworkCapabilityGuard.class),
                mock(PermissionService.class));
        WebSocketSession smallMessageSession = mock(WebSocketSession.class);
        WebSocketSession oversizedMessageSession = mock(WebSocketSession.class);

        handler.handleTextMessage(smallMessageSession, new TextMessage("client message"));
        handler.handleTextMessage(oversizedMessageSession, new TextMessage("x".repeat(65_537)));

        verify(smallMessageSession).close(argThat(status -> status.getCode() == 1003));
        verify(oversizedMessageSession).close(argThat(status -> status.getCode() == 1009));
    }

    @Test
    void refusesWildcardWebSocketOrigins() {
        TinyHatSecurityProperties securityProperties = new TinyHatSecurityProperties();
        securityProperties.getWs().setAllowedOrigins(List.of("*"));

        assertThrows(IllegalStateException.class, () -> new NetworkWebSocketConfiguration(
            mock(NetworkMonitorWebSocketHandler.class),
            mock(NetworkWebSocketHandshakeInterceptor.class),
            securityProperties));
    }

    @Test
    void registersOnlyTheConfiguredExactWebSocketOrigin() {
        TinyHatSecurityProperties securityProperties = new TinyHatSecurityProperties();
        securityProperties.getWs().setAllowedOrigins(List.of("https://hexora.example"));
        NetworkMonitorWebSocketHandler handler = mock(NetworkMonitorWebSocketHandler.class);
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        ServletWebSocketHandlerRegistration registration = mock(ServletWebSocketHandlerRegistration.class);
        when(registry.addHandler(handler, "/ws/network")).thenReturn(registration);
        when(registration.addInterceptors(any(HandshakeInterceptor[].class))).thenReturn(registration);

        NetworkWebSocketConfiguration configuration =
            new NetworkWebSocketConfiguration(
                handler,
                mock(NetworkWebSocketHandshakeInterceptor.class),
                securityProperties);
        configuration.registerWebSocketHandlers(registry);

        verify(registration).setAllowedOrigins("https://hexora.example");
    }

    @Test
    void doesNotPublishToSessionsWithoutAccessToTheProject() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"ok\":true}");
        NetworkCapabilityGuard capabilityGuard = mock(NetworkCapabilityGuard.class);
        when(capabilityGuard.isEnabled(any())).thenReturn(true);
        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.proyectosVisibles(any())).thenReturn(List.of(visibleProject()));
        NetworkMonitorWebSocketHandler handler =
            new NetworkMonitorWebSocketHandler(objectMapper, capabilityGuard, permissionService);
        WebSocketSession session = authenticatedSession("project-scope", 1L);
        handler.afterConnectionEstablished(session);

        TenantContext.runAs(1L, () -> handler.publish("project-2", new Object()));

        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void doesNotPublishAfterProjectAccessHasBeenRevoked() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"ok\":true}");
        NetworkCapabilityGuard capabilityGuard = mock(NetworkCapabilityGuard.class);
        when(capabilityGuard.isEnabled(any())).thenReturn(true);
        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.proyectosVisibles(any())).thenReturn(List.of(visibleProject()));
        doThrow(new ScopedNotFoundException()).when(permissionService).requireProyecto(any(), any());
        NetworkMonitorWebSocketHandler handler =
            new NetworkMonitorWebSocketHandler(objectMapper, capabilityGuard, permissionService);
        WebSocketSession session = authenticatedSession("revoked-project-access", 1L);
        handler.afterConnectionEstablished(session);

        TenantContext.runAs(1L, () -> handler.publish("project-1", new Object()));

        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void doesNotPublishAfterNetworkCapabilityHasBeenRevoked() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"ok\":true}");
        NetworkCapabilityGuard capabilityGuard = mock(NetworkCapabilityGuard.class);
        when(capabilityGuard.isEnabled(any())).thenReturn(true, false);
        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.proyectosVisibles(any())).thenReturn(List.of(visibleProject()));
        NetworkMonitorWebSocketHandler handler =
            new NetworkMonitorWebSocketHandler(objectMapper, capabilityGuard, permissionService);
        WebSocketSession session = authenticatedSession("revoked-network-capability", 1L);
        handler.afterConnectionEstablished(session);

        TenantContext.runAs(1L, () -> handler.publish("project-1", new Object()));

        verify(session, never()).sendMessage(any(TextMessage.class));
        verify(session).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
        verify(permissionService, never()).requireProyecto(any(), any());
    }

    private Auditoria visibleProject() {
        Auditoria project = new Auditoria();
        project.setPublicId("project-1");
        project.setNombre("Project one");
        return project;
    }

    private WebSocketSession authenticatedSession(String id, long tenantId) {
        return authenticatedSession(id, tenantId, true);
    }

    private WebSocketSession authenticatedSession(String id, long tenantId, boolean enabled) {
        TinyHatPrincipal principal = mock(TinyHatPrincipal.class);
        when(principal.isEnabled()).thenReturn(enabled);
        when(principal.getRol()).thenReturn("AUDITOR");
        when(principal.getCuentaId()).thenReturn(tenantId);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(authentication.isAuthenticated()).thenReturn(true);
        org.mockito.Mockito.doReturn(
            java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_AUDITOR"))
        ).when(authentication).getAuthorities();

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getPrincipal()).thenReturn(authentication);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(java.util.Map.of(
            HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME,
            "http-session-" + id
        ));
        return session;
    }
}

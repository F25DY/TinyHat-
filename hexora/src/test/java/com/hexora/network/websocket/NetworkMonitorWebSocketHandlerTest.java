package com.hexora.network.websocket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexora.hexora.security.TenantContext;
import com.hexora.hexora.security.SessionTerminatedEvent;
import com.hexora.hexora.security.TinyHatPrincipal;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class NetworkMonitorWebSocketHandlerTest {

    @Test
    void publishesOnlyToSessionsInTheActiveTenant() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"ok\":true}");
        NetworkMonitorWebSocketHandler handler = new NetworkMonitorWebSocketHandler(objectMapper);
        WebSocketSession firstTenantSession = authenticatedSession("first", 1L);
        WebSocketSession secondTenantSession = authenticatedSession("second", 2L);

        handler.afterConnectionEstablished(firstTenantSession);
        handler.afterConnectionEstablished(secondTenantSession);

        TenantContext.runAs(1L, () -> handler.publish(new Object()));

        verify(firstTenantSession).sendMessage(any(TextMessage.class));
        verify(secondTenantSession, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void closesWebSocketSessionsWhenTheirHttpSessionIsTerminated() throws Exception {
        NetworkMonitorWebSocketHandler handler =
            new NetworkMonitorWebSocketHandler(mock(ObjectMapper.class));
        WebSocketSession matchingSession = authenticatedSession("matching", 1L);
        WebSocketSession otherSession = authenticatedSession("other", 1L);
        when(matchingSession.getAttributes()).thenReturn(
            java.util.Map.of(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME, "http-session-1"));
        when(otherSession.getAttributes()).thenReturn(
            java.util.Map.of(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME, "http-session-2"));
        handler.afterConnectionEstablished(matchingSession);
        handler.afterConnectionEstablished(otherSession);

        handler.closeSessionsForTerminatedHttpSession(new SessionTerminatedEvent("http-session-1"));

        verify(matchingSession).close(org.springframework.web.socket.CloseStatus.NORMAL);
        verify(otherSession, never()).close(any());
    }

    private WebSocketSession authenticatedSession(String id, long tenantId) {
        TinyHatPrincipal principal = mock(TinyHatPrincipal.class);
        when(principal.isEnabled()).thenReturn(true);
        when(principal.getRol()).thenReturn("AUDITOR");
        when(principal.getCuentaId()).thenReturn(tenantId);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getPrincipal()).thenReturn(authentication);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(java.util.Map.of());
        return session;
    }
}

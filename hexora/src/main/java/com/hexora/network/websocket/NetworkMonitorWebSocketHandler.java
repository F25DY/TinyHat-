package com.hexora.network.websocket;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.hexora.hexora.security.TenantContext;
import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.security.SessionTerminatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Component
public class NetworkMonitorWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkMonitorWebSocketHandler.class);
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, TenantSession> sessions = new ConcurrentHashMap<>();

    public NetworkMonitorWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object sessionPrincipal = session.getPrincipal();
        TinyHatPrincipal principal = sessionPrincipal instanceof Authentication authentication
            && authentication.getPrincipal() instanceof TinyHatPrincipal tinyHatPrincipal
                ? tinyHatPrincipal
                : null;
        if (principal == null || !principal.isEnabled() || !"AUDITOR".equals(principal.getRol())) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(session, 10_000, 65_536);
        Object httpSessionId = session.getAttributes().get(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME);
        sessions.put(session.getId(), new TenantSession(
            principal.getCuentaId(),
            httpSessionId instanceof String id ? id : null,
            safeSession));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @EventListener
    public void closeSessionsForTerminatedHttpSession(SessionTerminatedEvent event) {
        sessions.forEach((webSocketId, tenantSession) -> {
            if (!event.sessionId().equals(tenantSession.httpSessionId())) {
                return;
            }
            sessions.remove(webSocketId);
            try {
                tenantSession.session().close(CloseStatus.NORMAL);
            } catch (IOException exception) {
                LOGGER.warn("Could not close WebSocket session {}", webSocketId, exception);
            }
        });
    }

    public void publish(Object update) {
        long tenantId = TenantContext.requireTenantId();
        final String json;
        try {
            json = objectMapper.writeValueAsString(update);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize network monitoring update", exception);
        }

        for (var entry : sessions.entrySet()) {
            if (entry.getValue().tenantId() != tenantId) {
                continue;
            }
            try {
                entry.getValue().session().sendMessage(new TextMessage(json));
            } catch (IOException exception) {
                sessions.remove(entry.getKey());
                LOGGER.warn("Could not deliver network update to WebSocket session {}", entry.getKey(), exception);
            }
        }

    }

    private record TenantSession(long tenantId, String httpSessionId, WebSocketSession session) {
    }
}

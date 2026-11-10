package com.hexora.network.websocket;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.hexora.hexora.security.TenantContext;
import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.security.SessionTerminatedEvent;
import com.hexora.network.service.NetworkCapabilityGuard;
import com.hexora.hexora.service.PermissionService;
import com.hexora.hexora.security.ScopedNotFoundException;
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
    private final NetworkCapabilityGuard capabilityGuard;
    private final PermissionService permissionService;
    private final ConcurrentMap<String, TenantSession> sessions = new ConcurrentHashMap<>();

    public NetworkMonitorWebSocketHandler(
        ObjectMapper objectMapper,
        NetworkCapabilityGuard capabilityGuard,
        PermissionService permissionService
    ) {
        this.objectMapper = objectMapper;
        this.capabilityGuard = capabilityGuard;
        this.permissionService = permissionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object sessionPrincipal = session.getPrincipal();
        Authentication authentication = sessionPrincipal instanceof Authentication auth ? auth : null;
        TinyHatPrincipal principal = authentication != null
            && authentication.getPrincipal() instanceof TinyHatPrincipal tinyHatPrincipal
                ? tinyHatPrincipal
                : null;
        var authorities = authentication == null
            ? java.util.Set.<String>of()
            : AuthorityUtils.authorityListToSet(authentication.getAuthorities());
        Object httpSessionId = session.getAttributes().get(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME);
        if (authentication == null || !authentication.isAuthenticated()
            || principal == null || !principal.isEnabled()
            || !"AUDITOR".equals(principal.getRol())
            || !authorities.contains("ROLE_AUDITOR")
            || authorities.contains("ROLE_PRE_MFA")
            || !capabilityGuard.isEnabled(authentication)
            || !(httpSessionId instanceof String id) || id.isBlank()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        var visibleProjectIds = permissionService.proyectosVisibles(principal).stream()
            .map(com.hexora.hexora.model.Auditoria::getPublicId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (visibleProjectIds.isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(session, 10_000, 65_536);
        sessions.put(session.getId(), new TenantSession(
            principal.getCuentaId(),
            id,
            principal,
            visibleProjectIds,
            safeSession));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        if (message.getPayloadLength() > 65_536) {
            session.close(new CloseStatus(1009, "Message too large"));
            return;
        }
        session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Client messages are not supported"));
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

    public void publish(String projectPublicId, Object update) {
        long tenantId = TenantContext.requireTenantId();
        if (projectPublicId == null || projectPublicId.isBlank()) {
            throw new IllegalArgumentException("A visible project is required for a network update");
        }
        final String json;
        try {
            json = objectMapper.writeValueAsString(new ProjectUpdate(projectPublicId, update));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize network monitoring update", exception);
        }

        for (var entry : sessions.entrySet()) {
            if (entry.getValue().tenantId() != tenantId
                || !entry.getValue().projectPublicIds().contains(projectPublicId)) {
                continue;
            }
            try {
                permissionService.requireProyecto(entry.getValue().principal(), projectPublicId);
            } catch (ScopedNotFoundException exception) {
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

    private record ProjectUpdate(String projectPublicId, Object update) {
    }

    private record TenantSession(
        long tenantId,
        String httpSessionId,
        TinyHatPrincipal principal,
        java.util.Set<String> projectPublicIds,
        WebSocketSession session
    ) {
    }
}

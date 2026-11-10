package com.hexora.network.websocket;

import com.hexora.hexora.security.TinyHatSecurityProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;
import java.net.URI;

@Configuration
@EnableWebSocket
public class NetworkWebSocketConfiguration implements WebSocketConfigurer {

    private final NetworkMonitorWebSocketHandler handler;
    private final TinyHatSecurityProperties securityProperties;

    public NetworkWebSocketConfiguration(
        NetworkMonitorWebSocketHandler handler,
        TinyHatSecurityProperties securityProperties
    ) {
        this.handler = handler;
        this.securityProperties = securityProperties;
        if (securityProperties.getWs().getAllowedOrigins().stream().anyMatch(origin -> !isExactOrigin(origin))) {
            throw new IllegalStateException("WebSocket origins must be explicit HTTP(S) origins; wildcards are forbidden");
        }
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/network")
            .addInterceptors(new HttpSessionHandshakeInterceptor())
            .setAllowedOrigins(securityProperties.getWs().getAllowedOrigins().toArray(String[]::new));
    }

    private boolean isExactOrigin(String value) {
        if (value == null || value.isBlank() || "*".equals(value)) {
            return false;
        }
        try {
            URI origin = URI.create(value);
            return origin.isAbsolute()
                && ("https".equalsIgnoreCase(origin.getScheme()) || "http".equalsIgnoreCase(origin.getScheme()))
                && origin.getHost() != null
                && origin.getRawPath().isEmpty()
                && origin.getQuery() == null
                && origin.getFragment() == null
                && origin.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}

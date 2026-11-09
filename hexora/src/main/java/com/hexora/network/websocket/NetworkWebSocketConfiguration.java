package com.hexora.network.websocket;

import com.hexora.hexora.security.TinyHatSecurityProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

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
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/network")
            .addInterceptors(new HttpSessionHandshakeInterceptor())
            .setAllowedOrigins(securityProperties.getWs().getAllowedOrigins().toArray(String[]::new));
    }
}

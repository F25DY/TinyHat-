package com.hexora.network.websocket;

import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.security.TinyHatSecurityProperties;
import com.hexora.network.service.NetworkCapabilityGuard;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Component
public class NetworkWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final NetworkCapabilityGuard capabilityGuard;
    private final Set<String> allowedOrigins;

    public NetworkWebSocketHandshakeInterceptor(
        NetworkCapabilityGuard capabilityGuard,
        TinyHatSecurityProperties securityProperties
    ) {
        this.capabilityGuard = capabilityGuard;
        this.allowedOrigins = Set.copyOf(securityProperties.getWs().getAllowedOrigins());
    }

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler handler,
        Map<String, Object> attributes
    ) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return reject(response);
        }
        String origin = request.getHeaders().getOrigin();
        if (origin == null || !allowedOrigins.contains(origin)) {
            return reject(response);
        }

        var httpSession = servletRequest.getServletRequest().getSession(false);
        if (httpSession == null) {
            return reject(response);
        }
        Object contextValue = httpSession.getAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        Authentication authentication = contextValue instanceof SecurityContext context
            ? context.getAuthentication()
            : SecurityContextHolder.getContext().getAuthentication();
        TinyHatPrincipal principal = authentication != null
            && authentication.getPrincipal() instanceof TinyHatPrincipal tinyHatPrincipal
                ? tinyHatPrincipal
                : null;
        Set<String> authorities = authentication == null
            ? Set.of()
            : AuthorityUtils.authorityListToSet(authentication.getAuthorities());
        if (authentication == null || !authentication.isAuthenticated()
            || principal == null || !principal.isEnabled()
            || !"AUDITOR".equals(principal.getRol())
            || !authorities.contains("ROLE_AUDITOR")
            || authorities.contains("ROLE_PRE_MFA")
            || !capabilityGuard.isEnabled(authentication)) {
            return reject(response);
        }

        attributes.put(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME, httpSession.getId());
        return true;
    }

    private boolean reject(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler handler,
        Exception exception
    ) {
    }
}

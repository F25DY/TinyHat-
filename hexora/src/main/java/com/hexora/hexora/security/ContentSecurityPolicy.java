package com.hexora.hexora.security;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the Content-Security-Policy directives from configuration so that the
 * WebSocket endpoint allowed in {@code connect-src} always matches the explicit
 * origins accepted by the WebSocket handshake.
 */
public final class ContentSecurityPolicy {

    private ContentSecurityPolicy() {
    }

    public static String directives(List<String> webSocketAllowedOrigins) {
        Set<String> connectSources = new LinkedHashSet<>();
        connectSources.add("'self'");
        for (String origin : webSocketAllowedOrigins) {
            connectSources.add(webSocketSource(origin));
        }
        return "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
            + "connect-src " + String.join(" ", connectSources) + "; font-src 'self'; "
            + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'";
    }

    static String webSocketSource(String origin) {
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("WebSocket origin is not a valid URI", exception);
        }
        String scheme = uri.getScheme();
        if (!uri.isAbsolute() || uri.getHost() == null
            || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
            || !uri.getRawPath().isEmpty() || uri.getQuery() != null
            || uri.getFragment() != null || uri.getUserInfo() != null) {
            throw new IllegalStateException("WebSocket origins must be explicit HTTP(S) origins");
        }
        String socketScheme = "https".equalsIgnoreCase(scheme) ? "wss" : "ws";
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return socketScheme + "://" + uri.getHost() + port;
    }
}

package com.hexora.hexora.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.AntPathMatcher;

enum RateLimitCategory {
    AUTH("auth"),
    REGISTRO("registro"),
    MFA("mfa"),
    TOKEN("token"),
    CVE_SEARCH("cve-search"),
    NETWORK_DISCOVER("network-discover"),
    PDF("pdf"),
    POLL("poll"),
    API_GENERAL("api-general");

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private final String propertyName;

    RateLimitCategory(String propertyName) {
        this.propertyName = propertyName;
    }

    String propertyName() {
        return propertyName;
    }

    static RateLimitCategory classify(HttpServletRequest request, TinyHatSecurityProperties properties) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (matches(path, "/api/auth/**", "/oauth2/**", "/login/oauth2/**")
            || ("/login".equals(path) && "POST".equalsIgnoreCase(request.getMethod()))) {
            return AUTH;
        }
        if (matches(
            path,
            "/registro",
            "/registro/**",
            "/invitacion/**",
            "/api/registro",
            "/api/registro/**",
            "/api/organizaciones/*/invitaciones"
        )) {
            return REGISTRO;
        }
        if (matches(path, "/mfa/**")) {
            return MFA;
        }
        if (matches(
            path,
            "/recuperar",
            "/recuperar/**",
            "/recuperar-contrasena",
            "/verificar/**",
            "/verificar-correo/**",
            "/api/token/**",
            "/api/email/verify/**"
        )) {
            return TOKEN;
        }
        if (matches(path, "/api/cves/**")) {
            return CVE_SEARCH;
        }
        if (matches(path, "/api/network/discover", "/api/network/discover/**")) {
            return NETWORK_DISCOVER;
        }
        if (matches(path, "/**/pdf", "/**/pdf/**", "/api/pdf/**", "/**/*.pdf", "/api/informes/*/descarga")) {
            return PDF;
        }
        if (properties.getSession().getPollPaths().stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path))) {
            return POLL;
        }
        return API_GENERAL;
    }

    static boolean isStaticResource(String path) {
        return matches(path, "/css/**", "/js/**", "/img/**", "/imagen/**", "/favicon.ico",
            "/webjars/**", "/actuator/health", "/actuator/health/**");
    }

    private static boolean matches(String path, String... patterns) {
        for (String pattern : patterns) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}

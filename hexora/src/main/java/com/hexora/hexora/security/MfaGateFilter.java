package com.hexora.hexora.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

public class MfaGateFilter extends OncePerRequestFilter {

    private final TinyHatSecurityProperties properties;
    private final MfaService mfaService;
    private final ObjectMapper objectMapper;

    public MfaGateFilter(
        TinyHatSecurityProperties properties,
        MfaService mfaService,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.mfaService = mfaService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return !properties.getMfa().isRequired()
            || RateLimitCategory.isStaticResource(path);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || !AuthorityUtils.authorityListToSet(authentication.getAuthorities()).contains("ROLE_PRE_MFA")) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = path(request);
        if (path.startsWith("/mfa/") || "/mfa".equals(path) || "/logout".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/")) {
            SecurityConfiguration.writeJsonError(
                objectMapper, request, response, HttpStatus.UNAUTHORIZED, "MFA_REQUIRED"
            );
            return;
        }

        // A PRE_MFA authentication always carries a TinyHatPrincipal; anything else is refused, never assumed.
        if (!(authentication.getPrincipal() instanceof TinyHatPrincipal principal)) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        String nextStep = mfaService.isActive(principal.getUserId()) ? "/mfa/verify" : "/mfa/enroll";
        response.sendRedirect(request.getContextPath() + nextStep);
    }

    private String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}

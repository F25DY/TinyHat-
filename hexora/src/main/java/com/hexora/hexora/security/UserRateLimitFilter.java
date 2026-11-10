package com.hexora.hexora.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

public class UserRateLimitFilter extends OncePerRequestFilter {

    private final TinyHatSecurityProperties properties;
    private final ClientIpResolver clientIpResolver;
    private final RateLimitService rateLimitService;
    private final RateLimitAuditGate auditGate;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public UserRateLimitFilter(
        TinyHatSecurityProperties properties,
        ClientIpResolver clientIpResolver,
        RateLimitService rateLimitService,
        RateLimitAuditGate auditGate,
        AuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.clientIpResolver = clientIpResolver;
        this.rateLimitService = rateLimitService;
        this.auditGate = auditGate;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return RateLimitCategory.isStaticResource(path)
            || authentication == null
            || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof TinyHatPrincipal);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        TinyHatPrincipal principal = (TinyHatPrincipal) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
        RateLimitCategory category = RateLimitCategory.classify(request, properties);
        TinyHatSecurityProperties.Bucket limits = IpRateLimitFilter.bucketFor(category, properties);
        String key = "user:" + principal.getUserId() + ":" + category.propertyName();
        long retryAfter = rateLimitService.tryConsume(key, limits);
        if (retryAfter == 0) {
            filterChain.doFilter(request, response);
            return;
        }

        if (auditGate.shouldAudit(key)) {
            auditService.rateLimited(
                category.propertyName(),
                "user:" + principal.getUserId(),
                clientIpResolver.resolve(request),
                request
            );
        }
        response.setHeader("Retry-After", Long.toString(retryAfter));
        SecurityConfiguration.writeJsonError(
            objectMapper, request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"
        );
    }
}

package com.hexora.hexora.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

public class IpRateLimitFilter extends OncePerRequestFilter {

    private final TinyHatSecurityProperties properties;
    private final ClientIpResolver clientIpResolver;
    private final LoginAttemptService loginAttemptService;
    private final RateLimitService rateLimitService;
    private final RateLimitAuditGate auditGate;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public IpRateLimitFilter(
        TinyHatSecurityProperties properties,
        ClientIpResolver clientIpResolver,
        LoginAttemptService loginAttemptService,
        RateLimitService rateLimitService,
        RateLimitAuditGate auditGate,
        AuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.clientIpResolver = clientIpResolver;
        this.loginAttemptService = loginAttemptService;
        this.rateLimitService = rateLimitService;
        this.auditGate = auditGate;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return RateLimitCategory.isStaticResource(path);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String ip = clientIpResolver.resolve(request);
        RateLimitCategory category = RateLimitCategory.classify(request, properties);
        TinyHatSecurityProperties.Bucket limits = bucketFor(category);
        long retryAfter = rateLimitService.tryConsume("ip:" + ip + ":" + category.propertyName(), limits);
        if (retryAfter == 0) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isLoginSubmission(request)) {
            long loginRetryAfter = loginAttemptService.retryAfterSeconds(request.getParameter("email"), ip);
            if (loginRetryAfter > 0) {
                if (auditGate.shouldAudit("login-lockout:" + ip)) {
                    auditService.throttled(ip, loginRetryAfter, request);
                }
                response.sendRedirect(request.getContextPath() + "/login?error");
                return;
            }
        }

        auditRejection(ip, category, request);
        response.setHeader("Retry-After", Long.toString(retryAfter));
        SecurityConfiguration.writeJsonError(
            objectMapper, request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"
        );
    }

    private void auditRejection(String ip, RateLimitCategory category, HttpServletRequest request) {
        if (auditGate.shouldAudit("ip:" + ip + ":" + category.propertyName())) {
            auditService.rateLimited(category.propertyName(), ip, ip, request);
        }
    }

    private boolean isLoginSubmission(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
            && "/login".equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    static TinyHatSecurityProperties.Bucket bucketFor(
        RateLimitCategory category,
        TinyHatSecurityProperties properties
    ) {
        TinyHatSecurityProperties.RateLimit limits = properties.getRatelimit();
        return switch (category) {
            case AUTH -> limits.getAuth();
            case REGISTRO -> limits.getRegistro();
            case MFA -> limits.getMfa();
            case TOKEN -> limits.getToken();
            case CVE_SEARCH -> limits.getCveSearch();
            case NETWORK_DISCOVER -> limits.getNetworkDiscover();
            case PDF -> limits.getPdf();
            case POLL -> limits.getPoll();
            case API_GENERAL -> limits.getApiGeneral();
        };
    }

    private TinyHatSecurityProperties.Bucket bucketFor(RateLimitCategory category) {
        return bucketFor(category, properties);
    }
}

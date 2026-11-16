package com.hexora.hexora.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoginAttemptFilter extends OncePerRequestFilter {

    private final LoginAttemptService loginAttemptService;
    private final ClientIpResolver clientIpResolver;
    private final RateLimitAuditGate auditGate;
    private final AuditService auditService;
    public LoginAttemptFilter(
        LoginAttemptService loginAttemptService,
        ClientIpResolver clientIpResolver,
        RateLimitAuditGate auditGate,
        AuditService auditService
    ) {
        this.loginAttemptService = loginAttemptService;
        this.clientIpResolver = clientIpResolver;
        this.auditGate = auditGate;
        this.auditService = auditService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
            && "/login".equals(request.getRequestURI().substring(request.getContextPath().length())));
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String email = request.getParameter("email");
        String ip = clientIpResolver.resolve(request);
        long retryAfter = loginAttemptService.retryAfterSeconds(email, ip);
        if (retryAfter == 0) {
            filterChain.doFilter(request, response);
            return;
        }

        if (auditGate.shouldAudit("login-lockout:" + ip)) {
            auditService.throttled(ip, retryAfter, request);
        }
        response.sendRedirect(request.getContextPath() + "/login?error");
    }
}

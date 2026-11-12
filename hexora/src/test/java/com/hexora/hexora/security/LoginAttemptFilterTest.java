package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoginAttemptFilterTest {

    @Test
    void blocksLoginDuringAccountOrIpCooldownWithGeneric429() throws Exception {
        TinyHatSecurityProperties properties = new TinyHatSecurityProperties();
        LoginAttemptService attempts = new LoginAttemptService(properties, Clock.systemUTC());
        for (int attempt = 0; attempt < 5; attempt++) {
            attempts.recordFailure("auditor@example.com", "192.0.2.20");
        }
        AuditService auditService = mock(AuditService.class);
        LoginAttemptFilter filter = new LoginAttemptFilter(
            attempts,
            new ClientIpResolver(properties),
            new RateLimitAuditGate(),
            auditService
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setParameter("email", " AUDITOR@example.com ");
        request.setParameter("password", "correct-password");
        request.setRemoteAddr("192.0.2.20");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            throw new AssertionError("Blocked login must not reach authentication");
        });

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error");
        // Phase 12: a login lockout is THROTTLED; RATE_LIMITED is reserved for token-bucket exhaustion.
        verify(auditService).throttled(
            eq("192.0.2.20"), org.mockito.ArgumentMatchers.longThat(seconds -> seconds > 0),
            any(jakarta.servlet.http.HttpServletRequest.class)
        );
    }
}

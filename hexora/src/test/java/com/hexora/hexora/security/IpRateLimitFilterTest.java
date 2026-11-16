package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class IpRateLimitFilterTest {

    @Test
    void returns429WithRetryAfterAndAggregatesRejectionAudit() throws Exception {
        TinyHatSecurityProperties properties = new TinyHatSecurityProperties();
        properties.getRatelimit().setAuth(new TinyHatSecurityProperties.Bucket(1, 1, 60));
        ClientIpResolver resolver = new ClientIpResolver(properties);
        RateLimitService rateLimitService = new RateLimitService();
        RateLimitAuditGate auditGate = new RateLimitAuditGate();
        AuditService auditService = mock(AuditService.class);
        IpRateLimitFilter filter = new IpRateLimitFilter(
            properties, resolver, mock(LoginAttemptService.class), rateLimitService, auditGate, auditService,
            new ObjectMapper()
        );
        AtomicInteger continued = new AtomicInteger();

        assertThat(filterRequest(filter, "/login", continued).getStatus()).isEqualTo(200);
        MockHttpServletResponse limited = filterRequest(filter, "/login", continued);

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotBlank();
        assertThat(limited.getContentAsString()).contains("RATE_LIMITED");
        assertThat(filterRequest(filter, "/login", continued).getStatus()).isEqualTo(429);
        assertThat(continued.get()).isEqualTo(1);
        verify(auditService).rateLimited(
            eq("auth"), eq("192.0.2.2"), eq("192.0.2.2"),
            any(jakarta.servlet.http.HttpServletRequest.class)
        );
    }

    @Test
    void doesNotConsumeBucketsForStaticResources() throws Exception {
        TinyHatSecurityProperties properties = new TinyHatSecurityProperties();
        properties.getRatelimit().setApiGeneral(new TinyHatSecurityProperties.Bucket(1, 1, 60));
        IpRateLimitFilter filter = new IpRateLimitFilter(
            properties,
            new ClientIpResolver(properties),
            mock(LoginAttemptService.class),
            new RateLimitService(),
            new RateLimitAuditGate(),
            mock(AuditService.class),
            new ObjectMapper()
        );
        AtomicInteger continued = new AtomicInteger();

        assertThat(filterRequest(filter, "/css/app.css", continued).getStatus()).isEqualTo(200);
        assertThat(filterRequest(filter, "/js/app.js", continued).getStatus()).isEqualTo(200);
        assertThat(filterRequest(filter, "/dashboard", continued).getStatus()).isEqualTo(200);
        assertThat(filterRequest(filter, "/dashboard", continued).getStatus()).isEqualTo(429);
    }

    @Test
    void preservesTheGenericLoginFailureResponseDuringLockout() throws Exception {
        TinyHatSecurityProperties properties = new TinyHatSecurityProperties();
        properties.getRatelimit().setAuth(new TinyHatSecurityProperties.Bucket(1, 1, 60));
        LoginAttemptService attempts = mock(LoginAttemptService.class);
        when(attempts.retryAfterSeconds("auditor@example.com", "192.0.2.2")).thenReturn(60L);
        IpRateLimitFilter filter = new IpRateLimitFilter(
            properties,
            new ClientIpResolver(properties),
            attempts,
            new RateLimitService(),
            new RateLimitAuditGate(),
            mock(AuditService.class),
            new ObjectMapper()
        );
        AtomicInteger continued = new AtomicInteger();

        assertThat(filterRequest(filter, "/login", continued).getStatus()).isEqualTo(200);
        MockHttpServletResponse locked = filterRequest(filter, "/login", continued);

        assertThat(locked.getStatus()).isEqualTo(302);
        assertThat(locked.getRedirectedUrl()).isEqualTo("/login?error");
        assertThat(locked.getHeader("Retry-After")).isNull();
        assertThat(continued.get()).isEqualTo(1);
    }

    private MockHttpServletResponse filterRequest(
        IpRateLimitFilter filter,
        String path,
        AtomicInteger continued
    ) throws Exception {
        MockHttpServletRequest request = requestFor(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (servletRequest, servletResponse) -> continued.incrementAndGet());
        return response;
    }

    private MockHttpServletRequest requestFor(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        if ("/login".equals(path)) {
            request.setMethod("POST");
            request.setParameter("email", "auditor@example.com");
        }
        request.setRemoteAddr("192.0.2.2");
        return request;
    }
}

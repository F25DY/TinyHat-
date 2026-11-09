package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

class UserRateLimitFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void appliesASeparateBucketToEachAuthenticatedUser() throws Exception {
        TinyHatSecurityProperties properties = new TinyHatSecurityProperties();
        properties.getRatelimit().setCveSearch(new TinyHatSecurityProperties.Bucket(1, 1, 60));
        TinyHatPrincipal principal = mock(TinyHatPrincipal.class);
        when(principal.getUserId()).thenReturn(33L);
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        AuditService auditService = mock(AuditService.class);
        UserRateLimitFilter filter = new UserRateLimitFilter(
            properties,
            new ClientIpResolver(properties),
            new RateLimitService(),
            new RateLimitAuditGate(),
            auditService,
            new ObjectMapper()
        );
        AtomicInteger continued = new AtomicInteger();

        assertThat(filterRequest(filter, continued).getStatus()).isEqualTo(200);
        MockHttpServletResponse limited = filterRequest(filter, continued);

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotBlank();
        assertThat(continued.get()).isEqualTo(1);
        verify(auditService).rateLimited(
            eq("cve-search"), eq("user:33"), eq("127.0.0.1"),
            any(jakarta.servlet.http.HttpServletRequest.class)
        );
    }

    private MockHttpServletResponse filterRequest(UserRateLimitFilter filter, AtomicInteger continued)
        throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cves/buscar");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (servletRequest, servletResponse) -> continued.incrementAndGet());
        return response;
    }
}

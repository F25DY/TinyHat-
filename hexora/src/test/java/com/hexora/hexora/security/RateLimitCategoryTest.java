package com.hexora.hexora.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RateLimitCategoryTest {

    @Test
    void classifiesConfiguredSensitiveEndpointFamilies() {
        TinyHatSecurityProperties properties = new TinyHatSecurityProperties();

        MockHttpServletRequest login = new MockHttpServletRequest("POST", "/login");
        assertEquals(RateLimitCategory.AUTH, RateLimitCategory.classify(login, properties));
        assertEquals(RateLimitCategory.AUTH, classify("/oauth2/callback/provider", properties));
        assertEquals(RateLimitCategory.API_GENERAL, classify("/login", properties));
        assertEquals(RateLimitCategory.REGISTRO, classify("/registro", properties));
        assertEquals(RateLimitCategory.MFA, classify("/mfa/verify", properties));
        assertEquals(RateLimitCategory.TOKEN, classify("/recuperar-contrasena", properties));
        assertEquals(RateLimitCategory.CVE_SEARCH, classify("/api/cves/buscar", properties));
        assertEquals(RateLimitCategory.NETWORK_DISCOVER, classify("/api/network/discover", properties));
        assertEquals(RateLimitCategory.PDF, classify("/informes/1/pdf", properties));
        assertEquals(RateLimitCategory.POLL, classify("/api/network/events/latest", properties));
        assertEquals(RateLimitCategory.API_GENERAL, classify("/api/network/status", properties));
    }

    private RateLimitCategory classify(String path, TinyHatSecurityProperties properties) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return RateLimitCategory.classify(request, properties);
    }
}

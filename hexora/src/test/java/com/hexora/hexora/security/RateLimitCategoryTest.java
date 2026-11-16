package com.hexora.hexora.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(
            RateLimitCategory.REGISTRO,
            classify("/api/organizaciones/organization-public-id/invitaciones", properties)
        );
        assertEquals(RateLimitCategory.MFA, classify("/mfa/verify", properties));
        assertEquals(RateLimitCategory.TOKEN, classify("/recuperar-contrasena", properties));
        assertEquals(RateLimitCategory.CVE_SEARCH, classify("/api/cves/buscar", properties));
        assertEquals(RateLimitCategory.NETWORK_DISCOVER, classify("/api/network/discover", properties));
        assertEquals(RateLimitCategory.PDF, classify("/informes/1/pdf", properties));
        assertEquals(RateLimitCategory.POLL, classify("/api/network/events/latest", properties));
        // Fase 14 (H-1): las consultas periódicas de la pantalla de red son sondeos; las acciones del usuario no.
        assertEquals(RateLimitCategory.POLL, classify("/api/network/status", properties));
        assertEquals(RateLimitCategory.POLL, classify("/api/network/history", properties));
        assertEquals(RateLimitCategory.POLL, classify("/api/network/discoveries/history", properties));
        assertEquals(RateLimitCategory.API_GENERAL, classify("/api/network/scan", properties));
        // Los archivos de /vendor (Bootstrap, Font Awesome, fuentes) son estáticos: no gastan el cupo de la IP.
        assertTrue(RateLimitCategory.isStaticResource("/vendor/bootstrap/bootstrap.min.css"));
        assertTrue(RateLimitCategory.isStaticResource("/vendor/fonts/fonts.css"));
        assertFalse(RateLimitCategory.isStaticResource("/vendors/x.css"));
        assertFalse(RateLimitCategory.isStaticResource("/api/vendor/x"));
        assertEquals(RateLimitCategory.API_GENERAL, classify("/api/network/detected-devices", properties));
    }

    private RateLimitCategory classify(String path, TinyHatSecurityProperties properties) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return RateLimitCategory.classify(request, properties);
    }
}

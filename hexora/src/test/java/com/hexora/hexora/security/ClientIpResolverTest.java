package com.hexora.hexora.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedAddressFromAnUntrustedPeer() {
        TinyHatSecurityProperties properties = new TinyHatSecurityProperties();
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.10");
        request.addHeader("X-Forwarded-For", "203.0.113.20");

        assertEquals("198.51.100.10", resolver.resolve(request));
    }

    @Test
    void walksForwardedChainOnlyThroughTrustedProxies() {
        TinyHatSecurityProperties properties = new TinyHatSecurityProperties();
        properties.getTrustedProxies().add("10.0.0.0/8");
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.20, 10.0.0.6");

        assertEquals("203.0.113.20", resolver.resolve(request));
    }
}

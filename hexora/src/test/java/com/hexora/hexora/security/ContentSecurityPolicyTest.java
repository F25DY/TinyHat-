package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContentSecurityPolicyTest {

    @Test
    void derivesConnectSourcesFromTheExplicitWebSocketOrigins() {
        String directives = ContentSecurityPolicy.directives(
            List.of("https://localhost:8443", "https://127.0.0.1:8443", "https://localhost:8443"));

        assertThat(directives)
            .contains("connect-src 'self' wss://localhost:8443 wss://127.0.0.1:8443;")
            .contains("default-src 'self'; script-src 'self'; style-src 'self';")
            .contains("img-src 'self' data:")
            .contains("frame-ancestors 'none'; base-uri 'self'; form-action 'self'")
            .doesNotContain("'unsafe-inline'")
            .doesNotContain("*");
    }

    @Test
    void mapsPlainHttpOriginsToWsAndRejectsNonOriginValues() {
        assertThat(ContentSecurityPolicy.webSocketSource("http://127.0.0.1:8080")).isEqualTo("ws://127.0.0.1:8080");
        assertThat(ContentSecurityPolicy.webSocketSource("https://hexora.example.com")).isEqualTo("wss://hexora.example.com");

        assertThatThrownBy(() -> ContentSecurityPolicy.webSocketSource("*"))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ContentSecurityPolicy.webSocketSource("https://localhost:8443/ws"))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ContentSecurityPolicy.webSocketSource("wss://localhost:8443"))
            .isInstanceOf(IllegalStateException.class);
    }
}

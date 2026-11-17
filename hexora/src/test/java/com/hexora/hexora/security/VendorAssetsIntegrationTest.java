package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hexora.test.IsolatedMySqlTestDatabase;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Bootstrap, Font Awesome y las fuentes se sirven desde {@code /vendor/**} para cumplir la CSP (sin CDN). Las pantallas
 * de acceso los necesitan antes de iniciar sesión, así que deben ser públicos, y como una página pide unos 20
 * archivos no pueden consumir el cupo de peticiones de la IP.
 */
@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.clean-disabled=true",
    "hexora.network.discovery-enabled=false",
    "hexora.network.monitor-interval-ms=2147483647",
    "hexora.network.retention-cleanup-interval-ms=2147483647"
})
@AutoConfigureMockMvc
class VendorAssetsIntegrationTest extends IsolatedMySqlTestDatabase {

    private static final List<String> PUBLIC_ASSETS = List.of(
        "/vendor/bootstrap/bootstrap.min.css",
        "/vendor/fontawesome/css/all.min.css",
        "/vendor/fontawesome/webfonts/fa-solid-900.woff2",
        "/vendor/fontawesome/webfonts/fa-regular-400.woff2",
        "/vendor/fonts/fonts.css",
        "/vendor/fonts/plus-jakarta-sans-latin-400-normal.woff2",
        "/vendor/fonts/jetbrains-mono-latin-400-normal.woff2",
        "/css/responsive-tablet.css",
        "/js/login-effects.js",
        "/js/dashboard-greeting.js"
    );

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousUsersCanLoadTheAssetsTheLoginPageNeeds() throws Exception {
        for (String asset : PUBLIC_ASSETS) {
            mockMvc.perform(get(asset).with(request -> {
                    request.setRemoteAddr("198.51.100.10");
                    return request;
                }))
                .andExpect(status().isOk());
        }
    }

    @Test
    void stylesheetsAndScriptsAreServedWithTheRightContentType() throws Exception {
        mockMvc.perform(get("/vendor/bootstrap/bootstrap.min.css"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/css")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mockMvc.perform(get("/vendor/fontawesome/webfonts/fa-solid-900.woff2"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void staticAssetsDoNotConsumeTheRateLimit() throws Exception {
        // Una página pide unos 20 archivos; recargarla varias veces no debe agotar el cupo de la IP.
        AtomicInteger limited = new AtomicInteger();
        for (int i = 0; i < 250; i++) {
            MvcResult result = mockMvc.perform(get("/vendor/bootstrap/bootstrap.min.css").with(request -> {
                request.setRemoteAddr("198.51.100.20");
                return request;
            })).andReturn();
            if (result.getResponse().getStatus() == 429) {
                limited.incrementAndGet();
            }
        }
        assertThat(limited.get()).as("respuestas 429 sobre archivos estáticos").isZero();
    }

    @Test
    void theVendorFolderCannotBeUsedToReadOtherFiles() throws Exception {
        for (String path : List.of(
            "/vendor/", "/vendor", "/vendor/../application.properties", "/vendor/%2e%2e/application.properties",
            "/vendor/..%2fapplication.properties", "/vendor/bootstrap/", "/vendor/fontawesome/webfonts/",
            "/vendor/nonexistent.css",
            // el listado de directorios tampoco debe verse en las demás carpetas estáticas
            "/css/", "/js/", "/imagen/")) {
            MvcResult result = mockMvc.perform(get(path)).andReturn();
            assertThat(result.getResponse().getContentAsString())
                .as(path + " no debe devolver el contenido de la configuración")
                .doesNotContain("spring.datasource")
                .doesNotContain("tinyhat.security");
            assertThat(result.getResponse().getStatus())
                .as("%s -> estado %d, Location=%s, cuerpo=[%s]", path, result.getResponse().getStatus(),
                    result.getResponse().getHeader("Location"),
                    result.getResponse().getContentAsString().replaceAll("\\s+", " ")
                        .substring(0, Math.min(160, result.getResponse().getContentAsString().replaceAll("\\s+", " ").length())))
                .isNotEqualTo(200);
        }
    }
}

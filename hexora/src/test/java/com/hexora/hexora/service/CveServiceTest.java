package com.hexora.hexora.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.hexora.hexora.security.NvdRateLimitService;
import com.hexora.hexora.security.TinyHatNvdProperties;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.hexora.hexora.model.Cve;
import com.hexora.hexora.repository.CveRepository;

class CveServiceTest {

    @Test
    void usesOneCvssVersionForScoreAndImpactWhenSeveralAreAvailable() {
        TinyHatNvdProperties properties = new TinyHatNvdProperties();
        CveService service = new CveService(
            mock(CveRepository.class), mock(TraductorService.class), properties, new NvdRateLimitService(properties)
        );
        Cve cve = new Cve();
        Map<String, Object> cvss31 = Map.of(
                "type", "Primary",
                "cvssData", Map.of(
                        "baseScore", 7.5,
                        "baseSeverity", "HIGH",
                        "confidentialityImpact", "HIGH",
                        "integrityImpact", "LOW",
                        "availabilityImpact", "NONE"),
                "exploitabilityScore", 3.9);
        Map<String, Object> cvss40 = Map.of(
                "type", "Primary",
                "cvssData", Map.of("baseScore", 9.8, "baseSeverity", "CRITICAL"));
        Map<String, Object> datos = Map.of("metrics", Map.of(
                "cvssMetricV40", List.of(cvss40),
                "cvssMetricV31", List.of(cvss31)));

        service.leerMetricas(datos, cve);

        assertEquals(new BigDecimal("7.5"), cve.getPuntajeCvss());
        assertEquals("HIGH", cve.getSeveridad());
        assertEquals("3.1", cve.getVersionCvss());
        assertEquals("HIGH", cve.getImpactoConfidencialidad());
        assertEquals("LOW", cve.getImpactoIntegridad());
        assertEquals("NONE", cve.getImpactoDisponibilidad());
        assertEquals(new BigDecimal("3.9"), cve.getPuntajeExplotabilidad());
    }

    @Test
    void capsNvdCallsAcrossSearchesAtConfiguredGlobalBudget() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rest/json/cves/2.0", exchange -> {
            requests.incrementAndGet();
            byte[] body = "{\"vulnerabilities\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            CveRepository repository = mock(CveRepository.class);
            when(repository.findTop10ByCodigoCveContainingIgnoreCaseOrDescripcionContainingIgnoreCase(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()
            )).thenReturn(List.of());
            TinyHatNvdProperties properties = new TinyHatNvdProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/rest/json/cves/2.0");
            properties.setRequestsWithoutKey(2);
            properties.setWindowSeconds(3600);
            CveService service = new CveService(
                repository,
                mock(TraductorService.class),
                properties,
                new NvdRateLimitService(properties),
                RestClient.builder()
                    .baseUrl(properties.getBaseUrl())
                    .requestFactory(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()))
                    .build()
            );

            service.buscar("first query");
            service.buscar("second query");
            service.buscar("third query");

            assertEquals(2, requests.get());
        } finally {
            server.stop(0);
        }
    }
}
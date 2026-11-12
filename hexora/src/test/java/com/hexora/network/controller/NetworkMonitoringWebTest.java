package com.hexora.network.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.hexora.hexora.HexoraApplication;
import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.security.SessionActivityFilter;
import com.hexora.test.IsolatedMySqlTestDatabase;
import java.util.UUID;
import java.util.List;
import java.sql.Statement;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.hexora.network.service.NetworkScanLockService;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@SpringBootTest(classes = HexoraApplication.class, properties = "hexora.network.discovery-enabled=false")
@AutoConfigureMockMvc
class NetworkMonitoringWebTest extends IsolatedMySqlTestDatabase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NetworkScanLockService scanLockService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserDetailsService userDetailsService;

    private TinyHatPrincipal principal;
    private String projectPublicId;
    private long projectId;

    @BeforeEach
    void createAuditorPrincipal() {
        jdbcTemplate.update("UPDATE capacidad_cuenta SET habilitada = TRUE WHERE cuenta_id = 1");
        jdbcTemplate.update(
            "INSERT INTO capacidad_cuenta (cuenta_id, capacidad, habilitada) "
                + "VALUES (1, 'monitoreo_continuo', TRUE) "
                + "ON DUPLICATE KEY UPDATE habilitada = TRUE");
        String email = "network-test-" + UUID.randomUUID() + "@example.test";
        jdbcTemplate.update(
            "INSERT INTO usuario "
                + "(public_id, cuenta_id, rol, estado, email_normalizado, password_hash) "
                + "VALUES (?, 1, 'AUDITOR', 'ACTIVO', ?, ?)",
            UUID.randomUUID().toString(),
            email,
            passwordEncoder.encode("network-test-password"));
        principal = (TinyHatPrincipal) userDetailsService.loadUserByUsername(email);
        projectPublicId = UUID.randomUUID().toString();
        long organizationId = insertWithGeneratedId(
            "INSERT INTO organizacion_cliente (public_id, nombre, cuenta_id) VALUES (?, ?, 1)",
            UUID.randomUUID().toString(),
            "Network test organization");
        projectId = insertWithGeneratedId(
            "INSERT INTO auditoria "
                + "(public_id, estado, fecha_fin, fecha_inico, nombre, tipo_auditoria, "
                + "organizacion_cliente_id, cuenta_id) VALUES (?, 'ACTIVA', CURRENT_DATE, CURRENT_DATE, ?, ?, ?, 1)",
            projectPublicId,
            "Network test project",
            "Security",
            organizationId);
        jdbcTemplate.update(
            "INSERT INTO asignacion_auditor_proyecto (usuario_id, proyecto_id, cuenta_id) VALUES (?, ?, 1)",
            principal.getUserId(),
            projectId);
    }

    @Test
    void servesAnIndependentMonitoringPage() throws Exception {
        performAsAuditor(get("/network-monitoring"))
            .andExpect(status().isOk())
            .andExpect(view().name("network-monitoring"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Monitorización de red")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Dispositivos conectados")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Ver historial")));
    }

    @Test
    void rejectsAnUnauthenticatedWebSocketHandshake() throws Exception {
        mockMvc.perform(get("/ws/network")
                .header("Origin", "https://localhost:8443")
                .header("Upgrade", "websocket")
                .header("Connection", "Upgrade")
                .header("Sec-WebSocket-Version", "13")
                .header("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ=="))
            .andExpect(status().is3xxRedirection())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/login"));
    }

    @Test
    void servesDiscoveryHistoryInItsOwnPage() throws Exception {
        performAsAuditor(get("/network-monitoring/history"))
            .andExpect(status().isOk())
            .andExpect(view().name("network-monitoring-history"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Historial de detecciones")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Disponibilidad estimada")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("history-project-select")));
    }

    @Test
    void rendersProjectAndOrganizationNamesAsEscapedText() throws Exception {
        String payload = "<img src=\"http://127.0.0.1:9/track\">";
        Long organizationId = jdbcTemplate.queryForObject(
            "SELECT organizacion_cliente_id FROM auditoria WHERE id_auditoria = ?",
            Long.class,
            projectId);
        jdbcTemplate.update(
            "UPDATE auditoria SET nombre = ? WHERE id_auditoria = ?",
            payload,
            projectId);
        jdbcTemplate.update(
            "UPDATE organizacion_cliente SET nombre = ? WHERE id_empresa = ?",
            payload,
            organizationId);

        performAsAuditor(get("/proyectos/" + projectPublicId))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;img src=")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString(payload))));
    }

    @Test
    void exposesDashboardStatus() throws Exception {
        performAsAuditor(get("/api/network/status").param("projectPublicId", projectPublicId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hostsMonitored").isNumber())
            .andExpect(jsonPath("$.hosts").isArray());
    }

    @Test
    void exposesTheDetectedDeviceInventory() throws Exception {
        performAsAuditor(get("/api/network/detected-devices").param("projectPublicId", projectPublicId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void exposesTheGatewayEndpoint() throws Exception {
        performAsAuditor(get("/api/network/gateway").param("projectPublicId", projectPublicId))
            .andExpect(status().is2xxSuccessful());
    }

    @Test
    void exposesDiscoverySnapshotsAndTheLatestDetection() throws Exception {
        performAsAuditor(get("/api/network/discoveries/history").param("projectPublicId", projectPublicId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        performAsAuditor(get("/api/network/discoveries/latest").param("projectPublicId", projectPublicId))
            .andExpect(status().is2xxSuccessful());
    }

    @Test
    void doesNotShareDiscoveryDataBetweenProjectsWithTheSameAuthorizedRange() throws Exception {
        long otherProjectId = createUnassignedProject("Other network project");
        insertAuthorizedRange(projectId, "192.168.10.0/24");
        long otherDeviceId = insertWithGeneratedId(
            "INSERT INTO network_detected_device "
                + "(address, mac_address, host_name, detection_method, subnet, status, consecutive_misses, "
                + "first_detected_at, last_detected_at, last_status_changed_at, gateway, gateway_address, "
                + "cuenta_id, public_id, proyecto_id) "
                + "VALUES ('192.168.10.20', '00:11:22:33:44:55', 'Other project device', 'ARP', "
                + "'192.168.10.0/24', 'ONLINE', 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), "
                + "CURRENT_TIMESTAMP(6), FALSE, NULL, 1, ?, ?)",
            UUID.randomUUID().toString(),
            otherProjectId);
        jdbcTemplate.update(
            "INSERT INTO network_discovery_event "
                + "(detected_device_id, address, device_name, event_type, description, created_at, cuenta_id, public_id) "
                + "VALUES (?, '192.168.10.20', 'Other project device', 'DEVICE_DETECTED', 'test event', "
                + "CURRENT_TIMESTAMP(6), 1, ?)",
            otherDeviceId,
            UUID.randomUUID().toString());
        jdbcTemplate.update(
            "INSERT INTO network_discovery_snapshot "
                + "(subnet, source, observed_at, cuenta_id, public_id, proyecto_id) "
                + "VALUES ('192.168.10.0/24', 'test', CURRENT_TIMESTAMP(6), 1, ?, ?)",
            UUID.randomUUID().toString(),
            otherProjectId);

        performAsAuditor(get("/api/network/detected-devices").param("projectPublicId", projectPublicId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
        performAsAuditor(get("/api/network/discoveries/history").param("projectPublicId", projectPublicId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
        performAsAuditor(get("/api/network/status").param("projectPublicId", projectPublicId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recentEvents").isEmpty());
        performAsAuditor(delete("/api/network/history")
                .param("projectPublicId", projectPublicId).with(csrf()))
            .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM network_discovery_event WHERE address = '192.168.10.20'",
            Integer.class));
    }

    @Test
    void rejectsHistoryAndHostMutationsForAHostOwnedByAnotherProject() throws Exception {
        long otherProjectId = createUnassignedProject("Host owner project");
        long hostId = insertWithGeneratedId(
            "INSERT INTO network_host "
                + "(display_name, address, status, latency_ms, last_checked_at, enabled, managed_by_ui, "
                + "consecutive_failures, cuenta_id, proyecto_id, public_id) "
                + "VALUES ('Other project host', '192.168.10.30', 'OFFLINE', NULL, NULL, TRUE, TRUE, 0, 1, ?, ?)",
            otherProjectId,
            UUID.randomUUID().toString());
        jdbcTemplate.update(
            "INSERT INTO monitored_port (network_host_id, port, enabled, cuenta_id, public_id) "
                + "VALUES (?, 80, TRUE, 1, ?)",
            hostId,
            UUID.randomUUID().toString());
        long scanId = insertWithGeneratedId(
            "INSERT INTO network_scan "
                + "(host_id, status, host_status, latency_ms, ports_scanned, started_at, completed_at, cuenta_id, public_id) "
                + "VALUES (?, 'COMPLETED', 'OFFLINE', NULL, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 1, ?)",
            hostId,
            UUID.randomUUID().toString());

        performAsAuditor(get("/api/network/history")
                .param("projectPublicId", projectPublicId)
                .param("hostId", String.valueOf(hostId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
        performAsAuditor(post("/api/network/devices/" + hostId + "/ports")
                .param("projectPublicId", projectPublicId).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"port\":81}"))
            .andExpect(status().isNotFound());
        performAsAuditor(put("/api/network/devices/" + hostId + "/ports/80")
                .param("projectPublicId", projectPublicId).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
            .andExpect(status().isNotFound());
        performAsAuditor(delete("/api/network/devices/" + hostId + "/ports/80")
                .param("projectPublicId", projectPublicId).with(csrf()))
            .andExpect(status().isNotFound());
        performAsAuditor(delete("/api/network/devices/" + hostId)
                .param("projectPublicId", projectPublicId).with(csrf()))
            .andExpect(status().isNotFound());
        performAsAuditor(delete("/api/network/history/scans")
                .param("projectPublicId", projectPublicId).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scanIds\":[" + scanId + "]}"))
            .andExpect(status().isNotFound());

        org.junit.jupiter.api.Assertions.assertTrue(jdbcTemplate.queryForObject(
            "SELECT enabled FROM network_host WHERE id = ?",
            Boolean.class,
            hostId));
        org.junit.jupiter.api.Assertions.assertTrue(jdbcTemplate.queryForObject(
            "SELECT enabled FROM monitored_port WHERE network_host_id = ? AND port = 80",
            Boolean.class,
            hostId));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM network_scan WHERE id = ?",
            Integer.class,
            scanId));
    }

    @Test
    void allowsOnlyOneConcurrentCooldownGrantAndReopensAfterExpiry() throws Exception {
        int attempts = 8;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> attemptsResult = new ArrayList<>();
        try {
            for (int index = 0; index < attempts; index++) {
                attemptsResult.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Cooldown test start signal timed out");
                    }
                    return scanLockService.acquireProjectCooldown(1L, projectId, principal.getUserId());
                }));
            }
            org.junit.jupiter.api.Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int grants = 0;
            for (Future<Boolean> result : attemptsResult) {
                if (result.get(5, TimeUnit.SECONDS)) grants++;
            }
            org.junit.jupiter.api.Assertions.assertEquals(1, grants);
            org.junit.jupiter.api.Assertions.assertFalse(
                scanLockService.acquireProjectCooldown(1L, projectId, principal.getUserId()));

            jdbcTemplate.update(
                "UPDATE bloqueo_escaneo SET inicio = '2000-01-01 00:00:00' WHERE proyecto_id = ? AND cuenta_id = 1",
                projectId);
            org.junit.jupiter.api.Assertions.assertTrue(
                scanLockService.acquireProjectCooldown(1L, projectId, principal.getUserId()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void listsOnlyProjectsVisibleToTheAuthenticatedAuditor() throws Exception {
        performAsAuditor(get("/api/network/projects"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].publicId").value(projectPublicId))
            .andExpect(jsonPath("$[0].name").value("Network test project"))
            .andExpect(jsonPath("$[0].continuousMonitoringEnabled").value(false));
    }

    @Test
    void enablesAndDisablesContinuousMonitoringOnlyForAnAssignedProject() throws Exception {
        performAsAuditor(put("/api/network/projects/" + projectPublicId + "/continuous-monitoring").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.continuousMonitoringEnabled").value(true));
        org.junit.jupiter.api.Assertions.assertTrue(jdbcTemplate.queryForObject(
            "SELECT monitoreo_continuo_habilitado FROM auditoria WHERE id_auditoria = ?",
            Boolean.class,
            projectId));

        performAsAuditor(put("/api/network/projects/" + projectPublicId + "/continuous-monitoring").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.continuousMonitoringEnabled").value(false));
        org.junit.jupiter.api.Assertions.assertFalse(jdbcTemplate.queryForObject(
            "SELECT monitoreo_continuo_habilitado FROM auditoria WHERE id_auditoria = ?",
            Boolean.class,
            projectId));
    }

    @Test
    void doesNotAllowContinuousMonitoringWithoutTheAccountCapability() throws Exception {
        jdbcTemplate.update(
            "UPDATE capacidad_cuenta SET habilitada = FALSE "
                + "WHERE cuenta_id = 1 AND capacidad = 'monitoreo_continuo'");

        performAsAuditor(put("/api/network/projects/" + projectPublicId + "/continuous-monitoring").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}"))
            .andExpect(status().isForbidden());

        org.junit.jupiter.api.Assertions.assertFalse(jdbcTemplate.queryForObject(
            "SELECT monitoreo_continuo_habilitado FROM auditoria WHERE id_auditoria = ?",
            Boolean.class,
            projectId));
    }

    @Test
    void rejectsAProjectScanWithoutAnAuthorizedRangeAndAuditsTheRejectedTarget() throws Exception {
        long hostId = insertWithGeneratedId(
            "INSERT INTO network_host "
                + "(display_name, address, status, enabled, managed_by_ui, consecutive_failures, "
                + "cuenta_id, proyecto_id, public_id) "
                + "VALUES ('Scoped test host', '192.168.10.31', 'OFFLINE', TRUE, TRUE, 0, 1, ?, ?)",
            projectId,
            UUID.randomUUID().toString());

        performAsAuditor(post("/api/network/scan").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostId\":" + hostId + ",\"projectPublicId\":\"" + projectPublicId + "\"}"))
            .andExpect(status().isUnprocessableEntity());

        org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE evento = 'SCAN_REJECTED' "
                + "AND recurso_public_id = ? AND usuario_id = ? "
                + "AND JSON_UNQUOTE(JSON_EXTRACT(detalle, '$.cidr')) = '192.168.10.31/32'",
            Integer.class,
            projectPublicId,
            principal.getUserId()));
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM network_scan WHERE host_id = ?",
            Integer.class,
            hostId));
    }

    @Test
    void rejectsNetworkApiCallsWhenTheNetworkCapabilityIsDisabled() throws Exception {
        jdbcTemplate.update(
            "UPDATE capacidad_cuenta SET habilitada = FALSE WHERE cuenta_id = 1 AND capacidad = 'modulo_red'");
        int existingScans = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM network_scan", Integer.class);

        performAsAuditor(get("/api/network/projects"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("ACCESS_DENIED"))
            .andExpect(jsonPath("$.correlationId").isString());

        org.junit.jupiter.api.Assertions.assertEquals(existingScans, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM network_scan",
            Integer.class));
    }

    @Test
    void declaresAndListsAuthorizedRangesOnlyForAVisibleProject() throws Exception {
        performAsAuditor(post("/api/network/authorized-ranges").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectPublicId\":\"" + projectPublicId
                    + "\",\"cidr\":\"192.168.10.0/24\",\"authorizationReference\":\"Approval-42\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cidr").value("192.168.10.0/24"))
            .andExpect(jsonPath("$.projectPublicId").value(projectPublicId));

        performAsAuditor(get("/api/network/projects/" + projectPublicId + "/authorized-ranges"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].authorizationReference").value("Approval-42"));
    }

    @Test
    void doesNotExposeRangesForProjectsOutsideTheAuditorsAssignments() throws Exception {
        performAsAuditor(get("/api/network/projects/" + UUID.randomUUID() + "/authorized-ranges"))
            .andExpect(status().isNotFound());
    }

    @Test
    void rejectsProjectScopedReadsWithoutAProject() throws Exception {
        performAsAuditor(get("/api/network/status"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsHistoryLimitsAboveOneHundredWithAGenericValidationError() throws Exception {
        performAsAuditor(get("/api/network/history")
                .param("limit", "101")
                .param("projectPublicId", projectPublicId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.correlationId").isString());
    }

    @Test
    void monitoringPageShowsTheAutomaticScanActionAndDetectedInventory() throws Exception {
        performAsAuditor(get("/network-monitoring"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Detectar dispositivos en la red")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Método de detección")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Dispositivos conectados")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("ACTUALIZACIÓN AUTOMÁTICA")));
    }

    @Test
    void rejectsAnOversizedRangeBeforeStartingAScan() throws Exception {
        performAsAuditor(post("/api/network/scan").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostId\":1,\"startPort\":1,\"endPort\":129}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.correlationId").isString());
    }

    @Test
    void refusesToRegisterDevicesOutsidePrivateIpv4Ranges() throws Exception {
        performAsAuditor(post("/api/network/hosts").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"External host\",\"address\":\"8.8.8.8\",\"ports\":[443]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.correlationId").isString());
    }

    @Test
    void mapsDeviceRegistrationPostAndReturnsValidationInsteadOfMethodNotAllowed() throws Exception {
        performAsAuditor(post("/api/network/devices").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"address\":\"192.168.1.25\",\"ports\":[443]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.correlationId").isString());
    }

    @Test
    void exposesExpectedMethodsForDeviceAndPortEndpoints() throws Exception {
        performAsAuditor(options("/api/network/devices"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Allow", org.hamcrest.Matchers.containsString("POST")));

        performAsAuditor(options("/api/network/devices/1/ports/443"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Allow", org.hamcrest.Matchers.containsString("DELETE")));
        performAsAuditor(options("/api/network/devices/1/ports/443"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Allow", org.hamcrest.Matchers.containsString("PUT")));

        performAsAuditor(options("/api/network/history"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Allow", org.hamcrest.Matchers.containsString("DELETE")));
        performAsAuditor(options("/api/network/history/scans"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Allow", org.hamcrest.Matchers.containsString("DELETE")));
        performAsAuditor(options("/api/network/discover"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Allow", org.hamcrest.Matchers.containsString("POST")));
    }

    @Test
    void mapsDevicePortAndDeleteActionsToTheirHttpMethods() throws Exception {
        String missingHostId = "9223372036854775807";

        performAsAuditor(post("/api/network/devices/" + missingHostId + "/ports")
                .param("projectPublicId", projectPublicId).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"port\":8080}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").isString());

        performAsAuditor(put("/api/network/devices/" + missingHostId + "/ports/8080")
                .param("projectPublicId", projectPublicId).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").isString());

        performAsAuditor(delete("/api/network/devices/" + missingHostId + "/ports/8080")
                .param("projectPublicId", projectPublicId).with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").isString());

        performAsAuditor(delete("/api/network/devices/" + missingHostId)
                .param("projectPublicId", projectPublicId).with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void reportsReceivedAndExpectedMethodsForMethodNotAllowed() throws Exception {
        performAsAuditor(put("/api/network/devices").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.path").value("/api/network/devices"))
            .andExpect(jsonPath("$.receivedMethod").value("PUT"))
            .andExpect(jsonPath("$.allowedMethods").value(org.hamcrest.Matchers.containsString("POST")));
    }

    private ResultActions performAsAuditor(MockHttpServletRequestBuilder request) throws Exception {
        long now = System.currentTimeMillis();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionActivityFilter.CREATED_AT_ATTRIBUTE, now);
        session.setAttribute(SessionActivityFilter.LAST_ACTIVITY_ATTRIBUTE, now);
        return mockMvc.perform(request
            .session(session)
            .with(user(principal)));
    }

    private long insertWithGeneratedId(String sql, Object... values) {
        var keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            return statement;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private long createUnassignedProject(String name) {
        long organizationId = insertWithGeneratedId(
            "INSERT INTO organizacion_cliente (public_id, nombre, cuenta_id) VALUES (?, ?, 1)",
            UUID.randomUUID().toString(),
            name + " organization");
        return insertWithGeneratedId(
            "INSERT INTO auditoria "
                + "(public_id, estado, fecha_fin, fecha_inico, nombre, tipo_auditoria, "
                + "organizacion_cliente_id, cuenta_id) VALUES (?, 'ACTIVA', CURRENT_DATE, CURRENT_DATE, ?, ?, ?, 1)",
            UUID.randomUUID().toString(),
            name,
            "Security",
            organizationId);
    }

    private void insertAuthorizedRange(long authorizedProjectId, String cidr) {
        jdbcTemplate.update(
            "INSERT INTO rango_autorizado "
                + "(cuenta_id, proyecto_id, cidr, referencia_autorizacion, declarado_por, declarado_en) "
                + "VALUES (1, ?, ?, 'test approval', ?, CURRENT_TIMESTAMP(6))",
            authorizedProjectId,
            cidr,
            principal.getUserId());
    }
}

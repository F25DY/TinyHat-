package com.hexora.network.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hexora.hexora.HexoraApplication;
import com.hexora.hexora.security.SessionActivityFilter;
import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.network.service.LocalNetworkDiscoveryService;
import com.hexora.network.service.NetworkProbe;
import com.hexora.network.service.NetworkProbeResult;
import com.hexora.test.IsolatedMySqlTestDatabase;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * F12-CA1 for the network events: RANGE_DECLARED, SCAN_STARTED and SCAN_COMPLETED. The probe is a double,
 * so no packet leaves the machine (rule R6).
 */
@SpringBootTest(classes = HexoraApplication.class, properties = {
    "hexora.network.discovery-enabled=false",
    "tinyhat.security.ratelimit.network-discover.capacity=1000",
    "tinyhat.security.ratelimit.network-discover.refill-tokens=1000",
    "tinyhat.security.ratelimit.api-general.capacity=100000",
    "tinyhat.security.ratelimit.api-general.refill-tokens=100000"
})
@AutoConfigureMockMvc
class NetworkAuditEventsIntegrationTest extends IsolatedMySqlTestDatabase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserDetailsService userDetailsService;

    @MockitoBean private NetworkProbe probe;
    @MockitoBean private LocalNetworkDiscoveryService localDiscovery;

    private TinyHatPrincipal principal;
    private String projectPublicId;
    private long projectId;

    @BeforeEach
    void fixture() {
        jdbc.update("INSERT INTO capacidad_cuenta (cuenta_id, capacidad, habilitada) VALUES (1, 'modulo_red', TRUE) "
            + "ON DUPLICATE KEY UPDATE habilitada = TRUE");
        String email = "net-audit-" + UUID.randomUUID() + "@example.test";
        jdbc.update("INSERT INTO usuario (public_id, cuenta_id, rol, estado, email_normalizado, password_hash) "
                + "VALUES (?, 1, 'AUDITOR', 'ACTIVO', ?, ?)",
            UUID.randomUUID().toString(), email, passwordEncoder.encode("network-audit-password"));
        principal = (TinyHatPrincipal) userDetailsService.loadUserByUsername(email);
        projectPublicId = UUID.randomUUID().toString();
        long organizationId = insert("INSERT INTO organizacion_cliente (public_id, nombre, cuenta_id) VALUES (?, ?, 1)",
            UUID.randomUUID().toString(), "Org de auditoría de red");
        projectId = insert("INSERT INTO auditoria (public_id, estado, fecha_fin, fecha_inico, nombre, tipo_auditoria, "
                + "organizacion_cliente_id, cuenta_id) VALUES (?, 'ACTIVA', CURRENT_DATE, CURRENT_DATE, ?, ?, ?, 1)",
            projectPublicId, "Proyecto de auditoría de red", "Seguridad", organizationId);
        jdbc.update("INSERT INTO asignacion_auditor_proyecto (usuario_id, proyecto_id, cuenta_id) VALUES (?, ?, 1)",
            principal.getUserId(), projectId);
        when(localDiscovery.activeInterfaceCidr()).thenReturn("192.168.77.0/24");
        when(probe.isReachable(any(), anyInt())).thenReturn(false);
        when(probe.probePort(any(), anyInt(), anyInt())).thenReturn(new NetworkProbeResult(false, 0));
    }

    @Test
    void declaringARangeAndScanningAHostLeaveAttributableAuditRows() throws Exception {
        long hostId = insert("INSERT INTO network_host (display_name, address, status, enabled, managed_by_ui, "
                + "consecutive_failures, cuenta_id, proyecto_id, public_id) "
                + "VALUES ('Host de auditoría', '192.168.77.31', 'OFFLINE', TRUE, TRUE, 0, 1, ?, ?)",
            projectId, UUID.randomUUID().toString());

        as(post("/api/network/authorized-ranges").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"projectPublicId\":\"" + projectPublicId
                + "\",\"cidr\":\"192.168.77.0/24\",\"authorizationReference\":\"Carta-2026-11\"}"))
            .andExpect(status().isCreated());
        assertRow("RANGE_DECLARED");

        as(post("/api/network/scan").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"hostId\":" + hostId + ",\"projectPublicId\":\"" + projectPublicId
                + "\",\"startPort\":80,\"endPort\":82}"))
            .andExpect(status().isCreated());
        assertRow("SCAN_STARTED");
        assertRow("SCAN_COMPLETED");
        assertThat(jdbc.queryForObject(
            "SELECT JSON_UNQUOTE(JSON_EXTRACT(detalle, '$.cidr')) FROM audit_event "
                + "WHERE evento = 'SCAN_COMPLETED' AND recurso_public_id = ?", String.class, projectPublicId))
            .isEqualTo("192.168.77.31/32");
    }

    private void assertRow(String event) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT usuario_id, cuenta_id, ip, correlation_id FROM audit_event "
                + "WHERE evento = ? AND recurso_public_id = ? AND usuario_id = ?",
            event, projectPublicId, principal.getUserId());
        assertThat(rows).as("rows for %s", event).hasSize(1);
        assertThat(((Number) rows.get(0).get("cuenta_id")).longValue()).isEqualTo(principal.getCuentaId());
        assertThat(rows.get(0).get("ip")).isNotNull();
        assertThat(rows.get(0).get("correlation_id")).isNotNull();
    }

    private ResultActions as(MockHttpServletRequestBuilder request) throws Exception {
        long now = System.currentTimeMillis();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionActivityFilter.CREATED_AT_ATTRIBUTE, now);
        session.setAttribute(SessionActivityFilter.LAST_ACTIVITY_ATTRIBUTE, now);
        return mockMvc.perform(request.session(session).with(user(principal)));
    }

    private long insert(String sql, Object... values) {
        var keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}

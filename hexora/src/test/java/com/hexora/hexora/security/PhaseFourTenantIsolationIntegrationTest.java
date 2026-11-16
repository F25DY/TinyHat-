package com.hexora.hexora.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexora.network.model.NetworkHost;
import com.hexora.network.model.NetworkHostStatus;
import com.hexora.network.model.NetworkScan;
import com.hexora.network.model.NetworkScanStatus;
import com.hexora.network.repository.NetworkHostRepository;
import com.hexora.network.repository.NetworkScanRepository;
import com.hexora.network.service.NetworkMonitoringService;
import com.hexora.hexora.model.AsignacionAuditorProyecto;
import com.hexora.hexora.model.AsignacionAuditorProyectoId;
import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.Empresa;
import com.hexora.hexora.model.Usuario;
import com.hexora.hexora.repository.AsignacionAuditorProyectoRepository;
import com.hexora.hexora.repository.AuditoriaRepository;
import com.hexora.hexora.repository.EmpresaRepository;
import com.hexora.hexora.repository.UsuarioRepository;
import com.hexora.hexora.service.PermissionService;
import com.hexora.test.IsolatedMySqlTestDatabase;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@SpringBootTest(properties = {
    "hexora.network.discovery-enabled=false",
    "hexora.network.monitor-interval-ms=3600000",
    "hexora.network.discovery-interval-ms=3600000",
    "hexora.network.retention-cleanup-interval-ms=3600000"
})
class PhaseFourTenantIsolationIntegrationTest extends IsolatedMySqlTestDatabase {

    @Autowired
    private TenantTaskExecutor tenantTaskExecutor;

    @Autowired
    private NetworkHostRepository hostRepository;

    @Autowired
    private NetworkScanRepository scanRepository;

    @Autowired
    private NetworkMonitoringService monitoringService;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AsignacionAuditorProyectoRepository asignacionRepository;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void hibernateFilterScopesKeyLookupsAndWritesUseOnlyTheActiveTenant() {
        long secondTenantId = createActiveAccount();
        AtomicLong firstTenantHostId = new AtomicLong();

        tenantTaskExecutor.execute(1L, () -> {
            NetworkHost host = newHost("tenant-one-" + UUID.randomUUID(), "192.168.10.10");
            hostRepository.save(host);
            firstTenantHostId.set(host.getId());
        });

        tenantTaskExecutor.execute(secondTenantId, () -> {
            assertTrue(hostRepository.findAll().isEmpty());
            assertFalse(hostRepository.findById(firstTenantHostId.get()).isPresent());
        });

        tenantTaskExecutor.execute(1L, () ->
            assertTrue(hostRepository.findById(firstTenantHostId.get()).isPresent()));

        assertThrows(InvalidDataAccessApiUsageException.class, () ->
            tenantTaskExecutor.execute(secondTenantId, () -> {
                NetworkHost spoofedHost = newHost("spoofed-" + UUID.randomUUID(), "192.168.10.11");
                spoofedHost.setCuentaId(1L);
                hostRepository.save(spoofedHost);
            }));
        assertNull(TenantContext.currentTenantId());
    }

    @Test
    void clearingNetworkHistoryDeletesOnlyTheActiveTenantHistory() {
        long secondTenantId = createActiveAccount();
        createScan(1L, "192.168.10.20");
        createScan(secondTenantId, "192.168.10.21");

        tenantTaskExecutor.execute(1L, monitoringService::clearHistory);
        tenantTaskExecutor.execute(1L, () -> assertTrue(scanRepository.findAll().isEmpty()));
        tenantTaskExecutor.execute(secondTenantId, () -> assertEquals(1, scanRepository.findAll().size()));
    }

    @Test
    void auditorsAndClientAdminsOnlySeeProjectsWithinTheirAssignmentOrOrganization() {
        long secondTenantId = createActiveAccount();
        ProjectFixture firstTenantProject = createAuditorProject(1L);
        ProjectFixture secondTenantProject = createAuditorProject(secondTenantId);
        TinyHatPrincipal clientAdmin = createClientAdmin(
            1L,
            firstTenantProject.organizationId(),
            "client-admin-" + UUID.randomUUID() + "@example.test");

        tenantTaskExecutor.execute(1L, () -> {
            assertEquals(
                firstTenantProject.projectPublicId(),
                permissionService.requireProyecto(
                    firstTenantProject.auditor(), firstTenantProject.projectPublicId()).getPublicId());
            assertEquals(1, permissionService.proyectosVisibles(clientAdmin).size());
        });

        assertThrows(ScopedNotFoundException.class, () ->
            tenantTaskExecutor.execute(1L, () ->
                permissionService.requireProyecto(
                    firstTenantProject.auditor(), secondTenantProject.projectPublicId())));

        tenantTaskExecutor.execute(secondTenantId, () ->
            assertEquals(
                secondTenantProject.projectPublicId(),
                permissionService.requireProyecto(
                    secondTenantProject.auditor(), secondTenantProject.projectPublicId()).getPublicId()));
    }

    private void createScan(long tenantId, String address) {
        tenantTaskExecutor.execute(tenantId, () -> {
            NetworkHost host = newHost("history-" + UUID.randomUUID(), address);
            hostRepository.save(host);

            NetworkScan scan = new NetworkScan();
            scan.setHost(host);
            scan.setStatus(NetworkScanStatus.COMPLETED);
            scan.setHostStatus(NetworkHostStatus.ONLINE);
            scan.setPortsScanned(0);
            scan.setStartedAt(java.time.LocalDateTime.now());
            scan.setCompletedAt(java.time.LocalDateTime.now());
            scanRepository.save(scan);
        });
    }

    private long createActiveAccount() {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO cuenta (public_id, nombre, tipo, estado, creado_en) "
                    + "VALUES (?, ?, 'AUDITOR', 'ACTIVA', UTC_TIMESTAMP(6))",
                Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, "Fase 4 test account");
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("Test account insert did not return its generated id");
        }
        return generatedId.longValue();
    }

    private ProjectFixture createAuditorProject(long tenantId) {
        AtomicReference<ProjectFixture> fixture = new AtomicReference<>();
        tenantTaskExecutor.execute(tenantId, () -> {
            Empresa organization = new Empresa();
            organization.setNombre("Organization " + UUID.randomUUID());
            organization.setNit(UUID.randomUUID().toString());
            organization.setSector("Technology");
            organization.setCuentaId(tenantId);
            empresaRepository.save(organization);

            Auditoria project = new Auditoria();
            project.setNombre("Project " + UUID.randomUUID());
            project.setTipoAuditoria("Security audit");
            project.setFechaInico(LocalDate.now());
            project.setFechaFin(LocalDate.now());
            project.setEstado("Planificado");
            project.setEmpresa(organization);
            project.setCuentaId(tenantId);
            auditoriaRepository.save(project);

            String email = "auditor-" + UUID.randomUUID() + "@example.test";
            insertUser(tenantId, null, "AUDITOR", email);
            Usuario auditor = usuarioRepository.findByEmailNormalizado(email).orElseThrow();
            asignacionRepository.save(new AsignacionAuditorProyecto(
                new AsignacionAuditorProyectoId(auditor.getId(), project.getIdAuditoria(), tenantId),
                java.time.LocalDateTime.now()));
            fixture.set(new ProjectFixture(
                project.getPublicId(),
                new TinyHatPrincipal(auditor, true),
                organization.getId()));
        });
        return fixture.get();
    }

    private TinyHatPrincipal createClientAdmin(long tenantId, long organizationId, String email) {
        AtomicReference<TinyHatPrincipal> principal = new AtomicReference<>();
        tenantTaskExecutor.execute(tenantId, () -> {
            insertUser(tenantId, organizationId, "CLIENTE_ADMIN", email);
            Usuario user = usuarioRepository.findByEmailNormalizado(email).orElseThrow();
            principal.set(new TinyHatPrincipal(user, true));
        });
        return principal.get();
    }

    private void insertUser(long tenantId, Long organizationId, String role, String email) {
        jdbcTemplate.update(
            "INSERT INTO usuario (cuenta_id, organizacion_id, rol, estado, email_normalizado, "
                + "password_hash, public_id) VALUES (?, ?, ?, 'ACTIVO', ?, 'test-hash', ?)",
            tenantId,
            organizationId,
            role,
            email,
            UUID.randomUUID().toString());
    }

    private NetworkHost newHost(String name, String address) {
        NetworkHost host = new NetworkHost();
        host.setDisplayName(name);
        host.setAddress(address);
        return host;
    }

    private record ProjectFixture(String projectPublicId, TinyHatPrincipal auditor, long organizationId) {
    }
}

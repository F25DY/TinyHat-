package com.hexora.hexora.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hexora.hexora.model.Cve;
import com.hexora.hexora.model.AreaAuditable;
import com.hexora.hexora.model.AsignacionAuditorProyecto;
import com.hexora.hexora.model.AsignacionAuditorProyectoId;
import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.Empresa;
import com.hexora.hexora.model.Usuario;
import com.hexora.hexora.model.Vulnerabilidad;
import com.hexora.hexora.repository.AreaAuditableRepository;
import com.hexora.hexora.repository.AsignacionAuditorProyectoRepository;
import com.hexora.hexora.repository.AuditoriaRepository;
import com.hexora.hexora.repository.CveRepository;
import com.hexora.hexora.repository.EmpresaRepository;
import com.hexora.hexora.repository.UsuarioRepository;
import com.hexora.hexora.repository.VulnerabilidadRepository;
import com.hexora.hexora.security.MfaAuthenticationService;
import com.hexora.hexora.security.SessionActivityFilter;
import com.hexora.hexora.security.TenantTaskExecutor;
import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.service.Capability;
import com.hexora.hexora.service.CapabilityService;
import com.hexora.test.IsolatedMySqlTestDatabase;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "hexora.network.discovery-enabled=false",
    "hexora.network.monitor-interval-ms=3600000",
    "hexora.network.discovery-interval-ms=3600000",
    "hexora.network.retention-cleanup-interval-ms=3600000",
    "tinyhat.security.mfa.required=false",
    "tinyhat.security.ratelimit.api-general.capacity=100000",
    "tinyhat.security.ratelimit.api-general.refill-tokens=100000",
    "tinyhat.security.ratelimit.pdf.capacity=100000",
    "tinyhat.security.ratelimit.pdf.refill-tokens=100000"
})
@AutoConfigureMockMvc
@Import(ReportFoundationIntegrationTest.FakePdfRenderer.class)
class ReportFoundationIntegrationTest extends IsolatedMySqlTestDatabase {

    private static final String FAKE_PDF = "%PDF-1.4 fake-for-tests";

    /** Test-only renderer: proves the export pipeline; real PDF/XLSX renderers come later. */
    @TestConfiguration
    static class FakePdfRenderer {
        @Bean
        ReportRenderer fakeTechnicalRenderer() {
            return new ReportRenderer() {
                @Override
                public boolean supports(ReportType type) {
                    return type == ReportType.TECNICO_PDF;
                }

                @Override
                public byte[] render(ReportSnapshot snapshot) {
                    return FAKE_PDF.getBytes(StandardCharsets.UTF_8);
                }
            };
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private Clock clock;
    @Autowired private SessionRegistry sessionRegistry;
    @Autowired private TenantTaskExecutor tenantTaskExecutor;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private AuditoriaRepository auditoriaRepository;
    @Autowired private AreaAuditableRepository areaRepository;
    @Autowired private VulnerabilidadRepository findingRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private AsignacionAuditorProyectoRepository assignmentRepository;
    @Autowired private CveRepository cveRepository;
    @Autowired private CapabilityService capabilityService;

    @Test
    void auditorCreatesADraftWithAnImmutableSnapshotAndAnAuditTrail() throws Exception {
        Fixture a = fixture();
        String reportId = createReport(a.auditorSession(), a.projectPublicId(), "TECNICO_PDF", "Informe inicial");

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT estado, tipo, audiencia, formato, hash_contenido, creado_por, cuenta_id, "
                + "CHAR_LENGTH(contenido_json) AS length FROM informe WHERE public_id = ?", reportId);
        assertThat(row.get("estado")).isEqualTo("BORRADOR");
        assertThat(row.get("tipo")).isEqualTo("TECNICO_PDF");
        assertThat(row.get("audiencia")).isEqualTo("TECNICO");
        assertThat(row.get("formato")).isEqualTo("PDF");
        assertThat((String) row.get("hash_contenido")).matches("[0-9a-f]{64}");
        assertThat(((Number) row.get("creado_por")).longValue()).isEqualTo(a.auditor().getUserId());
        assertThat(((Number) row.get("length")).intValue()).isGreaterThan(100);

        assertAuditRow("REPORT_CREATED", reportId, a.auditor());
    }

    @Test
    void snapshotCarriesFindingsCveMitigationsScopeAndRiskMatrix() throws Exception {
        Fixture a = fixture();
        String reportId = createReport(a.auditorSession(), a.projectPublicId(), "EJECUTIVO_PDF", "Resumen");

        MvcResult result = mockMvc.perform(get("/api/informes/" + reportId + "/contenido").session(a.auditorSession()))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andReturn();
        JsonNode snapshot = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(snapshot.get("audience").asString()).isEqualTo("EJECUTIVO");
        assertThat(snapshot.get("project").get("publicId").asString()).isEqualTo(a.projectPublicId());
        assertThat(snapshot.get("summary").get("totalFindings").asInt()).isEqualTo(2);
        assertThat(snapshot.get("summary").get("overallRisk").asString()).isEqualTo("CRITICO");
        assertThat(snapshot.get("summary").get("findingsByRiskLevel").get("Critico").asInt()).isEqualTo(1);
        assertThat(snapshot.get("summary").get("findingsByRiskLevel").get("Bajo").asInt()).isZero();

        JsonNode findings = snapshot.get("findings");
        assertThat(findings).hasSize(2);
        assertThat(findings.get(0).get("riskLevel").asString()).isEqualTo("Critico");
        assertThat(findings.get(0).get("cve").get("severity").asString()).isEqualTo("CRITICAL");
        assertThat(findings.get(0).get("name").asString()).contains("<script>");

        JsonNode mitigations = snapshot.get("mitigations");
        assertThat(mitigations).hasSize(2);
        assertThat(mitigations.get(0).get("planDocumented").asBoolean()).isTrue();
        assertThat(mitigations.get(1).get("planDocumented").asBoolean()).isFalse();

        assertThat(snapshot.get("scope").get("authorizedRanges")).isEmpty();
        int matrixTotal = 0;
        for (JsonNode row : snapshot.get("riskMatrix").get("cells")) {
            for (JsonNode count : row) {
                matrixTotal += count.asInt();
            }
        }
        assertThat(matrixTotal).isEqualTo(2);
        assertAuditRow("REPORT_VIEWED", reportId, a.auditor());
    }

    @Test
    void auditorsOfOtherAccountsAndUnassignedAuditorsOfTheSameAccountGetANotFound() throws Exception {
        Fixture a = fixture();
        Fixture b = fixture();
        String reportId = createReport(a.auditorSession(), a.projectPublicId(), "TECNICO_PDF", "Privado");

        mockMvc.perform(get("/api/informes/" + reportId).session(b.auditorSession()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
        mockMvc.perform(get("/api/informes/" + reportId + "/contenido").session(b.auditorSession()))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/informes/" + reportId + "/publicar").session(reauthenticated(b.auditor()))
                .with(csrf()))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/informes/" + reportId + "/descarga").session(b.auditorSession()))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/proyectos/" + a.projectPublicId() + "/informes").session(b.auditorSession()))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/proyectos/" + a.projectPublicId() + "/informes").session(b.auditorSession())
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"TECNICO_PDF\",\"titulo\":\"Intruso\"}"))
            .andExpect(status().isNotFound());

        TinyHatPrincipal unassigned = newAuditor(a.accountId(), false, a.projectId());
        mockMvc.perform(get("/api/informes/" + reportId).session(session(unassigned, false)))
            .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM informe WHERE titulo = 'Intruso'", Integer.class))
            .isZero();
    }

    @Test
    void clientAdminReadsOnlyPublishedReportsOfTheOwnOrganizationAndCannotWrite() throws Exception {
        Fixture a = fixture();
        Fixture b = fixture();
        String draftId = createReport(a.auditorSession(), a.projectPublicId(), "TECNICO_PDF", "Borrador");
        String publishedId = createReport(a.auditorSession(), a.projectPublicId(), "TECNICO_PDF", "Publicado");
        mockMvc.perform(post("/api/informes/" + publishedId + "/publicar")
                .session(reauthenticated(a.auditor())).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PUBLICADO"));

        MockHttpSession client = session(a.clientAdmin(), false);
        mockMvc.perform(get("/api/informes/" + draftId).session(client)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/informes/" + draftId + "/contenido").session(client)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/informes/" + publishedId).session(client))
            .andExpect(status().isOk()).andExpect(jsonPath("$.titulo").value("Publicado"));
        mockMvc.perform(get("/api/proyectos/" + a.projectPublicId() + "/informes").session(client))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].publicId").value(publishedId));
        mockMvc.perform(get("/api/informes/" + publishedId + "/contenido").session(client))
            .andExpect(status().isOk());
        assertAuditRow("REPORT_VIEWED", publishedId, a.clientAdmin());

        // Client of another organization (other account) sees nothing, even for a published report.
        mockMvc.perform(get("/api/informes/" + publishedId).session(session(b.clientAdmin(), false)))
            .andExpect(status().isNotFound());

        // A client administrator can never create, publish or unpublish.
        mockMvc.perform(post("/api/proyectos/" + a.projectPublicId() + "/informes").session(client).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"TECNICO_PDF\",\"titulo\":\"x\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/informes/" + publishedId + "/despublicar").session(reauthenticated(a.clientAdmin()))
                .with(csrf()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/informes/" + draftId + "/publicar").session(reauthenticated(a.clientAdmin())).with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    void publishingAndUnpublishingNeedRecentAuthenticationAndFollowTheStateMachine() throws Exception {
        Fixture a = fixture();
        String reportId = createReport(a.auditorSession(), a.projectPublicId(), "TECNICO_PDF", "Estados");

        mockMvc.perform(post("/api/informes/" + reportId + "/publicar").session(a.auditorSession()).with(csrf()))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("REAUTH_REQUIRED"));
        assertThat(state(reportId)).isEqualTo("BORRADOR");

        MockHttpSession fresh = reauthenticated(a.auditor());
        mockMvc.perform(post("/api/informes/" + reportId + "/publicar").session(fresh).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("PUBLICADO"))
            .andExpect(jsonPath("$.publicadoEn").isString());
        assertAuditRow("REPORT_PUBLISHED", reportId, a.auditor());
        mockMvc.perform(post("/api/informes/" + reportId + "/publicar").session(fresh).with(csrf()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("INVALID_STATE"));

        mockMvc.perform(post("/api/informes/" + reportId + "/despublicar").session(a.auditorSession()).with(csrf()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/informes/" + reportId + "/despublicar").session(fresh).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("BORRADOR"));
        assertAuditRow("REPORT_UNPUBLISHED", reportId, a.auditor());
        mockMvc.perform(post("/api/informes/" + reportId + "/despublicar").session(fresh).with(csrf()))
            .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject(
            "SELECT publicado_en IS NULL AND publicado_por IS NULL FROM informe WHERE public_id = ?",
            Boolean.class, reportId)).isTrue();
    }

    @Test
    void downloadUsesTheRegisteredRendererAndAnswersNotImplementedWhenThereIsNone() throws Exception {
        Fixture a = fixture();
        String technical = createReport(a.auditorSession(), a.projectPublicId(), "TECNICO_PDF", "PDF");
        String matrix = createReport(a.auditorSession(), a.projectPublicId(), "MATRIZ_RIESGO_XLSX", "Matriz");

        MvcResult download = mockMvc.perform(get("/api/informes/" + technical + "/descarga").session(a.auditorSession()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.startsWith("attachment; filename=\"informe-tecnico-" + technical.substring(0, 8))))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andReturn();
        assertThat(download.getResponse().getContentAsString()).isEqualTo(FAKE_PDF);
        assertAuditRow("REPORT_EXPORTED", technical, a.auditor());

        mockMvc.perform(get("/api/informes/" + matrix + "/descarga").session(a.auditorSession()))
            .andExpect(status().isNotImplemented())
            .andExpect(jsonPath("$.error").value("REPORT_RENDERER_UNAVAILABLE"))
            .andExpect(jsonPath("$.correlationId").exists());
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE evento = 'REPORT_EXPORTED' AND recurso_public_id = ?",
            Integer.class, matrix)).isZero();
    }

    @Test
    void contentTamperedOutsideTheApplicationIsDetectedAndNeverServed() throws Exception {
        Fixture a = fixture();
        String reportId = createReport(a.auditorSession(), a.projectPublicId(), "TECNICO_PDF", "Integridad");
        jdbc.update("UPDATE informe SET contenido_json = REPLACE(contenido_json, 'Critico', 'Bajo') WHERE public_id = ?",
            reportId);

        mockMvc.perform(get("/api/informes/" + reportId + "/contenido").session(a.auditorSession()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.error").value("REPORT_INTEGRITY_ERROR"));
        mockMvc.perform(post("/api/informes/" + reportId + "/publicar").session(reauthenticated(a.auditor()))
                .with(csrf()))
            .andExpect(status().isInternalServerError());
        mockMvc.perform(get("/api/informes/" + reportId + "/descarga").session(a.auditorSession()))
            .andExpect(status().isInternalServerError());
        assertThat(state(reportId)).isEqualTo("BORRADOR");
    }

    @Test
    void aDisabledCapabilityBlocksCreationAndExport() throws Exception {
        Fixture a = fixture();
        String reportId = createReport(a.auditorSession(), a.projectPublicId(), "TECNICO_PDF", "Capacidad");
        capabilityService.setEnabled(a.accountId(), Capability.INFORMES_PDF, false);

        mockMvc.perform(post("/api/proyectos/" + a.projectPublicId() + "/informes").session(a.auditorSession())
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"TECNICO_PDF\",\"titulo\":\"Nuevo\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FEATURE_DISABLED"));
        mockMvc.perform(get("/api/informes/" + reportId + "/descarga").session(a.auditorSession()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FEATURE_DISABLED"));
    }

    @Test
    void invalidInputIsRejectedWithGenericValidationErrors() throws Exception {
        Fixture a = fixture();
        String[] bodies = {
            "{\"tipo\":\"NO_EXISTE\",\"titulo\":\"x\"}",
            "{\"tipo\":\"TECNICO_PDF\",\"titulo\":\"   \"}",
            "{\"tipo\":\"TECNICO_PDF\",\"titulo\":\"con\\u0000control\"}",
            "{\"tipo\":\"TECNICO_PDF\",\"titulo\":\"" + "x".repeat(201) + "\"}",
            "{\"titulo\":\"sin tipo\"}",
            "{\"tipo\":\"TECNICO_PDF\",\"titulo\":\"ok\",\"cuentaId\":999,\"estado\":\"PUBLICADO\"}"
        };
        for (int index = 0; index < bodies.length - 1; index++) {
            mockMvc.perform(post("/api/proyectos/" + a.projectPublicId() + "/informes").session(a.auditorSession())
                    .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(bodies[index]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        }
        mockMvc.perform(get("/api/informes/not-a-uuid").session(a.auditorSession()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        // Extra properties (account, state) are ignored: the server decides both.
        mockMvc.perform(post("/api/proyectos/" + a.projectPublicId() + "/informes").session(a.auditorSession())
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(bodies[bodies.length - 1]))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("BORRADOR"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM informe WHERE cuenta_id = 999", Integer.class)).isZero();
    }

    @Test
    void theSnapshotNeverMixesDataFromOtherProjectsOrAccounts() throws Exception {
        Fixture a = fixture();
        Fixture b = fixture();
        String reportId = createReport(a.auditorSession(), a.projectPublicId(), "TECNICO_PDF", "Aislado");

        MvcResult result = mockMvc.perform(get("/api/informes/" + reportId + "/contenido").session(a.auditorSession()))
            .andExpect(status().isOk()).andReturn();
        String json = result.getResponse().getContentAsString();
        assertThat(json).doesNotContain(b.projectPublicId()).doesNotContain(b.organizationName());
        assertThat(json).contains(a.organizationName());
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private String createReport(MockHttpSession session, String projectPublicId, String type, String title)
        throws Exception {
        MvcResult result = mockMvc.perform(post("/api/proyectos/" + projectPublicId + "/informes")
                .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("tipo", type, "titulo", title))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.estado").value("BORRADOR"))
            .andExpect(jsonPath("$.hashContenido").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("publicId").asString();
    }

    private String state(String reportId) {
        return jdbc.queryForObject("SELECT estado FROM informe WHERE public_id = ?", String.class, reportId);
    }

    private void assertAuditRow(String event, String reportId, TinyHatPrincipal actor) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT usuario_id, cuenta_id, ip, correlation_id, recurso_tipo FROM audit_event "
                + "WHERE evento = ? AND recurso_public_id = ? AND usuario_id = ?",
            event, reportId, actor.getUserId());
        assertThat(rows).as("audit rows for %s", event).isNotEmpty();
        Map<String, Object> row = rows.get(0);
        assertThat(((Number) row.get("cuenta_id")).longValue()).isEqualTo(actor.getCuentaId());
        assertThat(row.get("ip")).isNotNull();
        assertThat(row.get("correlation_id")).isNotNull();
        assertThat(row.get("recurso_tipo")).isEqualTo("INFORME");
    }

    /** A separate session that has just re-authenticated; the base session of the fixture is left untouched. */
    private MockHttpSession reauthenticated(TinyHatPrincipal principal) {
        return session(principal, true);
    }

    private MockHttpSession session(TinyHatPrincipal principal, boolean reauthenticated) {
        MockHttpSession session = new MockHttpSession();
        long now = clock.millis();
        session.setAttribute(SessionActivityFilter.CREATED_AT_ATTRIBUTE, now);
        session.setAttribute(SessionActivityFilter.ORIGINAL_CREATED_AT_ATTRIBUTE, now);
        session.setAttribute(SessionActivityFilter.LAST_ACTIVITY_ATTRIBUTE, now);
        if (reauthenticated) {
            session.setAttribute(MfaAuthenticationService.LAST_REAUTH_ATTRIBUTE, now);
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities()));
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
        sessionRegistry.registerNewSession(session.getId(), principal);
        return session;
    }

    private Fixture fixture() {
        long accountId = createAccountWithCapabilities();
        AtomicReference<Fixture> holder = new AtomicReference<>();
        tenantTaskExecutor.execute(accountId, () -> {
            Empresa organization = new Empresa();
            organization.setNombre("Org " + UUID.randomUUID());
            organization.setNit(UUID.randomUUID().toString());
            organization.setSector("Tecnología");
            organization.setCuentaId(accountId);
            empresaRepository.save(organization);

            Auditoria project = new Auditoria();
            project.setNombre("Proyecto " + UUID.randomUUID());
            project.setTipoAuditoria("Auditoría de seguridad");
            project.setFechaInico(LocalDate.now());
            project.setFechaFin(LocalDate.now().plusDays(10));
            project.setEstado("En Progreso");
            project.setEmpresa(organization);
            project.setCuentaId(accountId);
            auditoriaRepository.save(project);

            AreaAuditable area = new AreaAuditable();
            area.setNombre("Infraestructura");
            area.setDescripcion("Servidores y red");
            area.setAuditoria(project);
            area.setCuentaId(accountId);
            areaRepository.save(area);

            if (cveRepository.findByCodigoCve("CVE-2021-44228").isEmpty()) {
                Cve cve = new Cve();
                cve.setCodigoCve("CVE-2021-44228");
                cve.setDescripcion("Apache Log4j2 JNDI features do not protect against attacker controlled LDAP.");
                cve.setPuntajeCvss(new BigDecimal("10.0"));
                cve.setSeveridad("CRITICAL");
                cve.setCwe("CWE-502");
                cve.setVersionCvss("3.1");
                cve.setReferenciasParche("https://logging.apache.org/log4j/2.x/security.html");
                cveRepository.save(cve);
            }
            findingRepository.save(finding(area, accountId, "SQL Injection <script>alert(1)</script>",
                "CVE-2021-44228", 9, 9, 8, 9, new BigDecimal("8.67"), "Critico", "Abierta",
                "Parametrizar las consultas y aplicar el parche del proveedor."));
            findingRepository.save(finding(area, accountId, "TLS débil", null,
                3, 4, 3, 4, new BigDecimal("3.33"), "Medio", "En Progreso", null));

            TinyHatPrincipal auditor = newAuditor(accountId, true, project.getIdAuditoria());
            TinyHatPrincipal clientAdmin = newClientAdmin(accountId, organization.getId());
            holder.set(new Fixture(accountId, project.getIdAuditoria(), project.getPublicId(),
                organization.getNombre(), auditor, clientAdmin));
        });
        Fixture base = holder.get();
        return base.withAuditorSession(session(base.auditor(), false));
    }

    private Vulnerabilidad finding(
        AreaAuditable area, long accountId, String name, String cve, int c, int i, int d, int p,
        BigDecimal impact, String level, String status, String plan
    ) {
        Vulnerabilidad finding = new Vulnerabilidad();
        finding.setNombre(name);
        finding.setTipo("Configuración");
        finding.setHerramienta("Revisión manual");
        finding.setHost("srv-01");
        finding.setIpAfectada("192.0.2.10");
        finding.setPuerto(443);
        finding.setServicio("https");
        finding.setCve(cve);
        finding.setConfidencialidad(c);
        finding.setIntegridad(i);
        finding.setDisponibilidad(d);
        finding.setProbabilidad(p);
        finding.setImpacto(impact);
        finding.setNivelRiesgo(level);
        finding.setEstado(status);
        finding.setFechaDeteccion(LocalDate.now());
        finding.setPlanMitigacion(plan);
        finding.setAreaAuditable(area);
        finding.setCuentaId(accountId);
        return finding;
    }

    private long createAccountWithCapabilities() {
        String publicId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO cuenta (public_id, nombre, tipo, estado, creado_en) "
            + "VALUES (?, 'Cuenta de prueba de informes', 'AUDITOR', 'ACTIVA', UTC_TIMESTAMP(6))", publicId);
        Long accountId = jdbc.queryForObject("SELECT id FROM cuenta WHERE public_id = ?", Long.class, publicId);
        for (Capability capability : Capability.values()) {
            jdbc.update("INSERT INTO capacidad_cuenta (cuenta_id, capacidad, habilitada) VALUES (?, ?, TRUE)",
                accountId, capability.getDatabaseValue());
        }
        return accountId;
    }

    private TinyHatPrincipal newAuditor(long accountId, boolean assigned, long projectId) {
        String email = "auditor-" + UUID.randomUUID() + "@example.test";
        insertUser(accountId, null, "AUDITOR", email);
        Usuario user = usuarioRepository.findByEmailNormalizado(email).orElseThrow();
        if (assigned) {
            assignmentRepository.save(new AsignacionAuditorProyecto(
                new AsignacionAuditorProyectoId(user.getId(), projectId, accountId), LocalDateTime.now()));
        }
        return new TinyHatPrincipal(user, true);
    }

    private TinyHatPrincipal newClientAdmin(long accountId, long organizationId) {
        String email = "cliente-" + UUID.randomUUID() + "@example.test";
        insertUser(accountId, organizationId, "CLIENTE_ADMIN", email);
        return new TinyHatPrincipal(usuarioRepository.findByEmailNormalizado(email).orElseThrow(), true);
    }

    private void insertUser(long accountId, Long organizationId, String role, String email) {
        jdbc.update("INSERT INTO usuario (cuenta_id, organizacion_id, rol, estado, email_normalizado, "
                + "password_hash, public_id) VALUES (?, ?, ?, 'ACTIVO', ?, 'test-hash', ?)",
            accountId, organizationId, role, email, UUID.randomUUID().toString());
    }

    private record Fixture(
        long accountId,
        long projectId,
        String projectPublicId,
        String organizationName,
        TinyHatPrincipal auditor,
        TinyHatPrincipal clientAdmin,
        MockHttpSession auditorSession
    ) {
        Fixture(long accountId, long projectId, String projectPublicId, String organizationName,
                TinyHatPrincipal auditor, TinyHatPrincipal clientAdmin) {
            this(accountId, projectId, projectPublicId, organizationName, auditor, clientAdmin, null);
        }

        Fixture withAuditorSession(MockHttpSession session) {
            return new Fixture(accountId, projectId, projectPublicId, organizationName, auditor, clientAdmin, session);
        }
    }
}

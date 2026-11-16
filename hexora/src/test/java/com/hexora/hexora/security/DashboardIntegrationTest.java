package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hexora.hexora.dto.DashboardResumen;
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
import com.hexora.hexora.repository.EmpresaRepository;
import com.hexora.hexora.repository.UsuarioRepository;
import com.hexora.hexora.repository.VulnerabilidadRepository;
import com.hexora.hexora.service.DashboardService;
import com.hexora.test.IsolatedMySqlTestDatabase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El panel muestra cifras reales y solo de lo que el usuario puede ver. Dos cuentas, un proyecto de la misma cuenta
 * al que el auditor no está asignado y un nombre de hallazgo con HTML: nada de eso puede colarse en el panel.
 */
@SpringBootTest(properties = {
	"hexora.network.discovery-enabled=false",
	"hexora.network.monitor-interval-ms=3600000",
	"hexora.network.discovery-interval-ms=3600000",
	"hexora.network.retention-cleanup-interval-ms=3600000",
	"tinyhat.security.mfa.required=false",
	"tinyhat.security.ratelimit.api-general.capacity=100000",
	"tinyhat.security.ratelimit.api-general.refill-tokens=100000"
})
@AutoConfigureMockMvc
class DashboardIntegrationTest extends IsolatedMySqlTestDatabase {

	private static final String HTML_NAME = "<script>alert(1)</script>";

	@Autowired private MockMvc mockMvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private Clock clock;
	@Autowired private SessionRegistry sessionRegistry;
	@Autowired private TenantTaskExecutor tenantTaskExecutor;
	@Autowired private DashboardService dashboardService;
	@Autowired private EmpresaRepository empresaRepository;
	@Autowired private AuditoriaRepository auditoriaRepository;
	@Autowired private AreaAuditableRepository areaRepository;
	@Autowired private VulnerabilidadRepository findingRepository;
	@Autowired private UsuarioRepository usuarioRepository;
	@Autowired private AsignacionAuditorProyectoRepository assignmentRepository;

	private record World(
		long accountA, TinyHatPrincipal auditorA, TinyHatPrincipal auditorWithoutProjects,
		String sqlProjectId, String sqlAreaId, String sqlFindingId) {
	}

	@Test
	void countsOnlyWhatTheAuditorCanSee() {
		World world = world();

		DashboardResumen resumen = resumen(world.accountA(), world.auditorA());

		assertThat(resumen.proyectos()).as("solo los 2 proyectos asignados").isEqualTo(2);
		assertThat(resumen.abiertos()).isEqualTo(8);
		assertThat(resumen.mitigados()).isEqualTo(1);
		assertThat(resumen.aceptados()).isEqualTo(1);
		assertThat(resumen.niveles()).extracting(DashboardResumen.Nivel::nombre)
			.containsExactly("Crítico", "Alto", "Medio", "Bajo");
		assertThat(resumen.niveles()).extracting(DashboardResumen.Nivel::cantidad).containsExactly(1L, 2L, 1L, 4L);
		assertThat(resumen.niveles()).extracting(DashboardResumen.Nivel::porcentaje).containsExactly(13, 25, 13, 50);
	}

	@Test
	void recentFindingsAreTheNewestFirstLimitedAndNeverForeign() {
		World world = world();

		DashboardResumen resumen = resumen(world.accountA(), world.auditorA());

		assertThat(resumen.recientes()).extracting(DashboardResumen.HallazgoReciente::nombre).containsExactly(
			"Puerto abierto", "Inyección SQL", "XSS reflejado", "Cabeceras débiles", HTML_NAME, "Contraseña por defecto");
		assertThat(resumen.recientes()).extracting(DashboardResumen.HallazgoReciente::nombre)
			.noneMatch(name -> name.startsWith("Oculto") || name.startsWith("Ajeno"));
	}

	@Test
	void anAccountWithoutProjectsSeesTheFirstSteps() throws Exception {
		World world = world();

		DashboardResumen resumen = resumen(world.accountA(), world.auditorWithoutProjects());
		assertThat(resumen.sinProyectos()).isTrue();

		String html = page("/dashboard", world.auditorWithoutProjects());
		assertThat(html).contains("Empieza por tu primer proyecto").contains("Crea el proyecto")
			.contains("Añade un departamento").contains("Registra los hallazgos")
			.doesNotContain("hallazgos abiertos").doesNotContain("Hallazgos recientes");
	}

	@Test
	void theDashboardShowsRealNumbersLinksAndTheLoggedInUser() throws Exception {
		World world = world();

		String html = page("/dashboard", world.auditorA());

		assertThat(html).contains("exposicion-numero\">8<").contains("hallazgos abiertos");
		assertThat(html).contains("riesgo-critico ancho-13").contains("riesgo-alto ancho-25").contains("riesgo-bajo ancho-50");
		assertThat(html).contains(world.auditorA().getUsername());
		assertThat(html).doesNotContain("analista@gmail.com");
		assertThat(html).contains("/proyectos/" + world.sqlProjectId() + "/departamentos/" + world.sqlAreaId()
			+ "/vulnerabilidades/" + world.sqlFindingId());
		assertThat(html).doesNotContain("Proyecto Gamma").doesNotContain("Oculto").doesNotContain("Proyecto Ajeno")
			.doesNotContain("Ajeno de B");
		assertThat(html).as("sin estilos en línea (CSP)").doesNotContain(" style=\"");
	}

	@Test
	void namesWithHtmlAreShownAsTextNeverAsMarkup() throws Exception {
		World world = world();

		String html = page("/dashboard", world.auditorA());

		assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
		assertThat(html).doesNotContain("<script>alert(1)");
	}

	@Test
	void everyPageWithTheSidebarShowsTheRealUserAndALogoutFormWithCsrf() throws Exception {
		World world = world();
		String findingPage = "/proyectos/" + world.sqlProjectId() + "/departamentos/" + world.sqlAreaId()
			+ "/vulnerabilidades/" + world.sqlFindingId();

		for (String path : List.of("/dashboard", "/proyectos", "/proyectos/" + world.sqlProjectId(), findingPage)) {
			String html = page(path, world.auditorA());
			assertThat(html).as(path).contains(world.auditorA().getUsername()).doesNotContain("analista@gmail.com");
			assertThat(html).as(path + ": cerrar sesión debe ser un formulario POST con CSRF")
				.contains("action=\"/logout\"").contains("name=\"_csrf\"");
		}
	}

	// ---------------------------------------------------------------- utilidades

	private DashboardResumen resumen(long accountId, TinyHatPrincipal principal) {
		AtomicReference<DashboardResumen> result = new AtomicReference<>();
		tenantTaskExecutor.execute(accountId, () -> result.set(dashboardService.resumen(principal)));
		return result.get();
	}

	private String page(String path, TinyHatPrincipal principal) throws Exception {
		return mockMvc.perform(get(path).session(session(principal)).with(request -> {
				request.setRemoteAddr("198.51.100." + (1 + Math.abs(path.hashCode() % 200)));
				return request;
			}))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
	}

	private World world() {
		long accountA = account("Cuenta A");
		long accountB = account("Cuenta B");
		AtomicReference<TinyHatPrincipal> auditor = new AtomicReference<>();
		AtomicReference<TinyHatPrincipal> empty = new AtomicReference<>();
		AtomicReference<String[]> sql = new AtomicReference<>();
		LocalDate today = LocalDate.now();

		tenantTaskExecutor.execute(accountA, () -> {
			Auditoria alfa = project(accountA, "Proyecto Alfa");
			AreaAuditable servidores = area(alfa, "Servidores");
			Vulnerabilidad sqlInjection = finding(servidores, "Inyección SQL", "Critico", "Abierta", today.minusDays(1));
			finding(servidores, "XSS reflejado", "Alto", "En Progreso", today.minusDays(2));
			finding(servidores, "Cabeceras débiles", "Medio", "Abierta", today.minusDays(3));
			finding(servidores, HTML_NAME, "Bajo", "Abierta", today.minusDays(4));
			finding(servidores, "Contraseña por defecto", "Critico", "Mitigada", today.minusDays(5));

			Auditoria beta = project(accountA, "Proyecto Beta");
			AreaAuditable red = area(beta, "Red");
			finding(red, "Puerto abierto", "Alto", "Abierta", today);
			finding(red, "Certificado caducado", "Medio", "Aceptada", today.minusDays(6));
			for (int i = 1; i <= 3; i++) {
				finding(red, "Hallazgo menor " + i, "Bajo", "Abierta", today.minusDays(10 + i));
			}

			// Misma cuenta, pero el auditor NO está asignado a este proyecto.
			Auditoria gamma = project(accountA, "Proyecto Gamma");
			finding(area(gamma, "Oculto"), "Oculto de A", "Critico", "Abierta", today);

			TinyHatPrincipal principal = auditor(accountA, "auditor-" + UUID.randomUUID() + "@example.test");
			assign(principal, alfa);
			assign(principal, beta);
			auditor.set(principal);
			empty.set(auditor(accountA, "nuevo-" + UUID.randomUUID() + "@example.test"));
			sql.set(new String[] {alfa.getPublicId(), servidores.getPublicId(), sqlInjection.getPublicId()});
		});
		tenantTaskExecutor.execute(accountB, () -> {
			Auditoria ajeno = project(accountB, "Proyecto Ajeno");
			finding(area(ajeno, "Ajeno"), "Ajeno de B", "Critico", "Abierta", today);
		});
		return new World(accountA, auditor.get(), empty.get(), sql.get()[0], sql.get()[1], sql.get()[2]);
	}

	private Auditoria project(long accountId, String name) {
		Empresa organization = new Empresa();
		organization.setNombre("Org " + UUID.randomUUID());
		organization.setNit(UUID.randomUUID().toString());
		organization.setSector("Tecnología");
		organization.setCuentaId(accountId);
		empresaRepository.save(organization);

		Auditoria project = new Auditoria();
		project.setNombre(name);
		project.setTipoAuditoria("Auditoría de seguridad");
		project.setFechaInico(LocalDate.now());
		project.setFechaFin(LocalDate.now().plusDays(10));
		project.setEstado("En Progreso");
		project.setEmpresa(organization);
		project.setCuentaId(accountId);
		return auditoriaRepository.save(project);
	}

	private AreaAuditable area(Auditoria project, String name) {
		AreaAuditable area = new AreaAuditable();
		area.setNombre(name);
		area.setDescripcion("Descripción");
		area.setAuditoria(project);
		area.setCuentaId(project.getCuentaId());
		return areaRepository.save(area);
	}

	private Vulnerabilidad finding(AreaAuditable area, String name, String level, String state, LocalDate date) {
		Vulnerabilidad finding = new Vulnerabilidad();
		finding.setNombre(name);
		finding.setTipo("Configuración");
		finding.setConfidencialidad(5);
		finding.setIntegridad(5);
		finding.setDisponibilidad(5);
		finding.setProbabilidad(5);
		finding.setImpacto(new BigDecimal("5.00"));
		finding.setNivelRiesgo(level);
		finding.setEstado(state);
		finding.setFechaDeteccion(date);
		finding.setAreaAuditable(area);
		finding.setCuentaId(area.getCuentaId());
		return findingRepository.save(finding);
	}

	private TinyHatPrincipal auditor(long accountId, String email) {
		jdbc.update("INSERT INTO usuario (cuenta_id, organizacion_id, rol, estado, email_normalizado, "
			+ "password_hash, public_id) VALUES (?, NULL, 'AUDITOR', 'ACTIVO', ?, 'test-hash', ?)",
			accountId, email, UUID.randomUUID().toString());
		Usuario user = usuarioRepository.findByEmailNormalizado(email).orElseThrow();
		return new TinyHatPrincipal(user, true);
	}

	private void assign(TinyHatPrincipal principal, Auditoria project) {
		assignmentRepository.save(new AsignacionAuditorProyecto(
			new AsignacionAuditorProyectoId(principal.getUserId(), project.getIdAuditoria(), project.getCuentaId()),
			LocalDateTime.now()));
	}

	private long account(String name) {
		String publicId = UUID.randomUUID().toString();
		jdbc.update("INSERT INTO cuenta (public_id, nombre, tipo, estado, creado_en) "
			+ "VALUES (?, ?, 'AUDITOR', 'ACTIVA', UTC_TIMESTAMP(6))", publicId, name);
		Long id = jdbc.queryForObject("SELECT id FROM cuenta WHERE public_id = ?", Long.class, publicId);
		for (String capability : List.of("modulo_red", "informes_pdf", "monitoreo_continuo")) {
			jdbc.update("INSERT INTO capacidad_cuenta (cuenta_id, capacidad, habilitada) VALUES (?, ?, TRUE)", id, capability);
		}
		return id;
	}

	private MockHttpSession session(TinyHatPrincipal principal) {
		MockHttpSession session = new MockHttpSession();
		long now = clock.millis();
		session.setAttribute(SessionActivityFilter.CREATED_AT_ATTRIBUTE, now);
		session.setAttribute(SessionActivityFilter.ORIGINAL_CREATED_AT_ATTRIBUTE, now);
		session.setAttribute(SessionActivityFilter.LAST_ACTIVITY_ATTRIBUTE, now);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
			principal, null, principal.getAuthorities()));
		session.setAttribute("SPRING_SECURITY_CONTEXT", context);
		sessionRegistry.registerNewSession(session.getId(), principal);
		return session;
	}
}

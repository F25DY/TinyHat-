package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import com.hexora.test.IsolatedMySqlTestDatabase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Fase 14, brechas G-1 y G-2 (criterios F4-CA1 a CA5 y CA9 del plan): matriz HTTP entre cuentas
 * para proyecto, área y hallazgo. Un auditor de la cuenta A usa identificadores de la cuenta B, de
 * otro proyecto de su propia cuenta al que no está asignado, o los mezcla en rutas anidadas. Cada
 * petición debe dar 404 (el mismo que un identificador inexistente) y no puede cambiar ninguna
 * fila. Después se comprueba que el cuerpo de una petición no puede elegir cuenta ni riesgo.
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
class CrossAccountIsolationIntegrationTest extends IsolatedMySqlTestDatabase {

	private static final List<String> BUSINESS_TABLES =
		List.of("organizacion_cliente", "auditoria", "area_auditable", "vulnerabilidad", "asignacion_auditor_proyecto");

	@Autowired private MockMvc mockMvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private Clock clock;
	@Autowired private SessionRegistry sessionRegistry;
	@Autowired private TenantTaskExecutor tenantTaskExecutor;
	@Autowired private EmpresaRepository empresaRepository;
	@Autowired private AuditoriaRepository auditoriaRepository;
	@Autowired private AreaAuditableRepository areaRepository;
	@Autowired private VulnerabilidadRepository findingRepository;
	@Autowired private UsuarioRepository usuarioRepository;
	@Autowired private AsignacionAuditorProyectoRepository assignmentRepository;

	private record Chain(long accountId, String projectId, String areaId, String findingId) {
	}

	private record World(
		long accountA,
		long accountB,
		TinyHatPrincipal auditorA,
		Chain a,
		Chain a2,
		Chain b
	) {
	}

	@Test
	void anAuditorNeverReachesForeignOrUnassignedProjectsAreasOrFindings() throws Exception {
		World world = world();
		MockHttpSession session = session(world.auditorA());
		String unknown = UUID.randomUUID().toString();

		// Control positivo: con identificadores propios las rutas responden 200. Sin esto, un 404 podría
		// deberse a una sesión mal armada y no al aislamiento.
		for (String path : List.of(
			project(world.a()),
			project(world.a()) + "/editar",
			area(world.a()),
			area(world.a()) + "/editar",
			finding(world.a()),
			finding(world.a()) + "/editar",
			project(world.a()) + "/areas/nueva",
			area(world.a()) + "/vulnerabilidades/nueva")) {
			assertThat(perform(HttpMethod.GET, path, session).getResponse().getStatus())
				.as("control positivo GET %s", path)
				.isEqualTo(200);
		}

		List<Probe> probes = new ArrayList<>();
		// 1) Cadena completa de otra cuenta, 2) cadena de otro proyecto de la misma cuenta sin asignación,
		// 3) identificadores inventados. Las tres deben ser indistinguibles.
		for (Chain foreign : List.of(world.b(), world.a2(), new Chain(0, unknown, unknown, unknown))) {
			probes.addAll(routes(foreign.projectId(), foreign.areaId(), foreign.findingId(), Depth.PROJECT));
		}
		// 4) Rutas anidadas mezcladas: la jerarquía entera debe pertenecer a la misma cuenta y proyecto.
		//    Solo se sondean los niveles que contienen el identificador ajeno; las rutas de nivel proyecto
		//    con el proyecto propio son legítimas y no deben ejecutarse.
		Chain own = world.a();
		probes.addAll(routes(own.projectId(), world.b().areaId(), world.b().findingId(), Depth.AREA));
		probes.addAll(routes(own.projectId(), world.a2().areaId(), world.a2().findingId(), Depth.AREA));
		probes.addAll(routes(world.b().projectId(), own.areaId(), own.findingId(), Depth.PROJECT));
		probes.addAll(routes(own.projectId(), own.areaId(), world.b().findingId(), Depth.FINDING));
		probes.addAll(routes(own.projectId(), own.areaId(), world.a2().findingId(), Depth.FINDING));
		probes.addAll(List.of(
			new Probe(HttpMethod.GET, "/areas/" + world.b().areaId()),
			new Probe(HttpMethod.GET, "/areas/" + world.a2().areaId()),
			new Probe(HttpMethod.GET, "/areas/" + unknown)));

		Map<String, List<Map<String, Object>>> before = snapshot();
		List<String> failures = new ArrayList<>();
		int checked = 0;
		for (Probe probe : probes) {
			MvcResult result = probe.method() == HttpMethod.POST
				? mockMvc.perform(withValidForm(post(probe.path()), probe.path()).session(session).with(csrf())).andReturn()
				: perform(HttpMethod.GET, probe.path(), session);
			checked++;
			if (result.getResponse().getStatus() != 404) {
				failures.add(probe.method() + " " + probe.path() + " -> " + result.getResponse().getStatus());
			}
		}

		assertThat(failures).as("peticiones que no dieron 404 (de %s)", checked).isEmpty();
		assertThat(checked).as("la matriz debe ser amplia").isEqualTo(95);
		assertThat(snapshot())
			.as("ninguna escritura entre cuentas puede modificar filas (proyectos, áreas, hallazgos, asignaciones)")
			.isEqualTo(before);
	}

	@Test
	void foreignAndMissingResourcesAnswerWithTheSameBody() throws Exception {
		World world = world();
		MockHttpSession session = session(world.auditorA());
		String ofOtherAccount = perform(HttpMethod.GET, project(world.b()), session).getResponse().getContentAsString();
		String ofOtherProject = perform(HttpMethod.GET, project(world.a2()), session).getResponse().getContentAsString();
		String missing = perform(HttpMethod.GET, "/proyectos/" + UUID.randomUUID(), session).getResponse().getContentAsString();
		// El cuerpo no puede revelar si el recurso existe. Solo el identificador de correlación cambia.
		assertThat(withoutCorrelation(ofOtherAccount)).isEqualTo(withoutCorrelation(missing));
		assertThat(withoutCorrelation(ofOtherProject)).isEqualTo(withoutCorrelation(missing));
		assertThat(ofOtherAccount).doesNotContain(world.b().projectId());
	}

	@Test
	void requestBodiesCannotChooseTheAccountTheRoleOrTheRisk() throws Exception {
		World world = world();
		MockHttpSession session = session(world.auditorA());

		// G-2: parámetros que intentan elegir cuenta, organización o rol se ignoran.
		String projectName = "Proyecto manipulado " + UUID.randomUUID();
		mockMvc.perform(post("/proyectos/guardar").session(session).with(csrf())
			.param("nombreEmpresa", "Organización manipulada")
			.param("nombre", projectName)
			.param("tipoAuditoria", "Interna")
			.param("fechaInicio", LocalDate.now().toString())
			.param("estado", "Planificado")
			.param("cuentaId", String.valueOf(world.accountB()))
			.param("cuenta_id", String.valueOf(world.accountB()))
			.param("empresa.cuentaId", String.valueOf(world.accountB()))
			.param("rol", "CLIENTE_ADMIN")
			.param("organizacionId", "1")).andReturn();
		Long projectAccount = jdbc.queryForObject(
			"SELECT cuenta_id FROM auditoria WHERE nombre = ?", Long.class, projectName);
		assertThat(projectAccount).isEqualTo(world.accountA());
		Long organizationAccount = jdbc.queryForObject(
			"SELECT e.cuenta_id FROM organizacion_cliente e JOIN auditoria a ON a.organizacion_cliente_id = e.id_empresa WHERE a.nombre = ?",
			Long.class, projectName);
		assertThat(organizationAccount).isEqualTo(world.accountA());
		assertThat(jdbc.queryForObject("SELECT rol FROM usuario WHERE id = ?", String.class, world.auditorA().getUserId()))
			.isEqualTo("AUDITOR");

		String areaName = "Área manipulada " + UUID.randomUUID();
		mockMvc.perform(post(project(world.a()) + "/areas/guardar").session(session).with(csrf())
			.param("nombre", areaName)
			.param("descripcion", "x")
			.param("cuentaId", String.valueOf(world.accountB()))
			.param("auditoria.id", "1")).andReturn();
		assertThat(jdbc.queryForObject("SELECT cuenta_id FROM area_auditable WHERE nombre = ?", Long.class, areaName))
			.isEqualTo(world.accountA());

		// F4-CA9: el servidor recalcula el riesgo; el valor que manda el cliente se ignora.
		String findingName = "Hallazgo manipulado " + UUID.randomUUID();
		mockMvc.perform(post(area(world.a()) + "/vulnerabilidades/guardar").session(session).with(csrf())
			.param("nombre", findingName)
			.param("tipo", "Configuración")
			.param("confidencialidad", "10")
			.param("integridad", "10")
			.param("disponibilidad", "10")
			.param("probabilidad", "10")
			.param("estado", "Abierta")
			.param("fechaDeteccion", LocalDate.now().toString())
			.param("impacto", "0.01")
			.param("nivelRiesgo", "Bajo")
			.param("cuentaId", String.valueOf(world.accountB()))).andReturn();
		Map<String, Object> stored = jdbc.queryForMap(
			"SELECT cuenta_id, impacto, nivel_riesgo FROM vulnerabilidad WHERE nombre = ?", findingName);
		assertThat(((Number) stored.get("cuenta_id")).longValue()).isEqualTo(world.accountA());
		assertThat(((BigDecimal) stored.get("impacto"))).isGreaterThan(new BigDecimal("5"));
		assertThat((String) stored.get("nivel_riesgo")).isNotEqualToIgnoringCase("Bajo");

		// Valores fuera de rango se rechazan y no se guardan.
		String invalidName = "Hallazgo fuera de rango " + UUID.randomUUID();
		mockMvc.perform(post(area(world.a()) + "/vulnerabilidades/guardar").session(session).with(csrf())
			.param("nombre", invalidName)
			.param("tipo", "Configuración")
			.param("confidencialidad", "11")
			.param("integridad", "-1")
			.param("disponibilidad", "10")
			.param("probabilidad", "10")
			.param("estado", "Abierta")
			.param("fechaDeteccion", LocalDate.now().toString())).andReturn();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vulnerabilidad WHERE nombre = ?", Integer.class, invalidName))
			.isZero();
	}

	// ---------------------------------------------------------------- rutas

	private record Probe(HttpMethod method, String path) {
	}

	/** Desde qué nivel de la jerarquía se sondean rutas: PROJECT (16 rutas), AREA (10) o FINDING (4). */
	private enum Depth {
		PROJECT, AREA, FINDING
	}

	private static List<Probe> routes(String projectId, String areaId, String findingId, Depth from) {
		String project = "/proyectos/" + projectId;
		String area = project + "/departamentos/" + areaId;
		String finding = area + "/vulnerabilidades/" + findingId;
		List<Probe> routes = new ArrayList<>();
		if (from == Depth.PROJECT) {
			routes.add(new Probe(HttpMethod.GET, project));
			routes.add(new Probe(HttpMethod.GET, project + "/editar"));
			routes.add(new Probe(HttpMethod.POST, project + "/actualizar"));
			routes.add(new Probe(HttpMethod.POST, project + "/eliminar"));
			routes.add(new Probe(HttpMethod.GET, project + "/areas/nueva"));
			routes.add(new Probe(HttpMethod.POST, project + "/areas/guardar"));
		}
		if (from != Depth.FINDING) {
			routes.add(new Probe(HttpMethod.GET, area));
			routes.add(new Probe(HttpMethod.GET, area + "/editar"));
			routes.add(new Probe(HttpMethod.POST, area + "/actualizar"));
			routes.add(new Probe(HttpMethod.POST, area + "/eliminar"));
			routes.add(new Probe(HttpMethod.GET, area + "/vulnerabilidades/nueva"));
			routes.add(new Probe(HttpMethod.POST, area + "/vulnerabilidades/guardar"));
		}
		routes.add(new Probe(HttpMethod.GET, finding));
		routes.add(new Probe(HttpMethod.GET, finding + "/editar"));
		routes.add(new Probe(HttpMethod.POST, finding + "/actualizar"));
		routes.add(new Probe(HttpMethod.POST, finding + "/eliminar"));
		return routes;
	}

	private static String project(Chain chain) {
		return "/proyectos/" + chain.projectId();
	}

	private static String area(Chain chain) {
		return project(chain) + "/departamentos/" + chain.areaId();
	}

	private static String finding(Chain chain) {
		return area(chain) + "/vulnerabilidades/" + chain.findingId();
	}

	private MvcResult perform(HttpMethod method, String path, MockHttpSession session) throws Exception {
		return mockMvc.perform(get(path).session(session)).andReturn();
	}

	private static String withoutCorrelation(String body) {
		return body.replaceAll("\"correlationId\":\"[^\"]*\"", "\"correlationId\":\"-\"");
	}

	/** Cuerpo válido para que una validación de formulario no oculte el control de acceso que se prueba. */
	private static MockHttpServletRequestBuilder withValidForm(MockHttpServletRequestBuilder builder, String path) {
		return builder
			.param("nombreEmpresa", "Organización de prueba")
			.param("nombre", "Nombre de prueba")
			.param("tipoAuditoria", "Interna")
			.param("fechaInicio", LocalDate.now().toString())
			.param("estado", path.contains("/vulnerabilidades/") ? "Abierta" : "Planificado")
			.param("descripcion", "Descripción de prueba")
			.param("tipo", "Configuración")
			.param("confidencialidad", "5")
			.param("integridad", "5")
			.param("disponibilidad", "5")
			.param("probabilidad", "5")
			.param("fechaDeteccion", LocalDate.now().toString());
	}

	private Map<String, List<Map<String, Object>>> snapshot() {
		Map<String, List<Map<String, Object>>> state = new java.util.LinkedHashMap<>();
		for (String table : BUSINESS_TABLES) {
			state.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1, 2"));
		}
		return state;
	}

	// ---------------------------------------------------------------- datos

	private World world() {
		long accountA = account("Cuenta A");
		long accountB = account("Cuenta B");
		AtomicReference<TinyHatPrincipal> auditor = new AtomicReference<>();
		AtomicReference<Chain> a = new AtomicReference<>();
		AtomicReference<Chain> a2 = new AtomicReference<>();
		AtomicReference<Chain> b = new AtomicReference<>();
		tenantTaskExecutor.execute(accountA, () -> {
			Chain first = chain(accountA);
			a.set(first);
			a2.set(chain(accountA));
			String email = "auditor-" + UUID.randomUUID() + "@example.test";
			insertUser(accountA, "AUDITOR", email);
			Usuario user = usuarioRepository.findByEmailNormalizado(email).orElseThrow();
			Long firstProjectId = jdbc.queryForObject(
				"SELECT id_auditoria FROM auditoria WHERE public_id = ?", Long.class, first.projectId());
			// Solo el primer proyecto está asignado: el segundo es de la misma cuenta pero ajeno al auditor.
			assignmentRepository.save(new AsignacionAuditorProyecto(
				new AsignacionAuditorProyectoId(user.getId(), firstProjectId, accountA), LocalDateTime.now()));
			auditor.set(new TinyHatPrincipal(user, true));
		});
		tenantTaskExecutor.execute(accountB, () -> b.set(chain(accountB)));
		return new World(accountA, accountB, auditor.get(), a.get(), a2.get(), b.get());
	}

	private Chain chain(long accountId) {
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
		area.setNombre("Área " + UUID.randomUUID());
		area.setDescripcion("Servidores");
		area.setAuditoria(project);
		area.setCuentaId(accountId);
		areaRepository.save(area);

		Vulnerabilidad finding = new Vulnerabilidad();
		finding.setNombre("Hallazgo " + UUID.randomUUID());
		finding.setTipo("Configuración");
		finding.setConfidencialidad(5);
		finding.setIntegridad(5);
		finding.setDisponibilidad(5);
		finding.setProbabilidad(5);
		finding.setImpacto(new BigDecimal("5.00"));
		finding.setNivelRiesgo("Medio");
		finding.setEstado("Abierta");
		finding.setFechaDeteccion(LocalDate.now());
		finding.setAreaAuditable(area);
		finding.setCuentaId(accountId);
		findingRepository.save(finding);
		return new Chain(accountId, project.getPublicId(), area.getPublicId(), finding.getPublicId());
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

	private void insertUser(long accountId, String role, String email) {
		jdbc.update("INSERT INTO usuario (cuenta_id, organizacion_id, rol, estado, email_normalizado, "
			+ "password_hash, public_id) VALUES (?, NULL, ?, 'ACTIVO', ?, 'test-hash', ?)",
			accountId, role, email, UUID.randomUUID().toString());
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

package com.hexora.hexora.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.hexora.hexora.model.AreaAuditable;
import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.Empresa;
import com.hexora.hexora.model.Vulnerabilidad;
import com.hexora.hexora.repository.AreaAuditableRepository;
import com.hexora.hexora.repository.AuditoriaRepository;
import com.hexora.hexora.repository.EmpresaRepository;
import com.hexora.hexora.repository.UsuarioRepository;
import com.hexora.hexora.repository.VulnerabilidadRepository;
import com.hexora.hexora.security.SessionActivityFilter;
import com.hexora.hexora.security.TenantTaskExecutor;
import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.test.IsolatedMySqlTestDatabase;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Las vistas de informes de punta a punta: sesión real, CSRF, autorización por rol, cabeceras de descarga y CSP (sin
 * estilos ni scripts en línea).
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
class InformeWebIntegrationTest extends IsolatedMySqlTestDatabase {

	private static final Path DIRECTORIO = crearDirectorio();
	private static final Pattern UUID_EN_RUTA = Pattern.compile("/informes/versiones/([0-9a-f-]{36})");

	private static Path crearDirectorio() {
		try {
			return Files.createTempDirectory("informes-web-");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void directorio(DynamicPropertyRegistry registro) {
		registro.add("tinyhat.informes.dir", () -> DIRECTORIO.toString());
	}

	@Autowired private MockMvc mockMvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private Clock clock;
	@Autowired private SessionRegistry sessionRegistry;
	@Autowired private TenantTaskExecutor tenants;
	@Autowired private EmpresaRepository empresas;
	@Autowired private AuditoriaRepository proyectos;
	@Autowired private AreaAuditableRepository areas;
	@Autowired private VulnerabilidadRepository vulnerabilidades;
	@Autowired private UsuarioRepository usuarios;

	// ================================================================ recorrido completo

	@Test
	void elAuditorYElClienteRecorrenElFlujoCompletoConLasCabecerasCorrectas() throws Exception {
		Mundo m = mundo();
		MockHttpSession auditor = sesion(m.auditor);
		MockHttpSession cliente = sesion(m.cliente);

		// Lista del proyecto con todos los tipos.
		String lista = mockMvc.perform(get("/proyectos/{p}/informes", m.proyectoUuid).session(auditor))
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(lista).contains("Matriz de riesgo").contains("Técnico").contains("Ejecutivo").contains("Mitigación")
			.contains("Final de entrega").contains("Crear borrador");
		sinEstilosNiScriptsEnLinea(lista);

		// Crear borrador (CSRF) y editor.
		MvcResult creado = mockMvc.perform(post("/proyectos/{p}/informes/TEC/borradores", m.proyectoUuid)
			.session(auditor).with(csrf())).andReturn();
		assertThat(creado.getResponse().getStatus()).isEqualTo(302);
		Matcher destino = UUID_EN_RUTA.matcher(creado.getResponse().getRedirectedUrl());
		assertThat(destino.find()).isTrue();
		String version = destino.group(1);

		String editor = pagina(auditor, "/informes/versiones/" + version);
		assertThat(editor).contains("Condiciones para publicar").contains("Falta la sección obligatoria")
			.contains("Alcance y metodología").contains("Publicar informe").contains("Vista previa");
		sinEstilosNiScriptsEnLinea(editor);
		// Solo se ofrecen los formatos válidos del tipo (§1.2): TEC no tiene XLSX.
		assertThat(editor).contains("value=\"DOCX\"").doesNotContain("value=\"XLSX\"");

		// Publicar sin narrativa obligatoria: vuelve al editor con las condiciones pendientes.
		mockMvc.perform(post("/informes/versiones/{v}/publicar", version).session(auditor).with(csrf())
			.param("formatos", "PDF")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection());
		assertThat(pagina(auditor, "/informes/versiones/" + version)).contains("No se puede publicar todavía");

		mockMvc.perform(post("/informes/versiones/{v}/narrativa", version).session(auditor).with(csrf())
			.param("seccion", "alcanceMetodologia").param("texto", "Alcance **ficticio** <script>alert(1)</script>"));
		String conTexto = pagina(auditor, "/informes/versiones/" + version);
		assertThat(conTexto).contains("Todo listo");
		assertThat(conTexto).doesNotContain("<script>alert(1)</script>");

		// Vista previa HTML con la marca NP, sin recursos del classpath ni estilos o scripts en línea.
		var vista = mockMvc.perform(get("/informes/versiones/{v}/vista-previa", version).session(auditor)).andReturn().getResponse();
		assertThat(vista.getStatus()).isEqualTo(200);
		assertThat(vista.getContentType()).startsWith("text/html");
		String html = vista.getContentAsString(StandardCharsets.UTF_8);
		assertThat(html).contains("DOCUMENTO NO PUBLICADO").contains("/css/informe-vista-previa.css")
			.doesNotContain("classpath:").doesNotContain("<script");
		assertThat(html).doesNotContain("alert(1)</script>");
		sinEstilosNiScriptsEnLinea(html);
		assertThat(vista.getHeader("Cache-Control")).contains("no-store");

		// Copia NO PUBLICADA: cabeceras de descarga.
		var copia = mockMvc.perform(post("/informes/versiones/{v}/generar", version).session(auditor).with(csrf())
			.param("formato", "DOCX")).andReturn().getResponse();
		assertThat(copia.getStatus()).isEqualTo(200);
		assertThat(copia.getContentType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
		assertThat(copia.getHeader("Content-Disposition")).startsWith("attachment;").contains("-NP.docx");
		assertThat(copia.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(copia.getHeader("Cache-Control")).contains("no-store");
		assertThat(new String(copia.getContentAsByteArray(), 0, 2, StandardCharsets.ISO_8859_1)).isEqualTo("PK");

		// El cliente no ve un borrador: ni la bandeja ni la verificación.
		assertThat(pagina(cliente, "/cliente/informes")).contains("Todavía no hay informes publicados");
		assertThat(mockMvc.perform(get("/informes/versiones/{v}/verificacion", version).session(cliente)).andReturn()
			.getResponse().getStatus()).isEqualTo(404);

		// Publicar PDF y DOCX.
		mockMvc.perform(post("/informes/versiones/{v}/publicar", version).session(auditor).with(csrf())
			.param("formatos", "PDF").param("formatos", "DOCX"));
		String publicado = pagina(auditor, "/informes/versiones/" + version);
		assertThat(publicado).contains("Archivos publicados").contains("Despublicar").doesNotContain("Publicar informe");
		String bandeja = pagina(cliente, "/cliente/informes");
		assertThat(bandeja).contains("Técnico · v1").contains("Verificar").contains(m.nombreProyecto);
		sinEstilosNiScriptsEnLinea(bandeja);

		// Descarga del PDF por el cliente con verificación de hash y las cabeceras del plan.
		Matcher enlace = Pattern.compile("/informes/archivos/([0-9a-f-]{36})").matcher(bandeja);
		List<String> archivos = new java.util.ArrayList<>();
		while (enlace.find()) {
			archivos.add(enlace.group(1));
		}
		assertThat(archivos).hasSize(2);
		for (String archivo : archivos) {
			var respuesta = mockMvc.perform(get("/informes/archivos/{a}", archivo).session(cliente)).andReturn().getResponse();
			assertThat(respuesta.getStatus()).isEqualTo(200);
			assertThat(respuesta.getHeader("Content-Disposition")).startsWith("attachment;").contains("filename=\"TH-");
			assertThat(respuesta.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
			assertThat(respuesta.getHeader("Cache-Control")).contains("no-store");
			assertThat(respuesta.getContentType()).isIn("application/pdf",
				"application/vnd.openxmlformats-officedocument.wordprocessingml.document");
			assertThat(respuesta.getContentAsByteArray().length).isPositive();
		}

		// Verificación para ambos roles.
		for (MockHttpSession quien : List.of(auditor, cliente)) {
			String verificacion = pagina(quien, "/informes/versiones/" + version + "/verificacion");
			assertThat(verificacion).contains("Verificación de integridad").contains("sha256sum").containsPattern("[0-9a-f]{64}");
		}

		// Despublicar (motivo mínimo 10): el cliente pasa a recibir 404 en todo.
		assertThat(mockMvc.perform(post("/informes/versiones/{v}/despublicar", version).session(auditor).with(csrf())
			.param("motivo", "corto")).andReturn().getResponse().getStatus()).isEqualTo(400);
		mockMvc.perform(post("/informes/versiones/{v}/despublicar", version).session(auditor).with(csrf())
			.param("motivo", "Se corrige la clasificación de un hallazgo"));
		// El historial del cliente conserva la fila con su fecha de retiro, pero sin descargas ni verificación.
		String historial = pagina(cliente, "/cliente/informes");
		assertThat(historial).contains("Retirado").contains("Ya no está disponible").doesNotContain("/informes/archivos/")
			.doesNotContain("/verificacion");
		for (String archivo : archivos) {
			assertThat(mockMvc.perform(get("/informes/archivos/{a}", archivo).session(cliente)).andReturn().getResponse()
				.getStatus()).isEqualTo(404);
		}
		assertThat(mockMvc.perform(get("/informes/versiones/{v}/verificacion", version).session(cliente)).andReturn()
			.getResponse().getStatus()).isEqualTo(404);
		// El auditor conserva el acceso.
		assertThat(mockMvc.perform(get("/informes/archivos/{a}", archivos.get(0)).session(auditor)).andReturn()
			.getResponse().getStatus()).isEqualTo(200);
	}

	// ================================================================ autorización, CSRF y validación

	@Test
	void sinSesionVaAlLoginYSinCsrfSeRechaza() throws Exception {
		Mundo m = mundo();
		for (String ruta : List.of("/proyectos/" + m.proyectoUuid + "/informes", "/cliente/informes",
			"/informes/versiones/" + UUID.randomUUID(), "/informes/archivos/" + UUID.randomUUID())) {
			var respuesta = mockMvc.perform(get(ruta)).andReturn().getResponse();
			assertThat(respuesta.getStatus()).as(ruta).isEqualTo(302);
			assertThat(respuesta.getRedirectedUrl()).contains("/login");
		}
		MockHttpSession auditor = sesion(m.auditor);
		assertThat(mockMvc.perform(post("/proyectos/{p}/informes/TEC/borradores", m.proyectoUuid).session(auditor))
			.andReturn().getResponse().getStatus()).isEqualTo(403);
		assertThat(mockMvc.perform(post("/informes/versiones/{v}/publicar", UUID.randomUUID()).session(auditor))
			.andReturn().getResponse().getStatus()).isEqualTo(403);
	}

	@Test
	void elClienteYLosAuditoresAjenosNoOperanYRecibenNoEncontradoODenegado() throws Exception {
		Mundo m = mundo();
		MockHttpSession auditor = sesion(m.auditor);
		String version = crearBorrador(auditor, m, "TEC");
		for (TinyHatPrincipal intruso : List.of(m.cliente, m.auditorSinAsignar, m.auditorAjeno)) {
			MockHttpSession sesion = sesion(intruso);
			boolean esCliente = "CLIENTE_ADMIN".equals(intruso.getRol());
			for (var peticion : List.of(
				post("/proyectos/{p}/informes/EJE/borradores", m.proyectoUuid),
				post("/informes/versiones/{v}/narrativa", version).param("seccion", "alcanceMetodologia").param("texto", "x"),
				post("/informes/versiones/{v}/generar", version).param("formato", "PDF"),
				post("/informes/versiones/{v}/publicar", version).param("formatos", "PDF"),
				post("/informes/versiones/{v}/despublicar", version).param("motivo", "Motivo suficientemente largo"),
				post("/informes/versiones/{v}/eliminar", version))) {
				int estado = mockMvc.perform(peticion.session(sesion).with(csrf())).andReturn().getResponse().getStatus();
				assertThat(estado).as(peticion.toString() + " como " + intruso.getRol()).isIn(403, 404);
			}
			for (String ruta : List.of("/informes/versiones/" + version, "/informes/versiones/" + version + "/vista-previa",
				"/informes/versiones/" + version + "/verificacion", "/proyectos/" + m.proyectoUuid + "/informes")) {
				int estado = mockMvc.perform(get(ruta).session(sesion)).andReturn().getResponse().getStatus();
				assertThat(estado).as(ruta + " como " + intruso.getRol()).isIn(403, 404);
			}
			if (!esCliente) {
				assertThat(mockMvc.perform(get("/cliente/informes").session(sesion)).andReturn().getResponse().getStatus())
					.isIn(403, 404);
			}
		}
		// El borrador sigue intacto.
		assertThat(pagina(auditor, "/informes/versiones/" + version)).contains("Alcance y metodología");
	}

	@Test
	void lasEntradasInvalidasSeRechazanConUn400() throws Exception {
		Mundo m = mundo();
		MockHttpSession auditor = sesion(m.auditor);
		String version = crearBorrador(auditor, m, "EJE");
		assertThat(status(get("/informes/versiones/no-es-un-uuid").session(auditor))).isEqualTo(400);
		assertThat(status(post("/proyectos/{p}/informes/XYZ/borradores", m.proyectoUuid).session(auditor).with(csrf()))).isEqualTo(400);
		assertThat(status(post("/informes/versiones/{v}/generar", version).session(auditor).with(csrf()).param("formato", "EXE")))
			.isEqualTo(400);
		assertThat(status(post("/informes/versiones/{v}/generar", version).session(auditor).with(csrf()))).isEqualTo(400);
		assertThat(status(post("/informes/versiones/{v}/narrativa", version).session(auditor).with(csrf())
			.param("seccion", "resumenEjecutivo").param("texto", "a".repeat(20001)))).isEqualTo(400);
		assertThat(status(post("/informes/versiones/{v}/despublicar", version).session(auditor).with(csrf())
			.param("motivo", "x".repeat(501)))).isEqualTo(400);
		// Un formato que el tipo no admite (EJE no tiene XLSX) no publica nada.
		mockMvc.perform(post("/informes/versiones/{v}/publicar", version).session(auditor).with(csrf()).param("formatos", "XLSX"));
		assertThat(pagina(auditor, "/informes/versiones/" + version)).doesNotContain("Archivos publicados");
	}

	@Test
	void elRateLimitDeGeneracionResponde429ConReintentoYSinContenidoInterno() throws Exception {
		Mundo m = mundo();
		MockHttpSession auditor = sesion(m.auditor);
		String version = crearBorrador(auditor, m, "MIT");
		for (int n = 0; n < 5; n++) {
			assertThat(status(post("/informes/versiones/{v}/generar", version).session(auditor).with(csrf())
				.param("formato", "XLSX"))).isEqualTo(200);
		}
		var limitada = mockMvc.perform(post("/informes/versiones/{v}/generar", version).session(auditor).with(csrf())
			.param("formato", "XLSX")).andReturn().getResponse();
		assertThat(limitada.getStatus()).isEqualTo(429);
		assertThat(limitada.getHeader("Retry-After")).isNotNull();
		assertThat(limitada.getContentAsString()).contains("RATE_LIMITED").doesNotContain("Exception");
	}

	// ================================================================ utilidades

	private String crearBorrador(MockHttpSession sesion, Mundo m, String tipo) throws Exception {
		MvcResult r = mockMvc.perform(post("/proyectos/{p}/informes/{t}/borradores", m.proyectoUuid, tipo)
			.session(sesion).with(csrf())).andReturn();
		Matcher d = UUID_EN_RUTA.matcher(r.getResponse().getRedirectedUrl());
		assertThat(d.find()).isTrue();
		return d.group(1);
	}

	private String pagina(MockHttpSession sesion, String ruta) throws Exception {
		var r = mockMvc.perform(get(ruta).session(sesion)).andReturn().getResponse();
		assertThat(r.getStatus()).as(ruta).isEqualTo(200);
		return r.getContentAsString(StandardCharsets.UTF_8);
	}

	private int status(org.springframework.test.web.servlet.RequestBuilder peticion) throws Exception {
		return mockMvc.perform(peticion).andReturn().getResponse().getStatus();
	}

	/** La CSP no se relaja: nada de atributos style, bloques style, manejadores on* ni scripts sin src. */
	private static void sinEstilosNiScriptsEnLinea(String html) {
		assertThat(html).doesNotContainPattern("(?i)\\sstyle\\s*=").doesNotContainPattern("(?i)<style")
			.doesNotContainPattern("(?i)\\son[a-z]+\\s*=").doesNotContainPattern("(?is)<script(?![^>]*\\ssrc=)[^>]*>");
	}

	private MockHttpSession sesion(TinyHatPrincipal principal) {
		MockHttpSession session = new MockHttpSession();
		long ahora = clock.millis();
		session.setAttribute(SessionActivityFilter.CREATED_AT_ATTRIBUTE, ahora);
		session.setAttribute(SessionActivityFilter.ORIGINAL_CREATED_AT_ATTRIBUTE, ahora);
		session.setAttribute(SessionActivityFilter.LAST_ACTIVITY_ATTRIBUTE, ahora);
		SecurityContext contexto = SecurityContextHolder.createEmptyContext();
		contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
		session.setAttribute("SPRING_SECURITY_CONTEXT", contexto);
		sessionRegistry.registerNewSession(session.getId(), principal);
		return session;
	}

	// ---------------------------------------------------------------- mundo

	private Mundo mundo() {
		Mundo m = new Mundo();
		long cuenta = cuenta("Cuenta Auditora Web");
		long ajena = cuenta("Cuenta Ajena Web");
		var org = new java.util.concurrent.atomic.AtomicReference<Empresa>();
		tenants.execute(cuenta, () -> {
			Empresa e = empresa(cuenta);
			org.set(e);
			Auditoria p = proyecto(cuenta, e);
			m.proyectoId = p.getIdAuditoria();
			m.proyectoUuid = p.getPublicId();
			m.nombreProyecto = p.getNombre();
			AreaAuditable red = area(p, "Red");
			vulnerabilidad(red, "Servicio expuesto a Internet", 9, 8, 6, 8, "Abierta", "Restringir el acceso por VPN");
			vulnerabilidad(red, "Cabeceras HTTP ausentes", 2, 1, 0, 6, "Mitigada", "Configurar las cabeceras");
		});
		m.auditor = principal(insertar(cuenta, null, "AUDITOR"));
		jdbc.update("INSERT INTO asignacion_auditor_proyecto (usuario_id, proyecto_id, cuenta_id) VALUES (?, ?, ?)",
			m.auditor.getUserId(), m.proyectoId, cuenta);
		m.auditorSinAsignar = principal(insertar(cuenta, null, "AUDITOR"));
		m.cliente = principal(insertar(cuenta, org.get().getId(), "CLIENTE_ADMIN"));
		jdbc.update("INSERT INTO asignacion_cliente_proyecto (usuario_id, proyecto_id, cuenta_id) VALUES (?, ?, ?)", m.cliente.getUserId(), m.proyectoId, cuenta);
		m.auditorAjeno = principal(insertar(ajena, null, "AUDITOR"));
		return m;
	}

	private TinyHatPrincipal principal(long usuarioId) {
		return new TinyHatPrincipal(usuarios.findById(usuarioId).orElseThrow(), true);
	}

	private long insertar(long cuenta, Long organizacion, String rol) {
		String correo = "web-" + UUID.randomUUID() + "@example.test";
		jdbc.update("INSERT INTO usuario (cuenta_id, organizacion_id, rol, estado, email_normalizado, password_hash, public_id) "
			+ "VALUES (?, ?, ?, 'ACTIVO', ?, 'test-hash', ?)", cuenta, organizacion, rol, correo, UUID.randomUUID().toString());
		return jdbc.queryForObject("SELECT id FROM usuario WHERE email_normalizado = ?", Long.class, correo);
	}

	private long cuenta(String nombre) {
		String publicId = UUID.randomUUID().toString();
		jdbc.update("INSERT INTO cuenta (public_id, nombre, tipo, estado, creado_en) "
			+ "VALUES (?, ?, 'AUDITOR', 'ACTIVA', UTC_TIMESTAMP(6))", publicId, nombre);
		return jdbc.queryForObject("SELECT id FROM cuenta WHERE public_id = ?", Long.class, publicId);
	}

	private Empresa empresa(long cuenta) {
		Empresa e = new Empresa();
		e.setNombre("Organización " + UUID.randomUUID());
		e.setNit(UUID.randomUUID().toString());
		e.setSector("Tecnología");
		e.setCuentaId(cuenta);
		return empresas.save(e);
	}

	private Auditoria proyecto(long cuenta, Empresa organizacion) {
		Auditoria p = new Auditoria();
		p.setNombre("Proyecto " + UUID.randomUUID());
		p.setTipoAuditoria("Auditoría de seguridad");
		p.setFechaInico(LocalDate.now());
		p.setFechaFin(LocalDate.now().plusDays(10));
		p.setEstado("En Progreso");
		p.setEmpresa(organizacion);
		p.setCuentaId(cuenta);
		return proyectos.save(p);
	}

	private AreaAuditable area(Auditoria proyecto, String nombre) {
		AreaAuditable a = new AreaAuditable();
		a.setNombre(nombre);
		a.setDescripcion("Descripción de " + nombre);
		a.setAuditoria(proyecto);
		a.setCuentaId(proyecto.getCuentaId());
		return areas.save(a);
	}

	private void vulnerabilidad(AreaAuditable area, String nombre, int c, int i, int a, int p, String estado, String plan) {
		Vulnerabilidad v = new Vulnerabilidad();
		v.setNombre(nombre);
		v.setTipo("Configuración");
		v.setConfidencialidad(c);
		v.setIntegridad(i);
		v.setDisponibilidad(a);
		v.setProbabilidad(p);
		v.setImpacto(new BigDecimal("5.00"));
		v.setNivelRiesgo("Medio");
		v.setEstado(estado);
		v.setPlanMitigacion(plan);
		v.setFechaDeteccion(LocalDate.now());
		v.setAreaAuditable(area);
		v.setCuentaId(area.getCuentaId());
		vulnerabilidades.save(v);
	}

	private static final class Mundo {
		long proyectoId;
		String proyectoUuid;
		String nombreProyecto;
		TinyHatPrincipal auditor;
		TinyHatPrincipal auditorSinAsignar;
		TinyHatPrincipal auditorAjeno;
		TinyHatPrincipal cliente;

	}
}

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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * El administrador de la MiPyme de punta a punta: el auditor lo agrega con una contraseña temporal, inicia sesión con
 * ella, está obligado a cambiarla, solo entra mientras el proyecto esté activo y ve los informes publicados con sus
 * fechas de publicación y de retiro.
 */
@SpringBootTest(properties = {
	"hexora.network.discovery-enabled=false",
	"hexora.network.monitor-interval-ms=3600000",
	"hexora.network.discovery-interval-ms=3600000",
	"hexora.network.retention-cleanup-interval-ms=3600000",
	"tinyhat.security.mfa.required=false",
	"tinyhat.security.ratelimit.api-general.capacity=100000",
	"tinyhat.security.ratelimit.api-general.refill-tokens=100000",
	"tinyhat.security.ratelimit.auth.capacity=100000",
	"tinyhat.security.ratelimit.auth.refill-tokens=100000"
})
@AutoConfigureMockMvc
class AccesoClienteIntegrationTest extends IsolatedMySqlTestDatabase {

	private static final Path DIRECTORIO = crearDirectorio();
	private static final Pattern CLAVE = Pattern.compile("id=\"clave-temporal\">([^<]+)<");
	private static final Pattern VERSION = Pattern.compile("/informes/versiones/([0-9a-f-]{36})");

	private static Path crearDirectorio() {
		try {
			return Files.createTempDirectory("informes-cliente-");
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
	@Autowired private PasswordEncoder codificador;

	// ================================================================ el recorrido completo

	@Test
	void elAuditorLoAgregaElClienteEntraCambiaLaContrasenaYVeLosInformes() throws Exception {
		Mundo m = mundo();
		MockHttpSession auditor = sesion(m.auditor);

		// 1. El auditor lo agrega: ve la contraseña temporal una sola vez.
		String correo = "pyme-" + UUID.randomUUID() + "@empresa.test";
		MvcResult agregado = mockMvc.perform(post("/proyectos/{p}/acceso-cliente", m.proyectoUuid).session(auditor).with(csrf())
			.param("correo", correo)).andReturn();
		assertThat(agregado.getResponse().getStatus()).isEqualTo(302);
		String pantalla = mockMvc.perform(get("/proyectos/{p}/acceso-cliente", m.proyectoUuid).session(auditor)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		Matcher clave = CLAVE.matcher(pantalla);
		assertThat(clave.find()).as("la contraseña temporal aparece en pantalla").isTrue();
		String temporal = clave.group(1);
		jdbcTemporal = temporal;
		assertThat(temporal).matches("[A-Za-z2-9]{4}(-[A-Za-z2-9]{4}){3}");
		assertThat(pantalla).contains(correo).contains("Entregue estas credenciales");
		// Recargar la pantalla ya no la muestra, y en la base no está en claro.
		String recarga = mockMvc.perform(get("/proyectos/{p}/acceso-cliente", m.proyectoUuid).session(auditor)).andReturn()
			.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(recarga).doesNotContain(temporal).contains(correo).contains("Aún no cambia la contraseña");
		assertThat(jdbc.queryForObject("SELECT password_hash FROM usuario WHERE email_normalizado = ?", String.class, correo))
			.doesNotContain(temporal).startsWith("{");
		assertThat(jdbc.queryForObject("SELECT debe_cambiar_password FROM usuario WHERE email_normalizado = ?", Boolean.class, correo))
			.isTrue();
		assertThat(jdbc.queryForObject("SELECT CAST(GROUP_CONCAT(CAST(detalle AS CHAR)) AS CHAR) FROM audit_event WHERE evento LIKE 'CLIENTE\\_ACCESO%' "
			+ "AND cuenta_id = ?", String.class, m.cuenta)).doesNotContain(temporal);

		// 2. Inicia sesión con la temporal; antes de cambiarla solo puede cambiarla.
		MockHttpSession cliente = iniciarSesion(correo);
		var intento = mockMvc.perform(get("/cliente").session(cliente)).andReturn().getResponse();
		assertThat(intento.getStatus()).isEqualTo(302);
		assertThat(intento.getRedirectedUrl()).endsWith("/cliente/contrasena");
		assertThat(mockMvc.perform(get("/cliente/informes").session(cliente)).andReturn().getResponse().getRedirectedUrl())
			.endsWith("/cliente/contrasena");
		String formulario = mockMvc.perform(get("/cliente/contrasena").session(cliente)).andReturn().getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		assertThat(formulario).contains("temporal").contains("Contraseña actual");

		// 3. Cambiar la contraseña: reglas y cierre de sesión.
		assertThat(cambiar(cliente, temporal, "corta", "corta")).as("política de contraseñas").endsWith("/cliente/contrasena");
		assertThat(cambiar(cliente, "otra-actual-incorrecta", "Una frase larga y propia 2026", "Una frase larga y propia 2026"))
			.endsWith("/cliente/contrasena");
		assertThat(cambiar(cliente, temporal, "Una frase larga y propia 2026", "no coincide con la anterior"))
			.endsWith("/cliente/contrasena");
		assertThat(jdbc.queryForObject("SELECT debe_cambiar_password FROM usuario WHERE email_normalizado = ?", Boolean.class, correo))
			.as("sin cambios por los intentos fallidos").isTrue();
		assertThat(cambiar(cliente, temporal, temporal, temporal)).as("debe ser distinta de la actual").endsWith("/cliente/contrasena");
		assertThat(cambiar(cliente, temporal, "Una frase larga y propia 2026", "Una frase larga y propia 2026"))
			.endsWith("/login?cambiada");
		assertThat(jdbc.queryForObject("SELECT debe_cambiar_password FROM usuario WHERE email_normalizado = ?", Boolean.class, correo))
			.isFalse();

		// La temporal ya no sirve; la nueva sí, y lleva directo al panel.
		assertThat(autenticar(correo, temporal)).as("la temporal queda inservible").isNull();
		MockHttpSession nueva = autenticar(correo, "Una frase larga y propia 2026");
		assertThat(nueva).isNotNull();
		var panel = mockMvc.perform(get("/cliente").session(nueva)).andReturn().getResponse();
		assertThat(panel.getStatus()).isEqualTo(200);
		String html = panel.getContentAsString(StandardCharsets.UTF_8);
		assertThat(html).contains(m.nombreProyecto).contains(m.nombreOrganizacion).contains("Aún no hay informes publicados");

		// 4. El auditor publica un informe: el cliente lo ve con su fecha y puede descargarlo.
		String version = publicarEje(auditor, m);
		String conInforme = mockMvc.perform(get("/cliente").session(nueva)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(conInforme).contains("Ejecutivo · v1").contains("Vigente").contains("/informes/archivos/")
			.contains(LocalDate.now(java.time.ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));

		// 5. Si lo despublica, el cliente ve la fila «Retirado» con su fecha y ya no puede descargarlo.
		mockMvc.perform(post("/informes/versiones/{v}/despublicar", version).session(auditor).with(csrf())
			.param("motivo", "Se corrige la clasificación de un hallazgo"));
		String retirado = mockMvc.perform(get("/cliente/informes").session(nueva)).andReturn().getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		assertThat(retirado).contains("Ejecutivo · v1").contains("Retirado").contains("Ya no está disponible")
			.doesNotContain("/informes/archivos/").doesNotContain("Se corrige la clasificación");
	}

	// ================================================================ solo mientras el proyecto esté activo

	@Test
	void soloEntraMientrasElProyectoEstaActivoYSeLeCierraLaSesionSiDejaDeEstarlo() throws Exception {
		Mundo m = mundo();
		MockHttpSession auditor = sesion(m.auditor);
		String correo = agregar(auditor, m, "activo-" + UUID.randomUUID() + "@empresa.test");
		String temporal = jdbcTemporal;
		jdbc.update("UPDATE usuario SET debe_cambiar_password = FALSE WHERE email_normalizado = ?", correo);
		jdbc.update("UPDATE usuario SET password_hash = ? WHERE email_normalizado = ?", codificador.encode("Clave propia del cliente 1"), correo);

		MockHttpSession sesionCliente = autenticar(correo, "Clave propia del cliente 1");
		assertThat(sesionCliente).isNotNull();
		assertThat(mockMvc.perform(get("/cliente").session(sesionCliente)).andReturn().getResponse().getStatus()).isEqualTo(200);

		// El proyecto se completa: la sesión en curso se cierra y no puede volver a entrar.
		jdbc.update("UPDATE auditoria SET estado = 'Completado' WHERE id_auditoria = ?", m.proyectoId);
		var cerrada = mockMvc.perform(get("/cliente").session(sesionCliente)).andReturn().getResponse();
		assertThat(cerrada.getStatus()).isIn(302, 401);
		assertThat(autenticar(correo, "Clave propia del cliente 1")).as("proyecto completado: sin acceso").isNull();

		// Vuelve a estar activo: puede entrar otra vez.
		jdbc.update("UPDATE auditoria SET estado = 'En Progreso' WHERE id_auditoria = ?", m.proyectoId);
		assertThat(autenticar(correo, "Clave propia del cliente 1")).isNotNull();
		assertThat(temporal).isNotBlank();
	}

	@Test
	void retirarElAccesoLoDejaFueraYPuedeVolverAdarse() throws Exception {
		Mundo m = mundo();
		MockHttpSession auditor = sesion(m.auditor);
		String correo = agregar(auditor, m, "retiro-" + UUID.randomUUID() + "@empresa.test");
		jdbc.update("UPDATE usuario SET debe_cambiar_password = FALSE, password_hash = ? WHERE email_normalizado = ?",
			codificador.encode("Clave propia del cliente 2"), correo);
		String usuarioUuid = jdbc.queryForObject("SELECT public_id FROM usuario WHERE email_normalizado = ?", String.class, correo);

		MockHttpSession sesionCliente = autenticar(correo, "Clave propia del cliente 2");
		assertThat(sesionCliente).isNotNull();

		mockMvc.perform(post("/proyectos/{p}/acceso-cliente/{u}/retirar", m.proyectoUuid, usuarioUuid).session(auditor).with(csrf()));
		assertThat(jdbc.queryForObject("SELECT retirado_en IS NOT NULL FROM asignacion_cliente_proyecto a JOIN usuario u ON u.id = a.usuario_id "
			+ "WHERE u.email_normalizado = ?", Boolean.class, correo)).as("la fila queda como historia").isTrue();
		assertThat(autenticar(correo, "Clave propia del cliente 2")).as("sin acceso").isNull();
		assertThat(mockMvc.perform(get("/cliente").session(sesionCliente)).andReturn().getResponse().getStatus()).isIn(302, 401);
		String lista = mockMvc.perform(get("/proyectos/{p}/acceso-cliente", m.proyectoUuid).session(auditor)).andReturn().getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		assertThat(lista).contains(correo).contains("Retirado").contains("Volver a dar acceso");

		// Volver a darle acceso no cambia su contraseña.
		MvcResult vuelto = mockMvc.perform(post("/proyectos/{p}/acceso-cliente", m.proyectoUuid).session(auditor).with(csrf())
			.param("correo", correo)).andReturn();
		assertThat(vuelto.getFlashMap().get("credencial")).isNotNull();
		assertThat(autenticar(correo, "Clave propia del cliente 2")).isNotNull();
	}

	@Test
	void restablecerDaUnaTemporalNuevaYObligaACambiarla() throws Exception {
		Mundo m = mundo();
		MockHttpSession auditor = sesion(m.auditor);
		String correo = agregar(auditor, m, "reset-" + UUID.randomUUID() + "@empresa.test");
		String primera = jdbcTemporal;
		String usuarioUuid = jdbc.queryForObject("SELECT public_id FROM usuario WHERE email_normalizado = ?", String.class, correo);
		MvcResult r = mockMvc.perform(post("/proyectos/{p}/acceso-cliente/{u}/restablecer", m.proyectoUuid, usuarioUuid)
			.session(auditor).with(csrf())).andReturn();
		String pantalla = mockMvc.perform(get("/proyectos/{p}/acceso-cliente", m.proyectoUuid).session(auditor)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		Matcher nueva = CLAVE.matcher(pantalla);
		assertThat(nueva.find()).isTrue();
		assertThat(nueva.group(1)).isNotEqualTo(primera);
		assertThat(autenticar(correo, primera)).as("la anterior deja de servir").isNull();
		assertThat(autenticar(correo, nueva.group(1))).isNotNull();
		assertThat(jdbc.queryForObject("SELECT debe_cambiar_password FROM usuario WHERE email_normalizado = ?", Boolean.class, correo)).isTrue();
	}

	// ================================================================ aislamiento y validación

	@Test
	void soloElAuditorAsignadoGestionaElAccesoYElClienteNo() throws Exception {
		Mundo m = mundo();
		MockHttpSession auditor = sesion(m.auditor);
		String correo = agregar(auditor, m, "aislado-" + UUID.randomUUID() + "@empresa.test");
		jdbc.update("UPDATE usuario SET debe_cambiar_password = FALSE, password_hash = ? WHERE email_normalizado = ?",
			codificador.encode("Clave propia del cliente 3"), correo);
		MockHttpSession cliente = autenticar(correo, "Clave propia del cliente 3");

		for (TinyHatPrincipal intruso : new TinyHatPrincipal[] {m.auditorSinAsignar, m.auditorAjeno}) {
			MockHttpSession s = sesion(intruso);
			assertThat(mockMvc.perform(get("/proyectos/{p}/acceso-cliente", m.proyectoUuid).session(s)).andReturn().getResponse()
				.getStatus()).isEqualTo(404);
			assertThat(mockMvc.perform(post("/proyectos/{p}/acceso-cliente", m.proyectoUuid).session(s).with(csrf())
				.param("correo", "x@empresa.test")).andReturn().getResponse().getStatus()).isEqualTo(404);
		}
		// El cliente no gestiona accesos ni ve pantallas del auditor.
		for (String ruta : new String[] {"/proyectos/" + m.proyectoUuid + "/acceso-cliente", "/dashboard",
			"/proyectos/" + m.proyectoUuid + "/informes"}) {
			assertThat(mockMvc.perform(get(ruta).session(cliente)).andReturn().getResponse().getStatus()).as(ruta).isIn(403, 404);
		}
		assertThat(mockMvc.perform(post("/proyectos/{p}/acceso-cliente", m.proyectoUuid).session(cliente).with(csrf())
			.param("correo", "otro@empresa.test")).andReturn().getResponse().getStatus()).isIn(403, 404);
		// Un auditor no entra a la vista del cliente.
		assertThat(mockMvc.perform(get("/cliente").session(auditor)).andReturn().getResponse().getStatus()).isIn(403, 404);
		// Sin sesión, al login; sin CSRF, rechazado.
		assertThat(mockMvc.perform(get("/cliente")).andReturn().getResponse().getRedirectedUrl()).contains("/login");
		assertThat(mockMvc.perform(post("/proyectos/{p}/acceso-cliente", m.proyectoUuid).session(auditor)
			.param("correo", "z@empresa.test")).andReturn().getResponse().getStatus()).isEqualTo(403);
	}

	@Test
	void noSeDaAccesoACorreosAjenosNiInvalidosNiSeFiltraSiExisten() throws Exception {
		Mundo m = mundo();
		MockHttpSession auditor = sesion(m.auditor);
		// Un correo de un auditor existente no se puede usar.
		var r1 = mockMvc.perform(post("/proyectos/{p}/acceso-cliente", m.proyectoUuid).session(auditor).with(csrf())
			.param("correo", m.auditor.getUsername())).andReturn();
		assertThat(r1.getFlashMap().get("error")).isEqualTo("No se puede dar acceso a ese correo.");
		assertThat(r1.getFlashMap().get("credencial")).isNull();
		// Correos inválidos.
		for (String malo : new String[] {"sin-arroba", "a@b", "con espacio@empresa.test", " ", "x".repeat(260) + "@e.co"}) {
			var r = mockMvc.perform(post("/proyectos/{p}/acceso-cliente", m.proyectoUuid).session(auditor).with(csrf()).param("correo", malo))
				.andReturn();
			assertThat(r.getResponse().getStatus()).as(malo).isIn(302, 400);
			assertThat(r.getFlashMap().get("credencial")).as(malo).isNull();
		}
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE rol = 'CLIENTE_ADMIN' AND cuenta_id = ?", Integer.class, m.cuenta)).isZero();
	}

	@Test
	void unaCuentaDeClienteAnteriorSinAsignacionesSigueEntrandoComoAntes() throws Exception {
		Mundo m = mundo();
		String correo = "antiguo-" + UUID.randomUUID() + "@empresa.test";
		jdbc.update("INSERT INTO usuario (cuenta_id, organizacion_id, rol, estado, email_normalizado, password_hash, public_id) "
			+ "VALUES (?, ?, 'CLIENTE_ADMIN', 'ACTIVO', ?, ?, ?)", m.cuenta, m.organizacionId, correo,
			codificador.encode("Clave propia del cliente 4"), UUID.randomUUID().toString());
		MockHttpSession s = autenticar(correo, "Clave propia del cliente 4");
		assertThat(s).as("compatibilidad con invitaciones por correo anteriores").isNotNull();
		var panel = mockMvc.perform(get("/cliente").session(s)).andReturn().getResponse();
		assertThat(panel.getStatus()).isEqualTo(200);
		assertThat(panel.getContentAsString(StandardCharsets.UTF_8)).contains("Todavía no tiene un proyecto activo asignado");
	}

	// ================================================================ utilidades

	private String jdbcTemporal;

	/** Agrega al cliente por la pantalla del auditor y devuelve su correo; deja la temporal en {@code jdbcTemporal}. */
	private String agregar(MockHttpSession auditor, Mundo m, String correo) throws Exception {
		MvcResult r = mockMvc.perform(post("/proyectos/{p}/acceso-cliente", m.proyectoUuid).session(auditor).with(csrf())
			.param("correo", correo)).andReturn();
		String pantalla = mockMvc.perform(get("/proyectos/{p}/acceso-cliente", m.proyectoUuid).session(auditor)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		Matcher clave = CLAVE.matcher(pantalla);
		assertThat(clave.find()).isTrue();
		jdbcTemporal = clave.group(1);
		return correo;
	}

	/** Inicia sesión como lo hace el formulario (credenciales reales) y devuelve la sesión, o null si se rechaza. */
	private MockHttpSession autenticar(String correo, String clave) throws Exception {
		MvcResult r = mockMvc.perform(post("/login").with(csrf()).param("email", correo).param("password", clave)).andReturn();
		String destino = r.getResponse().getRedirectedUrl();
		if (destino == null || destino.contains("error")) {
			return null;
		}
		return (MockHttpSession) r.getRequest().getSession(false);
	}

	private MockHttpSession iniciarSesion(String correo) throws Exception {
		MockHttpSession s = autenticar(correo, jdbcTemporalDe(correo));
		assertThat(s).as("la contraseña temporal permite iniciar sesión").isNotNull();
		return s;
	}

	private String jdbcTemporalDe(String correo) {
		return jdbcTemporal;
	}

	private String cambiar(MockHttpSession sesion, String actual, String nueva, String confirmar) throws Exception {
		return mockMvc.perform(post("/cliente/contrasena").session(sesion).with(csrf()).param("actual", actual).param("nueva", nueva)
			.param("confirmar", confirmar)).andReturn().getResponse().getRedirectedUrl();
	}

	private String publicarEje(MockHttpSession auditor, Mundo m) throws Exception {
		MvcResult r = mockMvc.perform(post("/proyectos/{p}/informes/EJE/borradores", m.proyectoUuid).session(auditor).with(csrf())).andReturn();
		Matcher d = VERSION.matcher(r.getResponse().getRedirectedUrl());
		assertThat(d.find()).isTrue();
		String version = d.group(1);
		mockMvc.perform(post("/informes/versiones/{v}/narrativa", version).session(auditor).with(csrf())
			.param("seccion", "resumenEjecutivo").param("texto", "Resumen ficticio de la evaluación."));
		mockMvc.perform(post("/informes/versiones/{v}/publicar", version).session(auditor).with(csrf()).param("formatos", "PDF"));
		assertThat(jdbc.queryForObject("SELECT estado FROM informe_version WHERE public_id = ?", String.class, version)).isEqualTo("PUBLICADO");
		return version;
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
		m.cuenta = cuenta("Firma Auditora Demo");
		long ajena = cuenta("Cuenta Ajena");
		AtomicReference<Empresa> org = new AtomicReference<>();
		tenants.execute(m.cuenta, () -> {
			Empresa e = empresa(m.cuenta);
			org.set(e);
			Auditoria p = proyecto(m.cuenta, e);
			m.proyectoId = p.getIdAuditoria();
			m.proyectoUuid = p.getPublicId();
			m.nombreProyecto = p.getNombre();
			m.nombreOrganizacion = e.getNombre();
			AreaAuditable red = area(p, "Red");
			vulnerabilidad(red, "Servicio expuesto a Internet", 9, 8, 6, 8, "Abierta", "Restringir el acceso por VPN");
		});
		m.organizacionId = org.get().getId();
		m.auditor = principal(insertar(m.cuenta, null, "AUDITOR"));
		jdbc.update("INSERT INTO asignacion_auditor_proyecto (usuario_id, proyecto_id, cuenta_id) VALUES (?, ?, ?)",
			m.auditor.getUserId(), m.proyectoId, m.cuenta);
		m.auditorSinAsignar = principal(insertar(m.cuenta, null, "AUDITOR"));
		m.auditorAjeno = principal(insertar(ajena, null, "AUDITOR"));
		return m;
	}

	private TinyHatPrincipal principal(long usuarioId) {
		return new TinyHatPrincipal(usuarios.findById(usuarioId).orElseThrow(), true);
	}

	private long insertar(long cuenta, Long organizacion, String rol) {
		String correo = "acc-" + UUID.randomUUID() + "@example.test";
		jdbc.update("INSERT INTO usuario (cuenta_id, organizacion_id, rol, estado, email_normalizado, password_hash, public_id) "
			+ "VALUES (?, ?, ?, 'ACTIVO', ?, ?, ?)", cuenta, organizacion, rol, correo, codificador.encode("no-se-usa-123456"),
			UUID.randomUUID().toString());
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
		long cuenta;
		long proyectoId;
		long organizacionId;
		String proyectoUuid;
		String nombreProyecto;
		String nombreOrganizacion;
		TinyHatPrincipal auditor;
		TinyHatPrincipal auditorSinAsignar;
		TinyHatPrincipal auditorAjeno;
	}
}

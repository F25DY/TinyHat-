package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Fase 14 (F14-CA3): inventario final de endpoints con evidencia ejecutable.
 *
 * <p>Recorre todos los mapeos reales de la aplicación y exige que cada uno sea una ruta pública
 * declarada a propósito en {@link #PUBLIC_ROUTES} o que un usuario anónimo sea rechazado (401 en
 * {@code /api/**}, redirección a {@code /login} en la web). Una ruta nueva sin protección hace
 * fallar la prueba hasta que alguien la declare pública de forma explícita. Las rutas públicas no
 * se invocan, para no crear datos. Escribe {@code target/inventario-endpoints.md}.
 */
@SpringBootTest(properties = {
	"spring.jpa.hibernate.ddl-auto=validate",
	"spring.flyway.clean-disabled=true",
	"hexora.network.discovery-enabled=false",
	"hexora.network.monitor-interval-ms=2147483647",
	"hexora.network.retention-cleanup-interval-ms=2147483647"
})
@AutoConfigureMockMvc
class FinalEndpointInventoryIntegrationTest {

	/** Única lista de rutas accesibles sin sesión. Agregar una ruta aquí es una decisión de seguridad. */
	private static final Set<String> PUBLIC_ROUTES = Set.of(
		"GET /",
		"GET /login",
		"GET /recuperar-contrasena",
		"GET /registro",
		"POST /registro",
		"GET /verificar/{token}",
		"GET /verificar-correo/{token}",
		"GET /invitacion/{token}",
		"POST /invitacion/{token}",
		"GET /recuperar",
		"POST /recuperar",
		"GET /recuperar/{token}",
		"POST /recuperar/{token}"
	);

	private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.46")
		.withDatabaseName("hexora_phase14")
		.withUsername("hexora_test")
		.withPassword("phase14_test_password");

	static {
		MYSQL.start();
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private org.springframework.core.env.Environment environment;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@DynamicPropertySource
	static void configureDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
		registry.add("spring.flyway.user", MYSQL::getUsername);
		registry.add("spring.flyway.password", MYSQL::getPassword);
	}

	@AfterAll
	static void stopDatabase() {
		MYSQL.stop();
	}

	private final AtomicInteger requestSequence = new AtomicInteger();

	private record Row(String method, String path, String handler, String rule, String anonymous) {
	}

	@Test
	void everyRouteIsDeclaredPublicOrDeniedToAnonymousUsers() throws Exception {
		List<Row> rows = new ArrayList<>();
		Set<String> seenKeys = new LinkedHashSet<>();

		for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
			RequestMappingInfo info = entry.getKey();
			HandlerMethod handler = entry.getValue();
			Class<?> controller = ClassUtils.getUserClass(handler.getBeanType());
			if (!controller.getPackageName().startsWith("com.hexora")) {
				continue;
			}
			Set<RequestMethod> declared = info.getMethodsCondition().getMethods();
			List<String> methods = declared.isEmpty()
				? List.of("GET")
				: declared.stream().map(Enum::name).sorted().toList();
			String rule = ruleOf(handler, controller);
			String label = controller.getSimpleName() + "#" + handler.getMethod().getName();

			for (String pattern : new TreeSet<>(info.getPatternValues())) {
				for (String method : methods) {
					String key = method + " " + pattern;
					seenKeys.add(key);
					if (PUBLIC_ROUTES.contains(key)) {
						assertThat(isDeclaredPublic(handler, controller))
							.as("la ruta pública %s debe declararlo con permitAll() o ser AuthController", key)
							.isTrue();
						rows.add(new Row(method, pattern, label, rule, "pública (declarada)"));
						continue;
					}
					assertThat(rule).as("regla de método de %s", key).isNotEqualTo("(sin regla)");
					MvcResult result = anonymous(method, pattern);
					assertDenied(key, result);
					rows.add(new Row(method, pattern, label, rule, describe(result)));
				}
			}
		}

		assertThat(seenKeys)
			.as("toda ruta pública declarada existe en los mapeos reales (sin entradas obsoletas)")
			.containsAll(PUBLIC_ROUTES);
		writeInventory(rows);
	}

	@Test
	void unmappedFrameworkAndReservedRoutesAreDeniedToAnonymousUsers() throws Exception {
		for (String path : List.of(
			"/actuator", "/actuator/health", "/actuator/env", "/h2-console", "/ws/network",
			"/not-a-registered-route", "/api/not-a-registered-route")) {
			assertDenied("GET " + path, anonymous("GET", path));
		}
		MvcResult agent = anonymous("GET", "/api/agent/anything");
		assertThat(agent.getResponse().getStatus()).as("prefijo /api/agent/** reservado").isEqualTo(401);
	}

	@Test
	void defaultConfigurationBindsToLoopbackAndKeepsDemoAndRangeSafeguardsOff() {
		// Decisión 10 del plan: demo en red de laboratorio, enlazada a 127.0.0.1, sin sembrado ni rangos públicos.
		assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
		assertThat(environment.getProperty("tinyhat.demo.seed.enabled", Boolean.class)).isFalse();
		assertThat(environment.getProperty("tinyhat.security.network.allow-public-ranges", Boolean.class)).isFalse();
		assertThat(environment.getProperty("tinyhat.security.network.max-prefix", Integer.class)).isEqualTo(24);
		assertThat(environment.getProperty("tinyhat.security.mfa.required", Boolean.class)).isTrue();
	}

	private MvcResult anonymous(String method, String pattern) throws Exception {
		String path = pattern.replaceAll("\\{[^}]+}", "1");
		MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method), path);
		if (path.startsWith("/api/")) {
			builder.accept(MediaType.APPLICATION_JSON);
		}
		if (!"GET".equals(method)) {
			// Con CSRF válido, el rechazo que se observa es el de autorización y no el del token.
			builder.with(csrf());
		}
		// Una IP distinta por petición: los límites de intentos por IP no deben enmascarar la autorización.
		int sequence = requestSequence.incrementAndGet();
		builder.with(request -> {
			request.setRemoteAddr("10.14." + (sequence / 250) + "." + (sequence % 250 + 1));
			return request;
		});
		return mockMvc.perform(builder).andReturn();
	}

	private static void assertDenied(String key, MvcResult result) {
		int status = result.getResponse().getStatus();
		String location = result.getResponse().getRedirectedUrl();
		boolean denied = status == 401
			|| status == 403
			|| (status == 302 && location != null && location.endsWith("/login"));
		assertThat(denied)
			.as("%s sin sesión debe ser rechazada, pero respondió %s %s", key, status, location)
			.isTrue();
		if (key.contains(" /api/")) {
			assertThat(status).as("%s: la API responde 401 JSON, nunca redirección", key).isEqualTo(401);
		}
	}

	private static String describe(MvcResult result) {
		int status = result.getResponse().getStatus();
		return status == 302 ? "302 → /login" : String.valueOf(status);
	}

	private static String ruleOf(HandlerMethod handler, Class<?> controller) {
		PreAuthorize onMethod = handler.getMethod().getAnnotation(PreAuthorize.class);
		PreAuthorize onClass = controller.getAnnotation(PreAuthorize.class);
		if (onMethod != null && onClass != null) {
			return onClass.value() + " Y " + onMethod.value();
		}
		if (onMethod != null) {
			return onMethod.value();
		}
		if (onClass != null) {
			return onClass.value();
		}
		return "(sin regla)";
	}

	private static boolean isDeclaredPublic(HandlerMethod handler, Class<?> controller) {
		if (controller.getSimpleName().equals("AuthController")) {
			return true;
		}
		return "permitAll()".equals(ruleOf(handler, controller));
	}

	private static void writeInventory(List<Row> rows) throws Exception {
		rows.sort(Comparator.comparing(Row::path).thenComparing(Row::method));
		StringBuilder md = new StringBuilder();
		md.append("| Método | Ruta | Controlador#método | Regla de método | Sin sesión |\n");
		md.append("|---|---|---|---|---|\n");
		for (Row row : rows) {
			md.append("| ").append(row.method()).append(" | `").append(row.path()).append("` | ")
				.append(row.handler()).append(" | `").append(row.rule().replace("|", "\\|")).append("` | ")
				.append(row.anonymous()).append(" |\n");
		}
		Path target = Path.of("target", "inventario-endpoints.md");
		Files.createDirectories(target.getParent());
		Files.writeString(target, md.toString());
	}
}

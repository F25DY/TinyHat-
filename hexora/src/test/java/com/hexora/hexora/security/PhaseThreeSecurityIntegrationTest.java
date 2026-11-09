package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import com.hexora.hexora.controller.AuthController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties = {
	"spring.jpa.hibernate.ddl-auto=validate",
	"spring.flyway.clean-disabled=true",
	"tinyhat.security.mfa.required=false",
	"hexora.network.discovery-enabled=false",
	"hexora.network.monitor-interval-ms=2147483647",
	"hexora.network.retention-cleanup-interval-ms=2147483647",
	"tinyhat.security.ratelimit.auth.capacity=100",
	"tinyhat.security.ratelimit.auth.refill-tokens=100",
	"tinyhat.security.login.throttle.schedule-seconds[0]=0",
	"tinyhat.security.login.throttle.schedule-seconds[1]=0",
	"tinyhat.security.login.throttle.schedule-seconds[2]=0",
	"tinyhat.security.login.throttle.schedule-seconds[3]=0",
	"tinyhat.security.login.throttle.schedule-seconds[4]=0",
	"tinyhat.security.login.throttle.schedule-seconds[5]=0",
	"tinyhat.security.login.throttle.schedule-seconds[6]=0",
	"tinyhat.security.login.throttle.schedule-seconds[7]=0"
})
@AutoConfigureMockMvc
class PhaseThreeSecurityIntegrationTest {

	private static final String DB_PASSWORD = "phase3_test_password";
	private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.46")
		.withDatabaseName("hexora_phase3")
		.withUsername("hexora_test")
		.withPassword(DB_PASSWORD);

	static {
		MYSQL.start();
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Autowired
	private UserDetailsService userDetailsService;

	private String email;
	private UserDetails principal;

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

	@BeforeEach
	void createActiveAuditor() {
		email = "phase3-" + UUID.randomUUID() + "@example.test";
		String publicId = UUID.randomUUID().toString();
		jdbcTemplate.update(
			"INSERT INTO usuario " +
				"(public_id, cuenta_id, rol, estado, email_normalizado, password_hash) " +
				"VALUES (?, 1, 'AUDITOR', 'ACTIVO', ?, ?)",
			publicId,
			email,
			passwordEncoder.encode("phase3-test-password")
		);
		principal = User.withUsername(email)
			.password(passwordEncoder.encode("phase3-test-password"))
			.roles("AUDITOR")
			.build();
	}

	@Test
	void unauthenticatedWebRedirectsAndApiReturnsJson401() throws Exception {
		mockMvc.perform(get("/dashboard"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));

		mockMvc.perform(get("/api/network/status").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.error").value("No autenticado"))
			.andExpect(header().exists(CorrelationIdFilter.HEADER_NAME));
	}

	@Test
	void loginPageContainsCsrfMetadataAndOneGenericFailureMessage() throws Exception {
		MvcResult response = mockMvc.perform(get("/login?error"))
			.andExpect(status().isOk())
			.andReturn();
		String html = response.getResponse().getContentAsString();
		assertThat(html)
			.contains("name=\"_csrf\"")
			.contains("name=\"_csrf_header\"")
			.contains("Credenciales no válidas.")
			.doesNotContain("usuario no existe", "contraseña incorrecta");
	}

	@Test
	void loginFailuresHaveIdenticalResponsesAndAreAuditedWithCorrelationIds() throws Exception {
		MvcResult unknownUser = mockMvc.perform(post("/login")
				.with(csrf())
				.param("email", "missing-" + UUID.randomUUID() + "@example.test")
				.param("password", "incorrect"))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/login?error"))
			.andReturn();

		MvcResult wrongPassword = mockMvc.perform(post("/login")
				.with(csrf())
				.param("email", email)
				.param("password", "incorrect"))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/login?error"))
			.andReturn();

		assertThat(unknownUser.getResponse().getContentAsString())
			.isEqualTo(wrongPassword.getResponse().getContentAsString());
		for (MvcResult result : new MvcResult[] {unknownUser, wrongPassword}) {
			String correlationId = result.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME);
			assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM audit_event " +
					"WHERE evento = 'LOGIN_FAIL' AND correlation_id = ? AND ip IS NOT NULL",
				Integer.class,
				correlationId
			)).isEqualTo(1);
		}
	}

	@Test
	void successfulLoginAuditsAndLogoutRequiresPostAndCsrf() throws Exception {
		MvcResult login = mockMvc.perform(post("/login")
				.with(csrf())
				.param("email", email)
				.param("password", "phase3-test-password"))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/dashboard"))
			.andReturn();

		String correlationId = login.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM audit_event " +
				"WHERE evento = 'LOGIN_OK' AND correlation_id = ? AND usuario_id IS NOT NULL",
			Integer.class,
			correlationId
		)).isEqualTo(1);

		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
		assertThat(session).isNotNull();
		mockMvc.perform(get("/logout").session(session))
			.andExpect(status().isForbidden());
		assertThat(session.isInvalid()).isFalse();

		MvcResult logout = mockMvc.perform(post("/logout").session(session).with(csrf()))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/login?logout"))
			.andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("TINYHAT_SID=")))
			.andReturn();
		assertThat(session.isInvalid()).isTrue();
		String deletedCookie = logout.getResponse().getHeader("Set-Cookie");
		assertThat(deletedCookie).contains("TINYHAT_SID=");
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM audit_event " +
				"WHERE evento = 'LOGOUT' AND correlation_id = ? AND usuario_id IS NOT NULL",
			Integer.class,
			logout.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME)
		)).isEqualTo(1);
	}

	@Test
	void loginRotatesSessionIdAndStampsActivityTimes() throws Exception {
		MvcResult beforeLogin = mockMvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
		MockHttpSession preAuthenticationSession =
			(MockHttpSession) beforeLogin.getRequest().getSession(false);
		assertThat(preAuthenticationSession).isNotNull();
		String previousSessionId = preAuthenticationSession.getId();

		MvcResult login = mockMvc.perform(post("/login")
				.session(preAuthenticationSession)
				.with(csrf())
				.param("email", email)
				.param("password", "phase3-test-password"))
			.andExpect(status().isFound())
			.andReturn();

		MockHttpSession authenticatedSession = (MockHttpSession) login.getRequest().getSession(false);
		assertThat(authenticatedSession).isNotNull();
		assertThat(authenticatedSession.getId()).isNotEqualTo(previousSessionId);
		assertThat(authenticatedSession.getAttribute(SessionActivityFilter.CREATED_AT_ATTRIBUTE))
			.isInstanceOf(Number.class);
		assertThat(authenticatedSession.getAttribute(SessionActivityFilter.LAST_ACTIVITY_ATTRIBUTE))
			.isInstanceOf(Number.class);
	}

	@Test
	void secondLoginReplacesThePreviousSession() throws Exception {
		MockHttpSession firstSession = loginAndGetSession();
		MockHttpSession secondSession = loginAndGetSession();

		mockMvc.perform(get("/api/network/status").session(firstSession).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("SESSION_REPLACED"));

		mockMvc.perform(get("/api/network/status").session(secondSession).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk());
	}

	@Test
	void suspendedUserSessionIsRejectedOnItsNextRequest() throws Exception {
		MockHttpSession session = loginAndGetSession();
		jdbcTemplate.update("UPDATE usuario SET estado = 'SUSPENDIDO' WHERE email_normalizado = ?", email);

		mockMvc.perform(get("/api/network/status").session(session).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("SESSION_EXPIRED"));
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM audit_event "
				+ "WHERE evento = 'SESSION_EXPIRED' AND usuario_id = "
				+ "(SELECT id FROM usuario WHERE email_normalizado = ?)",
			Integer.class,
			email)).isEqualTo(1);
	}

	private MockHttpSession loginAndGetSession() throws Exception {
		MvcResult beforeLogin = mockMvc.perform(get("/login")).andReturn();
		MockHttpSession preAuthenticationSession =
			(MockHttpSession) beforeLogin.getRequest().getSession(false);
		MvcResult login = mockMvc.perform(post("/login")
				.session(preAuthenticationSession)
				.with(csrf())
				.param("email", email)
				.param("password", "phase3-test-password"))
			.andExpect(status().isFound())
			.andReturn();
		return (MockHttpSession) login.getRequest().getSession(false);
	}

	@Test
	void csrfProtectsWritesAndSecurityHeadersArePresent() throws Exception {
		mockMvc.perform(post("/api/cves/buscar").param("q", "CVE-2025-0001"))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

		mockMvc.perform(post("/api/cves/buscar")
				.with(csrf())
				.param("q", "CVE-2025-0001"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/dashboard"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("X-Frame-Options", "DENY"))
			.andExpect(header().string("Referrer-Policy", "no-referrer"))
			.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
	}

	@Test
	void unlistedRoutesAreDeniedAndEveryControllerRouteHasExplicitMethodSecurity() throws Exception {
		mockMvc.perform(get("/not-a-registered-route").with(user(principal)))
			.andExpect(status().isForbidden());

		handlerMapping.getHandlerMethods().forEach((mapping, handler) -> {
			Class<?> controller = ClassUtils.getUserClass(handler.getBeanType());
			if (!controller.getPackageName().startsWith("com.hexora")) {
				return;
			}
			if (AuthController.class.isAssignableFrom(controller)) {
				assertThat(mapping.getPatternValues())
					.as("public auth mapping %s", mapping)
					.allMatch(path -> path.equals("/") || path.equals("/login")
						|| path.equals("/recuperar-contrasena"));
				return;
			}
			assertThat(handler.getMethod().isAnnotationPresent(PreAuthorize.class)
				|| controller.isAnnotationPresent(PreAuthorize.class))
				.as("method security for %s", mapping)
				.isTrue();
		});
	}

	@Test
	void principalEqualityUsesUserIdAndSuspendedAccountsCannotAuthenticate() throws Exception {
		var first = userDetailsService.loadUserByUsername(email);
		var second = userDetailsService.loadUserByUsername(email.toUpperCase(java.util.Locale.ROOT));
		assertThat(first).isEqualTo(second);
		assertThat(first.hashCode()).isEqualTo(second.hashCode());

		jdbcTemplate.update(
			"INSERT INTO cuenta (public_id, nombre, tipo, estado, creado_en) " +
				"VALUES (?, 'Cuenta suspendida de prueba', 'AUDITOR', 'SUSPENDIDA', UTC_TIMESTAMP(6))",
			UUID.randomUUID().toString()
		);
		Long suspendedAccountId = jdbcTemplate.queryForObject(
			"SELECT id FROM cuenta WHERE nombre = 'Cuenta suspendida de prueba'",
			Long.class
		);
		String suspendedEmail = "suspended-" + UUID.randomUUID() + "@example.test";
		jdbcTemplate.update(
			"INSERT INTO usuario " +
				"(public_id, cuenta_id, rol, estado, email_normalizado, password_hash) " +
				"VALUES (?, ?, 'AUDITOR', 'ACTIVO', ?, ?)",
			UUID.randomUUID().toString(),
			suspendedAccountId,
			suspendedEmail,
			passwordEncoder.encode("phase3-test-password")
		);

		mockMvc.perform(post("/login")
				.with(csrf())
				.param("email", suspendedEmail)
				.param("password", "phase3-test-password"))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/login?error"));
	}
}

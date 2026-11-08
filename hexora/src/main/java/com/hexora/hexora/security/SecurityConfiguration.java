package com.hexora.hexora.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

	private final ObjectMapper objectMapper;
	private final String sessionCookieName;

	public SecurityConfiguration(
		ObjectMapper objectMapper,
		@Value("${server.servlet.session.cookie.name:JSESSIONID}") String sessionCookieName
	) {
		this.objectMapper = objectMapper;
		this.sessionCookieName = sessionCookieName;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new DelegatingPasswordEncoder(
			"bcrypt",
			Map.of("bcrypt", new BCryptPasswordEncoder(12))
		);
	}

	@Bean
	DaoAuthenticationProvider authenticationProvider(
		TinyHatUserDetailsService userDetailsService,
		PasswordEncoder passwordEncoder
	) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return provider;
	}

	@Bean
	SessionRegistry sessionRegistry() {
		return new SessionRegistryImpl();
	}

	@Bean
	HttpSessionEventPublisher httpSessionEventPublisher() {
		return new HttpSessionEventPublisher();
	}

	@Bean
	@Order(1)
	SecurityFilterChain apiSecurityFilterChain(
		HttpSecurity http,
		DaoAuthenticationProvider authenticationProvider,
		SessionRegistry sessionRegistry,
		TinyHatSecurityProperties securityProperties,
		ApiAuthenticationEntryPoint apiAuthenticationEntryPoint,
		ApiAccessDeniedHandler apiAccessDeniedHandler
	) throws Exception {
		http
			.securityMatcher("/api/**")
			.authenticationProvider(authenticationProvider)
			.requestCache(cache -> cache.requestCache(new NullRequestCache()))
			.sessionManagement(session -> {
				session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED);
				session.sessionFixation(fixation -> fixation.changeSessionId());
				session.maximumSessions(securityProperties.getSession().getMaxPerUser())
					.maxSessionsPreventsLogin(false)
					.sessionRegistry(sessionRegistry);
			})
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/api/agent/**").denyAll()
				.requestMatchers(
					"/api/cves/buscar",
					"/api/cves/*/detalle",
					"/api/network/scan",
					"/api/network/history",
					"/api/network/hosts",
					"/api/network/devices",
					"/api/network/hosts/*/ports",
					"/api/network/devices/*/ports",
					"/api/network/hosts/*/ports/*",
					"/api/network/devices/*/ports/*",
					"/api/network/hosts/*",
					"/api/network/devices/*",
					"/api/network/history/scans",
					"/api/network/devices/*/history",
					"/api/network/discover",
					"/api/network/detected-devices",
					"/api/network/gateway",
					"/api/network/discoveries/latest",
					"/api/network/discoveries/history",
					"/api/network/status"
				).hasRole("AUDITOR")
				.anyRequest().denyAll())
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(apiAuthenticationEntryPoint)
				.accessDeniedHandler(apiAccessDeniedHandler))
			.headers(this::configureHeaders);
		return http.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain webSecurityFilterChain(
		HttpSecurity http,
		DaoAuthenticationProvider authenticationProvider,
		SessionRegistry sessionRegistry,
		TinyHatSecurityProperties securityProperties,
		AuditService auditService,
		WebAccessDeniedHandler webAccessDeniedHandler
	) throws Exception {
		http
			.authenticationProvider(authenticationProvider)
			.sessionManagement(session -> {
				session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED);
				session.sessionFixation(fixation -> fixation.changeSessionId());
				session.maximumSessions(securityProperties.getSession().getMaxPerUser())
					.maxSessionsPreventsLogin(false)
					.sessionRegistry(sessionRegistry);
			})
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(
					"/",
					"/login",
					"/registro",
					"/recuperar/**",
					"/recuperar-contrasena",
					"/invitacion/**",
					"/error",
					"/css/**",
					"/js/**",
					"/img/**",
					"/imagen/**",
					"/favicon.ico"
				).permitAll()
				.requestMatchers(
					"/dashboard",
					"/inicio",
					"/proyectos",
					"/proyectos/",
					"/proyectos/nuproyectos",
					"/proyectos/guardar",
					"/proyectos/{id}",
					"/proyectos/{id}/editar",
					"/proyectos/{id}/actualizar",
					"/proyectos/{id}/eliminar",
					"/proyectos/{idProyecto}/areas/nueva",
					"/proyectos/{idProyecto}/departamentos/nuevo",
					"/proyectos/{idProyecto}/areas/guardar",
					"/proyectos/{idProyecto}/departamentos/guardar",
					"/areas/{idArea}",
					"/proyectos/{idProyecto}/departamentos/{idArea}",
					"/areas/{idArea}/editar",
					"/areas/{idArea}/actualizar",
					"/areas/{idArea}/eliminar",
					"/proyectos/{pid}/departamentos/{did}/vulnerabilidades/nueva",
					"/proyectos/{pid}/departamentos/{did}/vulnerabilidades/guardar",
					"/proyectos/{pid}/departamentos/{did}/vulnerabilidades/{vid}/editar",
					"/proyectos/{pid}/departamentos/{did}/vulnerabilidades/{vid}",
					"/proyectos/{pid}/departamentos/{did}/vulnerabilidades/{vid}/actualizar",
					"/proyectos/{pid}/departamentos/{did}/vulnerabilidades/eliminar/{vid}",
					"/network-monitoring",
					"/network-monitoring/history",
					"/ws/network"
				).hasRole("AUDITOR")
				.anyRequest().denyAll())
			.formLogin(form -> form
				.loginPage("/login")
				.loginProcessingUrl("/login")
				.usernameParameter("email")
				.passwordParameter("password")
				.failureUrl("/login?error")
				.successHandler(loginSuccessHandler(auditService))
				.failureHandler(loginFailureHandler(auditService)))
			.logout(logout -> logout
				.logoutUrl("/logout")
				.invalidateHttpSession(true)
				.clearAuthentication(true)
				.deleteCookies(sessionCookieName)
				.addLogoutHandler(logoutAuditHandler(auditService))
				.logoutSuccessUrl("/login?logout"))
			.exceptionHandling(exceptions -> exceptions
				.accessDeniedHandler(webAccessDeniedHandler))
			.headers(this::configureHeaders);
		return http.build();
	}

	private void configureHeaders(
		org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<HttpSecurity> headers
	) {
		headers
			.contentTypeOptions(Customizer.withDefaults())
			.frameOptions(frame -> frame.deny())
			.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
			.cacheControl(Customizer.withDefaults());
	}

	private AuthenticationSuccessHandler loginSuccessHandler(AuditService auditService) {
		return (request, response, authentication) -> {
			if (!(authentication.getPrincipal() instanceof TinyHatPrincipal principal)) {
				throw new IllegalStateException("Authenticated principal has an unsupported type");
			}
			auditService.loginSucceeded(principal, request);
			response.sendRedirect(request.getContextPath() + "/dashboard");
		};
	}

	private AuthenticationFailureHandler loginFailureHandler(AuditService auditService) {
		return (request, response, exception) -> {
			auditService.loginFailed(request.getParameter("email"), request);
			response.sendRedirect(request.getContextPath() + "/login?error");
		};
	}

	private LogoutHandler logoutAuditHandler(AuditService auditService) {
		return (request, response, authentication) -> auditService.logout(authentication, request);
	}

	@Bean
	ApiAuthenticationEntryPoint apiAuthenticationEntryPoint() {
		return new ApiAuthenticationEntryPoint(objectMapper);
	}

	@Bean
	ApiAccessDeniedHandler apiAccessDeniedHandler(AuditService auditService) {
		return new ApiAccessDeniedHandler(objectMapper, auditService);
	}

	@Bean
	WebAccessDeniedHandler webAccessDeniedHandler(AuditService auditService) {
		return new WebAccessDeniedHandler(auditService);
	}

	static void writeJsonError(
		ObjectMapper objectMapper,
		HttpServletRequest request,
		HttpServletResponse response,
		HttpStatus status,
		String message
	) throws IOException {
		Object correlationId = request.getAttribute(CorrelationIdFilter.class.getName() + ".id");
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(
			response.getOutputStream(),
			new SecurityErrorResponse(message, correlationId == null ? null : correlationId.toString())
		);
	}
}

final class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	ApiAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(
		HttpServletRequest request,
		HttpServletResponse response,
		org.springframework.security.core.AuthenticationException exception
	) throws IOException {
		SecurityConfiguration.writeJsonError(
			objectMapper, request, response, HttpStatus.UNAUTHORIZED, "No autenticado"
		);
	}
}

final class ApiAccessDeniedHandler implements AccessDeniedHandler {

	private final ObjectMapper objectMapper;
	private final AuditService auditService;

	ApiAccessDeniedHandler(ObjectMapper objectMapper, AuditService auditService) {
		this.objectMapper = objectMapper;
		this.auditService = auditService;
	}

	@Override
	public void handle(
		HttpServletRequest request,
		HttpServletResponse response,
		org.springframework.security.access.AccessDeniedException exception
	) throws IOException {
		auditService.accessDenied(request);
		SecurityConfiguration.writeJsonError(
			objectMapper, request, response, HttpStatus.FORBIDDEN, "Acceso denegado"
		);
	}
}

final class WebAccessDeniedHandler implements AccessDeniedHandler {

	private final AuditService auditService;

	WebAccessDeniedHandler(AuditService auditService) {
		this.auditService = auditService;
	}

	@Override
	public void handle(
		HttpServletRequest request,
		HttpServletResponse response,
		org.springframework.security.access.AccessDeniedException exception
	) throws IOException {
		auditService.accessDenied(request);
		response.sendError(HttpStatus.FORBIDDEN.value());
	}
}

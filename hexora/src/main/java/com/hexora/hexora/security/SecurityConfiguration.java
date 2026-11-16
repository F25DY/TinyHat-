package com.hexora.hexora.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
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
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

	private final ObjectMapper objectMapper;
	private final String sessionCookieName;
	private final String contentSecurityPolicy;
	private final boolean cspReportOnly;

	public SecurityConfiguration(
		ObjectMapper objectMapper,
		TinyHatSecurityProperties securityProperties,
		@Value("${server.servlet.session.cookie.name:JSESSIONID}") String sessionCookieName
	) {
		this.objectMapper = objectMapper;
		this.sessionCookieName = sessionCookieName;
		this.contentSecurityPolicy = ContentSecurityPolicy.directives(
			securityProperties.getWs().getAllowedOrigins());
		this.cspReportOnly = securityProperties.getCsp().isReportOnly();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new DelegatingPasswordEncoder(
			"bcrypt",
			Map.of("bcrypt", new BCryptPasswordEncoder(12))
		);
	}

	@Bean
	TenantContextFilter tenantContextFilter(org.springframework.beans.factory.ObjectProvider<EntityManager> entityManagerProvider) {
		return new TenantContextFilter(entityManagerProvider);
	}

	@Bean
	FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(
		TenantContextFilter tenantContextFilter
	) {
		FilterRegistrationBean<TenantContextFilter> registration = new FilterRegistrationBean<>(tenantContextFilter);
		registration.setEnabled(false);
		return registration;
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
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	Clock sessionClock() {
		return Clock.systemUTC();
	}

	@Bean
	SessionActivityFilter sessionActivityFilter(
		TinyHatSecurityProperties securityProperties,
		Clock sessionClock,
		com.hexora.hexora.repository.UsuarioRepository usuarioRepository,
		com.hexora.hexora.repository.CuentaRepository cuentaRepository,
		SessionRegistry sessionRegistry,
		SessionTerminationEventPublisher sessionTerminationEventPublisher,
		AuditService auditService
	) {
		return new SessionActivityFilter(
			securityProperties,
			sessionClock,
			usuarioRepository,
			cuentaRepository,
			sessionRegistry,
			sessionTerminationEventPublisher,
			auditService,
			objectMapper
		);
	}

	@Bean
	FilterRegistrationBean<SessionActivityFilter> sessionActivityFilterRegistration(
		SessionActivityFilter sessionActivityFilter
	) {
		FilterRegistrationBean<SessionActivityFilter> registration =
			new FilterRegistrationBean<>(sessionActivityFilter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	IpRateLimitFilter ipRateLimitFilter(
		TinyHatSecurityProperties properties,
		ClientIpResolver clientIpResolver,
		LoginAttemptService loginAttemptService,
		RateLimitService rateLimitService,
		RateLimitAuditGate auditGate,
		AuditService auditService
	) {
		return new IpRateLimitFilter(
			properties, clientIpResolver, loginAttemptService, rateLimitService, auditGate, auditService, objectMapper
		);
	}

	@Bean
	FilterRegistrationBean<IpRateLimitFilter> ipRateLimitFilterRegistration(IpRateLimitFilter filter) {
		FilterRegistrationBean<IpRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	UserRateLimitFilter userRateLimitFilter(
		TinyHatSecurityProperties properties,
		ClientIpResolver clientIpResolver,
		RateLimitService rateLimitService,
		RateLimitAuditGate auditGate,
		AuditService auditService
	) {
		return new UserRateLimitFilter(
			properties, clientIpResolver, rateLimitService, auditGate, auditService, objectMapper
		);
	}

	@Bean
	FilterRegistrationBean<UserRateLimitFilter> userRateLimitFilterRegistration(UserRateLimitFilter filter) {
		FilterRegistrationBean<UserRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	LoginAttemptFilter loginAttemptFilter(
		LoginAttemptService loginAttemptService,
		ClientIpResolver clientIpResolver,
		RateLimitAuditGate auditGate,
		AuditService auditService
	) {
		return new LoginAttemptFilter(loginAttemptService, clientIpResolver, auditGate, auditService);
	}

	@Bean
	FilterRegistrationBean<LoginAttemptFilter> loginAttemptFilterRegistration(LoginAttemptFilter filter) {
		FilterRegistrationBean<LoginAttemptFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	MfaGateFilter mfaGateFilter(TinyHatSecurityProperties properties, MfaService mfaService) {
		return new MfaGateFilter(properties, mfaService, objectMapper);
	}

	@Bean
	FilterRegistrationBean<MfaGateFilter> mfaGateFilterRegistration(MfaGateFilter filter) {
		FilterRegistrationBean<MfaGateFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	@Order(1)
	SecurityFilterChain apiSecurityFilterChain(
		HttpSecurity http,
		DaoAuthenticationProvider authenticationProvider,
		SessionRegistry sessionRegistry,
		TinyHatSecurityProperties securityProperties,
		ApiAuthenticationEntryPoint apiAuthenticationEntryPoint,
		ApiAccessDeniedHandler apiAccessDeniedHandler,
		AuditService auditService,
		TenantContextFilter tenantContextFilter,
		SessionActivityFilter sessionActivityFilter,
		IpRateLimitFilter ipRateLimitFilter,
		UserRateLimitFilter userRateLimitFilter,
		MfaGateFilter mfaGateFilter,
		SecurityContextRepository securityContextRepository
	) throws Exception {
		http
			.securityMatcher("/api/**")
			.authenticationProvider(authenticationProvider)
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			.requestCache(cache -> cache.requestCache(new NullRequestCache()))
			.sessionManagement(session -> {
				session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED);
				session.sessionFixation(fixation -> fixation.changeSessionId());
				session.maximumSessions(securityProperties.getSession().getMaxPerUser())
					.maxSessionsPreventsLogin(false)
					.expiredSessionStrategy(event -> {
						auditService.sessionReplaced(event.getSessionInformation().getPrincipal(), event.getRequest());
						writeJsonError(
							objectMapper,
							event.getRequest(),
							event.getResponse(),
							HttpStatus.UNAUTHORIZED,
							"SESSION_REPLACED"
						);
					})
					.sessionRegistry(sessionRegistry);
			})
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/api/agent/**").denyAll()
				.requestMatchers(
					HttpMethod.GET,
					"/api/proyectos/*/informes",
					"/api/informes/*",
					"/api/informes/*/contenido",
					"/api/informes/*/descarga"
				).hasAnyRole("AUDITOR", "CLIENTE_ADMIN")
				.requestMatchers(
					"/api/proyectos/*/informes",
					"/api/informes/*/publicar",
					"/api/informes/*/despublicar"
				).hasRole("AUDITOR")
				.requestMatchers(
					"/api/organizaciones/*/invitaciones",
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
					"/api/network/status",
					"/api/network/authorized-ranges",
					"/api/network/projects",
					"/api/network/projects/*/authorized-ranges",
					"/api/network/projects/*/continuous-monitoring"
				).hasRole("AUDITOR")
				.anyRequest().denyAll())
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(apiAuthenticationEntryPoint)
				.accessDeniedHandler(apiAccessDeniedHandler))
			.addFilterAfter(tenantContextFilter, org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
			.addFilterAfter(sessionActivityFilter, TenantContextFilter.class)
			.addFilterBefore(ipRateLimitFilter, org.springframework.security.web.context.SecurityContextHolderFilter.class)
			.addFilterAfter(mfaGateFilter, SessionActivityFilter.class)
			.addFilterAfter(userRateLimitFilter, MfaGateFilter.class)
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
		WebAccessDeniedHandler webAccessDeniedHandler,
		TenantContextFilter tenantContextFilter,
		SessionActivityFilter sessionActivityFilter,
		Clock sessionClock,
		IpRateLimitFilter ipRateLimitFilter,
		UserRateLimitFilter userRateLimitFilter,
		LoginAttemptFilter loginAttemptFilter,
		LoginAttemptService loginAttemptService,
		ClientIpResolver clientIpResolver,
		MfaGateFilter mfaGateFilter,
		SecurityContextRepository securityContextRepository
	) throws Exception {
		http
			.authenticationProvider(authenticationProvider)
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			.sessionManagement(session -> {
				session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED);
				session.sessionFixation(fixation -> fixation.changeSessionId());
				session.maximumSessions(securityProperties.getSession().getMaxPerUser())
					.maxSessionsPreventsLogin(false)
					.expiredSessionStrategy(event -> {
						auditService.sessionReplaced(event.getSessionInformation().getPrincipal(), event.getRequest());
						event.getResponse().sendRedirect(event.getRequest().getContextPath() + "/login?replaced");
					})
					.sessionRegistry(sessionRegistry);
			})
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(
					"/",
					"/login",
					"/registro",
					"/verificar/**",
					"/verificar-correo/**",
					"/recuperar",
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
					.requestMatchers("/mfa/**").authenticated()
					.requestMatchers(
					"/dashboard",
					"/inicio",
					"/network-monitoring",
					"/network-monitoring/history",
					"/ws/network"
				).hasRole("AUDITOR")
				.requestMatchers(
					"/proyectos",
					"/proyectos/",
					"/proyectos/{publicId}",
					"/areas/{areaPublicId}",
					"/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}"
				).hasAnyRole("AUDITOR", "CLIENTE_ADMIN")
				.requestMatchers("/proyectos/**", "/areas/**").hasRole("AUDITOR")
				.anyRequest().denyAll())
			.formLogin(form -> form
				.loginPage("/login")
				.loginProcessingUrl("/login")
				.usernameParameter("email")
				.passwordParameter("password")
				.failureUrl("/login?error")
				.successHandler(loginSuccessHandler(
					auditService,
					sessionClock,
					loginAttemptService,
					clientIpResolver,
					securityProperties,
					securityContextRepository
				))
				.failureHandler(loginFailureHandler(auditService, loginAttemptService, clientIpResolver)))
			.logout(logout -> logout
				.logoutUrl("/logout")
				.invalidateHttpSession(true)
				.clearAuthentication(true)
				.deleteCookies(sessionCookieName)
				.addLogoutHandler(logoutAuditHandler(auditService))
				.logoutSuccessUrl("/login?logout"))
			.exceptionHandling(exceptions -> exceptions
				.accessDeniedHandler(webAccessDeniedHandler))
			.addFilterAfter(tenantContextFilter, org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
			.addFilterAfter(sessionActivityFilter, TenantContextFilter.class)
			.addFilterBefore(ipRateLimitFilter, org.springframework.security.web.context.SecurityContextHolderFilter.class)
			.addFilterAfter(mfaGateFilter, SessionActivityFilter.class)
			.addFilterAfter(userRateLimitFilter, MfaGateFilter.class)
			.addFilterBefore(loginAttemptFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
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
			.cacheControl(Customizer.withDefaults())
			.httpStrictTransportSecurity(hsts -> hsts
				.maxAgeInSeconds(31_536_000)
				.includeSubDomains(false))
			.contentSecurityPolicy(csp -> {
				csp.policyDirectives(contentSecurityPolicy);
				if (cspReportOnly) {
					csp.reportOnly();
				}
			});
		headers.addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter(
			"Permissions-Policy",
			"camera=(), microphone=(), geolocation=(), payment=(), usb=()"
		));
		headers.addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter(
			"Cross-Origin-Opener-Policy",
			"same-origin"
		));
	}

	private AuthenticationSuccessHandler loginSuccessHandler(
		AuditService auditService,
		Clock clock,
		LoginAttemptService loginAttemptService,
		ClientIpResolver clientIpResolver,
		TinyHatSecurityProperties securityProperties,
		SecurityContextRepository securityContextRepository
	) {
		return (request, response, authentication) -> {
			if (!(authentication.getPrincipal() instanceof TinyHatPrincipal principal)) {
				throw new IllegalStateException("Authenticated principal has an unsupported type");
			}
			long now = clock.millis();
			jakarta.servlet.http.HttpSession session = request.getSession(false);
			if (session == null) {
				throw new IllegalStateException("Authenticated session was not created");
			}
			session.setAttribute(SessionActivityFilter.CREATED_AT_ATTRIBUTE, now);
			session.setAttribute(SessionActivityFilter.ORIGINAL_CREATED_AT_ATTRIBUTE, now);
			session.setAttribute(SessionActivityFilter.LAST_ACTIVITY_ATTRIBUTE, now);
			loginAttemptService.recordSuccess(request.getParameter("email"));
			if (securityProperties.getMfa().isRequired()) {
				org.springframework.security.core.context.SecurityContext context =
					org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
				context.setAuthentication(
					org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
						principal,
						null,
						java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
							"ROLE_PRE_MFA"
						))
					)
				);
				org.springframework.security.core.context.SecurityContextHolder.setContext(context);
				securityContextRepository.saveContext(context, request, response);
			}
			auditService.loginSucceeded(principal, request);
			response.sendRedirect(request.getContextPath() + "/dashboard");
		};
	}

	private AuthenticationFailureHandler loginFailureHandler(
		AuditService auditService,
		LoginAttemptService loginAttemptService,
		ClientIpResolver clientIpResolver
	) {
		return (request, response, exception) -> {
			String email = request.getParameter("email");
			loginAttemptService.recordFailure(email, clientIpResolver.resolve(request));
			auditService.loginFailed(email, request);
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

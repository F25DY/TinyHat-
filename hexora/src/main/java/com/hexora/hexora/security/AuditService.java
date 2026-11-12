package com.hexora.hexora.security;

import com.hexora.hexora.model.AuditEvent;
import com.hexora.hexora.model.Usuario;
import com.hexora.hexora.repository.AuditEventRepository;
import com.hexora.hexora.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

	private static final long FALLBACK_ACCOUNT_ID = 1L;
	private final AuditEventRepository auditEventRepository;
	private final UsuarioRepository usuarioRepository;
	private final EnumerationDetector enumerationDetector;

	public AuditService(
		AuditEventRepository auditEventRepository,
		UsuarioRepository usuarioRepository,
		EnumerationDetector enumerationDetector
	) {
		this.auditEventRepository = auditEventRepository;
		this.usuarioRepository = usuarioRepository;
		this.enumerationDetector = enumerationDetector;
	}

	@Transactional
	public void loginSucceeded(TinyHatPrincipal principal, HttpServletRequest request) {
		record("LOGIN_OK", "OK", principal, request, Map.of());
	}

	@Transactional
	public void loginFailed(String submittedEmail, HttpServletRequest request) {
		String email = submittedEmail == null ? "" : submittedEmail.trim().toLowerCase(Locale.ROOT);
		Usuario knownUser = usuarioRepository.findByEmailNormalizado(email).orElse(null);
		record(
			"LOGIN_FAIL",
			"DENEGADO",
			knownUser == null ? null : knownPrincipal(knownUser),
			request,
			Map.of()
		);
	}

	@Transactional
	public void logout(Authentication authentication, HttpServletRequest request) {
		record("LOGOUT", "OK", principal(authentication), request, Map.of());
	}

	@Transactional
	public void sessionExpired(TinyHatPrincipal principal, HttpServletRequest request, String reason) {
		record("SESSION_EXPIRED", "EXPIRED", principal, request, Map.of("reason", reason));
	}

	@Transactional
	public void accessDenied(HttpServletRequest request) {
		record("ACCESS_DENIED", "DENEGADO", principal(request), request, Map.of());
	}

	@Transactional
	public void mfaReset(TinyHatPrincipal principal, HttpServletRequest request) {
		record("MFA_RESET", "OK", principal, request, Map.of());
	}

	@Transactional
	public void networkEvent(
		String eventName,
		String result,
		TinyHatPrincipal principal,
		HttpServletRequest request,
		String projectPublicId,
		String cidr,
		String reason
	) {
		AuditEvent event = new AuditEvent();
		event.setEvento(eventName);
		event.setResultado(result);
		event.setCuentaId(principal.getCuentaId());
		event.setUsuarioId(principal.getUserId());
		event.setOrganizacionId(principal.getOrganizacionId());
		event.setIp(request.getRemoteAddr());
		event.setUserAgent(limit(request.getHeader("User-Agent"), 512));
		event.setRecursoTipo("NETWORK_SCOPE");
		event.setRecursoPublicId(projectPublicId);
		event.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
		event.setDetalle(Map.of(
			"projectPublicId", projectPublicId,
			"cidr", cidr == null ? "" : cidr,
			"reason", reason == null ? "" : limit(reason, 160)
		));
		auditEventRepository.save(event);
	}

	@Transactional
	public void networkSchedulerEvent(
		long accountId,
		String eventName,
		String result,
		Map<String, Object> details
	) {
		if (accountId < 1) {
			throw new IllegalArgumentException("A valid account is required for scheduler audit events");
		}
		AuditEvent event = new AuditEvent();
		event.setEvento(eventName);
		event.setResultado(result);
		event.setCuentaId(accountId);
		event.setIp("127.0.0.1");
		event.setUserAgent("HEXORA_NETWORK_SCHEDULER");
		event.setRecursoTipo("NETWORK_SCHEDULER");
		event.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
		event.setDetalle(details);
		auditEventRepository.save(event);
	}

	@Transactional
	public void rateLimited(String category, String subject, String clientIp, HttpServletRequest request) {
		TinyHatPrincipal principal = principal(request);
		AuditEvent event = new AuditEvent();
		event.setEvento("RATE_LIMITED");
		event.setResultado("DENEGADO");
		event.setCuentaId(principal == null ? FALLBACK_ACCOUNT_ID : principal.getCuentaId());
		event.setUsuarioId(principal == null ? null : principal.getUserId());
		event.setOrganizacionId(principal == null ? null : principal.getOrganizacionId());
		event.setIp(clientIp);
		event.setUserAgent(limit(request.getHeader("User-Agent"), 512));
		event.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
		event.setDetalle(Map.of("category", category, "subject", subject));
		auditEventRepository.save(event);
	}

	@Transactional
	public void scopedNotFound(TinyHatPrincipal principal, HttpServletRequest request) {
		AuditEvent event = new AuditEvent();
		event.setEvento("NOT_FOUND_SCOPED");
		event.setResultado("NOT_FOUND");
		event.setCuentaId(principal.getCuentaId());
		event.setUsuarioId(principal.getUserId());
		event.setOrganizacionId(principal.getOrganizacionId());
		event.setIp(request.getRemoteAddr());
		event.setUserAgent(limit(request.getHeader("User-Agent"), 512));
		event.setRecursoTipo("ROUTE");
		event.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
		event.setDetalle(Map.of("path", limit(request.getRequestURI(), 512)));
		auditEventRepository.save(event);
		enumerationDetector.recordScopedNotFound(principal.getUserId(), principal.getCuentaId());
	}

	@Transactional
	public void userCreated(Usuario usuario, HttpServletRequest request) {
		record("USER_CREATED", "OK", knownPrincipal(usuario), request, Map.of("email", usuario.getEmailNormalizado()));
	}

	@Transactional
	public void userVerified(Usuario usuario, HttpServletRequest request) {
		record("USER_VERIFIED", "OK", knownPrincipal(usuario), request, Map.of("email", usuario.getEmailNormalizado()));
	}

	@Transactional
	public void userInvited(Usuario usuario, TinyHatPrincipal auditor, HttpServletRequest request) {
		record("USER_INVITED", "OK", auditor, request, Map.of(
			"invitedUserId", usuario.getId(),
			"email", usuario.getEmailNormalizado(),
			"role", usuario.getRol()
		));
	}

	@Transactional
	public void passwordReset(Usuario usuario, HttpServletRequest request) {
		record("PASSWORD_RESET", "OK", knownPrincipal(usuario), request, Map.of("email", usuario.getEmailNormalizado()));
	}

	@Transactional
	public void opsCommand(String command, String targetPublicId, String outcome) {
		AuditEvent event = new AuditEvent();
		event.setEvento("OPS_COMMAND");
		event.setResultado(outcome);
		event.setCuentaId(FALLBACK_ACCOUNT_ID);
		event.setIp("127.0.0.1");
		event.setUserAgent("OPS_CLI");
		event.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
		event.setDetalle(Map.of("command", command, "target", targetPublicId != null ? targetPublicId : ""));
		auditEventRepository.save(event);
	}


	// --- Phase 12: events that complete the plan catalogue -------------------------------------

	@Transactional
	public void mfaSucceeded(TinyHatPrincipal principal, HttpServletRequest request, String context) {
		record("MFA_OK", "OK", principal, request, Map.of("context", context));
	}

	@Transactional
	public void mfaFailed(TinyHatPrincipal principal, HttpServletRequest request, String context, int failures) {
		record("MFA_FAIL", "DENEGADO", principal, request, Map.of("context", context, "failures", failures));
	}

	@Transactional
	public void mfaEnrolled(TinyHatPrincipal principal, HttpServletRequest request) {
		record("MFA_ENROLLED", "OK", principal, request, Map.of());
	}

	@Transactional
	public void passwordChanged(Usuario usuario, HttpServletRequest request, String reason) {
		record("PASSWORD_CHANGED", "OK", knownPrincipal(usuario), request, Map.of("reason", reason));
	}

	@Transactional
	public void sessionReplaced(Object principalObject, HttpServletRequest request) {
		TinyHatPrincipal principal = principalObject instanceof TinyHatPrincipal known ? known : null;
		record("SESSION_REPLACED", "EXPIRED", principal, request, Map.of());
	}

	/** Temporary login lockout (distinct from the token-bucket RATE_LIMITED event). */
	@Transactional
	public void throttled(String clientIp, long retryAfterSeconds, HttpServletRequest request) {
		AuditEvent event = baseEvent("THROTTLED", "DENEGADO", null, request);
		event.setIp(clientIp);
		event.setDetalle(Map.of("subject", clientIp, "retryAfterSeconds", retryAfterSeconds));
		auditEventRepository.save(event);
	}

	/** A suspended user was detected on a live session. */
	@Transactional
	public void userSuspendedDetected(TinyHatPrincipal principal, HttpServletRequest request) {
		record("USER_SUSPENDED", "OK", principal, request, Map.of("source", "SESSION_CHECK"));
	}

	/** A role (or organization) change was detected on a live session. */
	@Transactional
	public void roleChangedDetected(
		TinyHatPrincipal principal, String newRole, HttpServletRequest request
	) {
		record("ROLE_CHANGED", "OK", principal, request, Map.of(
			"source", "SESSION_CHECK",
			"previousRole", principal.getRol(),
			"newRole", newRole == null ? "" : newRole
		));
	}

	/** Owner command: user suspended or reactivated (no HTTP request, no correlation id). */
	@Transactional
	public void userSuspendedByOps(Usuario usuario, boolean suspended) {
		AuditEvent event = systemEvent(
			suspended ? "USER_SUSPENDED" : "USER_REACTIVATED", "OK", usuario.getCuentaId(), usuario.getId(),
			usuario.getOrganizacionId());
		event.setRecursoTipo("USUARIO");
		event.setRecursoPublicId(usuario.getPublicId());
		event.setDetalle(Map.of("source", "OPS_CLI", "suspended", suspended));
		auditEventRepository.save(event);
	}

	@Transactional
	public void capabilityChanged(long accountId, String capability, boolean enabled) {
		AuditEvent event = systemEvent("CAPABILITY_CHANGED", "OK", accountId, null, null);
		event.setRecursoTipo("CAPACIDAD");
		event.setDetalle(Map.of("capability", capability, "enabled", enabled, "source", "OPS_CLI"));
		auditEventRepository.save(event);
	}

	@Transactional
	public void reportEvent(
		AuditEventType type,
		TinyHatPrincipal principal,
		HttpServletRequest request,
		String reportPublicId,
		Map<String, Object> details
	) {
		AuditEvent event = baseEvent(type.name(), "OK", principal, request);
		event.setRecursoTipo("INFORME");
		event.setRecursoPublicId(reportPublicId);
		event.setDetalle(details);
		auditEventRepository.save(event);
	}

	/** Effective, secret-free configuration written once per start (Phase 12, STARTUP_CONFIG). */
	@Transactional
	public void startupConfig(Map<String, Object> effectiveConfiguration) {
		AuditEvent event = systemEvent("STARTUP_CONFIG", "OK", FALLBACK_ACCOUNT_ID, null, null);
		event.setRecursoTipo("SISTEMA");
		event.setDetalle(effectiveConfiguration);
		auditEventRepository.save(event);
	}

	private AuditEvent baseEvent(
		String eventName, String result, TinyHatPrincipal principal, HttpServletRequest request
	) {
		AuditEvent event = new AuditEvent();
		event.setEvento(eventName);
		event.setResultado(result);
		event.setCuentaId(principal == null ? FALLBACK_ACCOUNT_ID : principal.getCuentaId());
		event.setUsuarioId(principal == null ? null : principal.getUserId());
		event.setOrganizacionId(principal == null ? null : principal.getOrganizacionId());
		event.setIp(request.getRemoteAddr());
		event.setUserAgent(limit(request.getHeader("User-Agent"), 512));
		event.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
		return event;
	}

	private AuditEvent systemEvent(
		String eventName, String result, long accountId, Long userId, Long organizationId
	) {
		AuditEvent event = new AuditEvent();
		event.setEvento(eventName);
		event.setResultado(result);
		event.setCuentaId(accountId);
		event.setUsuarioId(userId);
		event.setOrganizacionId(organizationId);
		event.setIp("127.0.0.1");
		event.setUserAgent("TINYHAT_SYSTEM");
		event.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
		return event;
	}

	private void record(
		String eventName,
		String result,
		TinyHatPrincipal principal,
		HttpServletRequest request,
		Map<String, Object> details
	) {
		AuditEvent event = new AuditEvent();
		event.setEvento(eventName);
		event.setResultado(result);
		event.setCuentaId(principal == null ? FALLBACK_ACCOUNT_ID : principal.getCuentaId());
		event.setUsuarioId(principal == null ? null : principal.getUserId());
		event.setOrganizacionId(principal == null ? null : principal.getOrganizacionId());
		event.setIp(request.getRemoteAddr());
		event.setUserAgent(limit(request.getHeader("User-Agent"), 512));
		event.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
		event.setDetalle(details);
		auditEventRepository.save(event);
	}

	private TinyHatPrincipal principal(Authentication authentication) {
		return authentication != null && authentication.getPrincipal() instanceof TinyHatPrincipal principal
			? principal
			: null;
	}

	private TinyHatPrincipal principal(HttpServletRequest request) {
		return principal(org.springframework.security.core.context.SecurityContextHolder
			.getContext().getAuthentication());
	}

	private TinyHatPrincipal knownPrincipal(Usuario usuario) {
		return new TinyHatPrincipal(usuario, false);
	}

	private String limit(String value, int maxLength) {
		return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
	}
}

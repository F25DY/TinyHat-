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

	public AuditService(
		AuditEventRepository auditEventRepository,
		UsuarioRepository usuarioRepository
	) {
		this.auditEventRepository = auditEventRepository;
		this.usuarioRepository = usuarioRepository;
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

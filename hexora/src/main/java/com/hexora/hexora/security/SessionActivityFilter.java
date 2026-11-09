package com.hexora.hexora.security;

import com.hexora.hexora.model.Cuenta;
import com.hexora.hexora.model.Usuario;
import com.hexora.hexora.repository.CuentaRepository;
import com.hexora.hexora.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.AntPathMatcher;

public final class SessionActivityFilter extends OncePerRequestFilter {

    public static final String CREATED_AT_ATTRIBUTE = SessionActivityFilter.class.getName() + ".createdAt";
    public static final String LAST_ACTIVITY_ATTRIBUTE = SessionActivityFilter.class.getName() + ".lastActivity";

    private final TinyHatSecurityProperties properties;
    private final Clock clock;
    private final UsuarioRepository usuarioRepository;
    private final CuentaRepository cuentaRepository;
    private final SessionRegistry sessionRegistry;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public SessionActivityFilter(
            TinyHatSecurityProperties properties,
            Clock clock,
            UsuarioRepository usuarioRepository,
            CuentaRepository cuentaRepository,
            SessionRegistry sessionRegistry,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.clock = clock;
        this.usuarioRepository = usuarioRepository;
        this.cuentaRepository = cuentaRepository;
        this.sessionRegistry = sessionRegistry;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof TinyHatPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = clock.millis();
        Long createdAt = timestamp(session.getAttribute(CREATED_AT_ATTRIBUTE));
        Long lastActivity = timestamp(session.getAttribute(LAST_ACTIVITY_ATTRIBUTE));
        String expiredReason = expirationReason(principal, createdAt, lastActivity, now);
        if (expiredReason != null) {
            expireSession(request, response, session, principal, expiredReason);
            return;
        }

        if (!isPollRequest(request)) {
            session.setAttribute(LAST_ACTIVITY_ATTRIBUTE, now);
        }
        filterChain.doFilter(request, response);
    }

    private String expirationReason(
            TinyHatPrincipal principal,
            Long createdAt,
            Long lastActivity,
            long now) {
        if (createdAt == null || lastActivity == null || createdAt > now || lastActivity > now) {
            return "INVALID_SESSION_METADATA";
        }

        long idleMillis = properties.getSession().getIdleMinutes() * 60_000L;
        if (now - lastActivity >= idleMillis) {
            return "IDLE_TIMEOUT";
        }

        long absoluteMillis = properties.getSession().getAbsoluteMinutes() * 60_000L;
        if (now - createdAt >= absoluteMillis) {
            return "ABSOLUTE_TIMEOUT";
        }

        Optional<Usuario> user = usuarioRepository.findByIdAndCuentaId(
            principal.getUserId(), principal.getCuentaId());
        Optional<Cuenta> account = cuentaRepository.findById(principal.getCuentaId());
        if (user.isEmpty() || account.isEmpty()
                || !"ACTIVO".equals(user.get().getEstado())
                || !"ACTIVA".equals(account.get().getEstado())
                || !principal.getRol().equals(user.get().getRol())
                || !Objects.equals(principal.getOrganizacionId(), user.get().getOrganizacionId())) {
            return "PRINCIPAL_REVOKED";
        }

        return null;
    }

    private void expireSession(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpSession session,
            TinyHatPrincipal principal,
            String reason) throws IOException {
        auditService.sessionExpired(principal, request, reason);
        sessionRegistry.getAllSessions(principal, false).forEach(information -> information.expireNow());
        session.invalidate();
        SecurityContextHolder.clearContext();

        if (isApiRequest(request) || isPollRequest(request)) {
            SecurityConfiguration.writeJsonError(
                objectMapper,
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "SESSION_EXPIRED"
            );
            return;
        }
        response.sendRedirect(request.getContextPath() + "/login?expired");
    }

    private boolean isPollRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return properties.getSession().getPollPaths().stream()
            .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/");
    }

    private Long timestamp(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}

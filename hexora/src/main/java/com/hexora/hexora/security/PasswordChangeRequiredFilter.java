package com.hexora.hexora.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Mientras un usuario deba cambiar su contraseña (la temporal que le entregó el auditor), lo único que puede hacer es
 * cambiarla o cerrar sesión: cualquier otra ruta lo lleva a la pantalla de cambio. Va después del control de MFA.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    public static final String RUTA_CAMBIO = "/cliente/contrasena";

    private static final String[] PERMITIDAS = {
        RUTA_CAMBIO, "/logout", "/error", "/css/**", "/js/**", "/img/**", "/imagen/**", "/vendor/**", "/favicon.ico", "/mfa/**"
    };
    private final AntPathMatcher comparador = new AntPathMatcher();
    private final RedirectStrategy redireccion = new DefaultRedirectStrategy();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof TinyHatPrincipal principal
            && principal.isDebeCambiarPassword() && !permitida(request)) {
            redireccion.sendRedirect(request, response, RUTA_CAMBIO);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean permitida(HttpServletRequest request) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        for (String patron : PERMITIDAS) {
            if (comparador.match(patron, ruta)) {
                return true;
            }
        }
        return false;
    }
}

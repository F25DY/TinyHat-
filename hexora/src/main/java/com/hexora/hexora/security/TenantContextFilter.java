package com.hexora.hexora.security;

import jakarta.persistence.EntityManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.hibernate.Session;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class TenantContextFilter extends OncePerRequestFilter {

    private final ObjectProvider<EntityManager> entityManagerProvider;

    public TenantContextFilter(ObjectProvider<EntityManager> entityManagerProvider) {
        this.entityManagerProvider = entityManagerProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof TinyHatPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            TenantContext.runAs(principal.getCuentaId(), () -> {
                Session session = entityManagerProvider.getObject().unwrap(Session.class);
                session.enableFilter("accountFilter").setParameter("tenantId", principal.getCuentaId());
                try {
                    filterChain.doFilter(request, response);
                } catch (IOException | ServletException exception) {
                    throw new TenantFilterException(exception);
                } finally {
                    session.disableFilter("accountFilter");
                }
            });
        } catch (TenantFilterException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            if (exception.getCause() instanceof ServletException servletException) {
                throw servletException;
            }
            throw exception;
        }
    }

    private static final class TenantFilterException extends RuntimeException {
        private TenantFilterException(Exception cause) {
            super(cause);
        }
    }
}

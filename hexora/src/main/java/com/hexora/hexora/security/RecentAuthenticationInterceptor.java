package com.hexora.hexora.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

@Component
public class RecentAuthenticationInterceptor implements HandlerInterceptor {

    private final MfaAuthenticationService authenticationService;
    private final ObjectMapper objectMapper;

    public RecentAuthenticationInterceptor(
        MfaAuthenticationService authenticationService,
        ObjectMapper objectMapper
    ) {
        this.authenticationService = authenticationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler
    ) throws IOException {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireRecentAuth requirement = requirement(handlerMethod);
        if (requirement == null) {
            return true;
        }
        var session = request.getSession(false);
        if (session != null && authenticationService.hasRecentAuthentication(
            session,
            requirement == null ? 5 : requirement.maxAgeMinutes()
        )) {
            return true;
        }

        if (session != null) {
            session.setAttribute(MfaAuthenticationService.REAUTH_TARGET_ATTRIBUTE, request.getRequestURI());
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/")) {
            SecurityConfiguration.writeJsonError(
                objectMapper, request, response, HttpStatus.UNAUTHORIZED, "REAUTH_REQUIRED"
            );
        } else {
            response.sendRedirect(request.getContextPath() + "/mfa/reauth?required");
        }
        return false;
    }

    private RequireRecentAuth requirement(HandlerMethod handlerMethod) {
        RequireRecentAuth methodRequirement = handlerMethod.getMethodAnnotation(RequireRecentAuth.class);
        return methodRequirement != null
            ? methodRequirement
            : handlerMethod.getBeanType().getAnnotation(RequireRecentAuth.class);
    }
}

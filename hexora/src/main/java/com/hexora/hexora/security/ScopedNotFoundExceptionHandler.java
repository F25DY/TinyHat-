package com.hexora.hexora.security;

import com.hexora.hexora.security.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Debe evaluarse antes que ApiExceptionHandler (Exception.class), que de lo contrario convierte el 404 en 500.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScopedNotFoundExceptionHandler {

    private final AuditService auditService;

    public ScopedNotFoundExceptionHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    @ExceptionHandler(ScopedNotFoundException.class)
    public ResponseEntity<SecurityErrorResponse> notFound(
            ScopedNotFoundException exception,
            HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TinyHatPrincipal principal) {
            auditService.scopedNotFound(principal, request);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new SecurityErrorResponse("NOT_FOUND", MDC.get(CorrelationIdFilter.MDC_KEY)));
    }
}

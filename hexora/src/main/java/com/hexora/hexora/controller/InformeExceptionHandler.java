package com.hexora.hexora.controller;

import com.hexora.hexora.informes.servicio.ErroresInforme;
import com.hexora.hexora.informes.validacion.InformeValidacionException;
import com.hexora.hexora.security.CorrelationIdFilter;
import com.hexora.hexora.security.RateLimitExceededException;
import com.hexora.hexora.security.SecurityErrorResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores de las vistas de informes: siempre genéricos y con el correlationId, sin rutas, hashes ni causas internas.
 * Un recurso ajeno o no visible lo resuelve {@code ScopedNotFoundExceptionHandler} (404).
 */
@RestControllerAdvice(assignableTypes = InformeWebController.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class InformeExceptionHandler {

    @ExceptionHandler(ErroresInforme.Ocupado.class)
    public ResponseEntity<SecurityErrorResponse> ocupado() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "5")
            .body(respuesta("BUSY"));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<SecurityErrorResponse> limite(RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", Long.toString(e.getRetryAfterSeconds())).body(respuesta("RATE_LIMITED"));
    }

    @ExceptionHandler({ErroresInforme.Integridad.class, ErroresInforme.Publicacion.class})
    public ResponseEntity<SecurityErrorResponse> interno() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(respuesta("INTERNAL_ERROR"));
    }

    @ExceptionHandler({ErroresInforme.Conflicto.class, InformeValidacionException.class})
    public ResponseEntity<SecurityErrorResponse> conflicto() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(respuesta("CONFLICT"));
    }

    private static SecurityErrorResponse respuesta(String codigo) {
        return new SecurityErrorResponse(codigo, MDC.get(CorrelationIdFilter.MDC_KEY));
    }
}

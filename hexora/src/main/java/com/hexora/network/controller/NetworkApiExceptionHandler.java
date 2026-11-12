package com.hexora.network.controller;

import jakarta.persistence.EntityNotFoundException;
import com.hexora.network.service.ScanRateLimitException;
import com.hexora.network.service.NetworkDiscoveryException;
import com.hexora.network.service.NetworkCapabilityDisabledException;
import com.hexora.network.service.NetworkScopeRejectedException;
import com.hexora.hexora.security.CorrelationIdFilter;
import com.hexora.hexora.security.SecurityErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class NetworkApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkApiExceptionHandler.class);

    @ExceptionHandler(NetworkScopeRejectedException.class)
    public ResponseEntity<SecurityErrorResponse> scopeRejected(
        NetworkScopeRejectedException exception,
        HttpServletRequest request
    ) {
        LOGGER.warn("Network endpoint {} rejected operation; correlationId={}",
            request.getRequestURI(), MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(error("SCOPE_REJECTED"));
    }

    @ExceptionHandler(NetworkCapabilityDisabledException.class)
    public ResponseEntity<SecurityErrorResponse> capabilityDisabled(
        NetworkCapabilityDisabledException exception,
        HttpServletRequest request
    ) {
        LOGGER.warn("Network endpoint {} denied; correlationId={}",
            request.getRequestURI(), MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("FEATURE_DISABLED"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<SecurityErrorResponse> invalidRequest(
        IllegalArgumentException exception,
        HttpServletRequest request
    ) {
        LOGGER.warn("Network endpoint {} rejected {}; correlationId={}",
            request.getRequestURI(), request.getMethod(), MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.badRequest().body(error("VALIDATION_ERROR"));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<SecurityErrorResponse> hostNotFound(
        EntityNotFoundException exception,
        HttpServletRequest request
    ) {
        LOGGER.warn("Network endpoint {} could not find a resource; correlationId={}",
            request.getRequestURI(), MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND"));
    }

    @ExceptionHandler(ScanRateLimitException.class)
    public ResponseEntity<SecurityErrorResponse> scanRateLimited(
        ScanRateLimitException exception,
        HttpServletRequest request
    ) {
        LOGGER.warn("Network endpoint {} rate limited; correlationId={}",
            request.getRequestURI(), MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(error("RATE_LIMITED"));
    }

    @ExceptionHandler(NetworkDiscoveryException.class)
    public ResponseEntity<SecurityErrorResponse> discoveryUnavailable(
        NetworkDiscoveryException exception,
        HttpServletRequest request
    ) {
        LOGGER.error("Network discovery failed; correlationId={}",
            MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error("NETWORK_UNAVAILABLE"));
    }

    private static SecurityErrorResponse error(String code) {
        return new SecurityErrorResponse(code, MDC.get(CorrelationIdFilter.MDC_KEY));
    }

}

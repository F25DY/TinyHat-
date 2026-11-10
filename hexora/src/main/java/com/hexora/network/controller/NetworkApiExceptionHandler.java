package com.hexora.network.controller;

import jakarta.persistence.EntityNotFoundException;
import com.hexora.network.service.ScanRateLimitException;
import com.hexora.network.service.NetworkDiscoveryException;
import com.hexora.network.service.NetworkCapabilityDisabledException;
import com.hexora.network.service.NetworkScopeRejectedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class NetworkApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkApiExceptionHandler.class);

    @ExceptionHandler(NetworkScopeRejectedException.class)
    public ResponseEntity<Map<String, String>> scopeRejected(
        NetworkScopeRejectedException exception,
        HttpServletRequest request
    ) {
        LOGGER.warn("Network endpoint {} rejected operation: {}",
            request.getRequestURI(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(NetworkCapabilityDisabledException.class)
    public ResponseEntity<Map<String, String>> capabilityDisabled(
        NetworkCapabilityDisabledException exception,
        HttpServletRequest request
    ) {
        LOGGER.warn("Network endpoint {} denied: {}", request.getRequestURI(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidRequest(
        IllegalArgumentException exception,
        HttpServletRequest request
    ) {
        LOGGER.error("Network endpoint {} rejected {}: {}",
            request.getRequestURI(), request.getMethod(), exception.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> hostNotFound(
        EntityNotFoundException exception,
        HttpServletRequest request
    ) {
        LOGGER.error("Network endpoint {} failed for {}: {}",
            request.getRequestURI(), request.getMethod(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(ScanRateLimitException.class)
    public ResponseEntity<Map<String, String>> scanRateLimited(
        ScanRateLimitException exception,
        HttpServletRequest request
    ) {
        LOGGER.error("Network endpoint {} rate-limited {}: {}",
            request.getRequestURI(), request.getMethod(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(NetworkDiscoveryException.class)
    public ResponseEntity<Map<String, String>> discoveryUnavailable(
        NetworkDiscoveryException exception,
        HttpServletRequest request
    ) {
        LOGGER.error("Network endpoint {} could not discover the local network: {}",
            request.getRequestURI(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", exception.getMessage()));
    }

}

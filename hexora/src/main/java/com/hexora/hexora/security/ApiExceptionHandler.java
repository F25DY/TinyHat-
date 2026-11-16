package com.hexora.hexora.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({
        HandlerMethodValidationException.class,
        MethodArgumentNotValidException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        IllegalArgumentException.class
    })
    public ResponseEntity<SecurityErrorResponse> validationError(Exception exception) {
        return ResponseEntity.badRequest().body(
            new SecurityErrorResponse("VALIDATION_ERROR", MDC.get(CorrelationIdFilter.MDC_KEY)));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<SecurityErrorResponse> methodNotAllowed(
        org.springframework.web.HttpRequestMethodNotSupportedException exception
    ) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        java.util.Set<org.springframework.http.HttpMethod> supported = exception.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            response.allow(supported.toArray(new org.springframework.http.HttpMethod[0]));
        }
        return response.body(new SecurityErrorResponse("METHOD_NOT_ALLOWED", MDC.get(CorrelationIdFilter.MDC_KEY)));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<SecurityErrorResponse> noResource() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            new SecurityErrorResponse("NOT_FOUND", MDC.get(CorrelationIdFilter.MDC_KEY)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<SecurityErrorResponse> accessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            new SecurityErrorResponse("ACCESS_DENIED", MDC.get(CorrelationIdFilter.MDC_KEY)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<SecurityErrorResponse> requestError(
        Exception exception,
        HttpServletRequest request
    ) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        LOGGER.error(
            "Request failed; correlationId={}, exceptionType={}",
            correlationId,
            exception.getClass().getName()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            new SecurityErrorResponse("REQUEST_ERROR", correlationId));
    }
}

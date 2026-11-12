package com.hexora.hexora.report;

import com.hexora.hexora.security.CorrelationIdFilter;
import com.hexora.hexora.security.SecurityErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Generic, secret-free error bodies for the report API; details stay in the log. */
@RestControllerAdvice(assignableTypes = ReportController.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ReportApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportApiExceptionHandler.class);

    @ExceptionHandler(ReportCapabilityDisabledException.class)
    public ResponseEntity<SecurityErrorResponse> capabilityDisabled() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("FEATURE_DISABLED"));
    }

    @ExceptionHandler(ReportRendererUnavailableException.class)
    public ResponseEntity<SecurityErrorResponse> rendererUnavailable() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(error("REPORT_RENDERER_UNAVAILABLE"));
    }

    @ExceptionHandler(ReportStateException.class)
    public ResponseEntity<SecurityErrorResponse> invalidState() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("INVALID_STATE"));
    }

    @ExceptionHandler(ReportIntegrityException.class)
    public ResponseEntity<SecurityErrorResponse> integrity(ReportIntegrityException exception) {
        LOGGER.error("Report integrity check failed; correlationId={}", MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("REPORT_INTEGRITY_ERROR"));
    }

    private static SecurityErrorResponse error(String code) {
        return new SecurityErrorResponse(code, MDC.get(CorrelationIdFilter.MDC_KEY));
    }
}

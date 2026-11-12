package com.hexora.hexora.report;

/** The stored snapshot no longer matches its SHA-256: the row was altered outside the application. */
public class ReportIntegrityException extends RuntimeException {

    public ReportIntegrityException(String reportPublicId) {
        super("Stored content hash mismatch for report " + reportPublicId);
    }
}

package com.hexora.hexora.report;

/** No renderer is registered for the requested report type (the file generators are not built yet). */
public class ReportRendererUnavailableException extends RuntimeException {

    public ReportRendererUnavailableException(ReportType type) {
        super("No renderer registered for " + type);
    }
}

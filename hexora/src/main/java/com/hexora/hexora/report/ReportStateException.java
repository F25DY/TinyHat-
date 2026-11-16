package com.hexora.hexora.report;

/** The requested transition is not valid for the report's current state. */
public class ReportStateException extends RuntimeException {

    public ReportStateException(String message) {
        super(message);
    }
}

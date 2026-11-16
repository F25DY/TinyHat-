package com.hexora.hexora.report;

/** The account's {@code informes_pdf} capability is disabled. */
public class ReportCapabilityDisabledException extends RuntimeException {

    public ReportCapabilityDisabledException() {
        super("Report capability is disabled for this account");
    }
}

package com.hexora.hexora.security;

import java.util.EnumSet;
import java.util.Set;

/**
 * Catalogue of audit events written to {@code audit_event.evento}.
 *
 * <p>The first group is the list required by the security plan (Phase 12, task 1). Events marked
 * {@code RESERVED} exist in the catalogue but have no emitter in v1; the reason is documented in
 * {@code docs/security/auditoria.md}.
 */
public enum AuditEventType {
    LOGIN_OK,
    LOGIN_FAIL,
    MFA_OK,
    MFA_FAIL,
    LOGOUT,
    SESSION_EXPIRED,
    SESSION_REPLACED,
    THROTTLED,
    RATE_LIMITED,
    ACCESS_DENIED,
    NOT_FOUND_SCOPED,
    USER_CREATED,
    USER_INVITED,
    USER_SUSPENDED,
    ROLE_CHANGED,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    MFA_ENROLLED,
    MFA_RESET,
    /** RESERVED: OAuth2/OIDC was not adopted (Phase 9 decision), so nothing links external identities. */
    OAUTH_LINKED,
    REPORT_PUBLISHED,
    REPORT_VIEWED,
    REPORT_EXPORTED,
    SCAN_STARTED,
    SCAN_COMPLETED,
    SCAN_REJECTED,
    SCAN_FAILED,
    RANGE_DECLARED,
    CAPABILITY_CHANGED,
    OPS_COMMAND,
    STARTUP_CONFIG,

    // Additional events (not in the plan list) that complete the traceability of existing flows.
    USER_VERIFIED,
    USER_REACTIVATED,
    REPORT_CREATED,
    REPORT_UNPUBLISHED;

    /** Events that have no emitter in v1. */
    public static final Set<AuditEventType> RESERVED = EnumSet.of(OAUTH_LINKED);

    /** Events required by the plan list of Phase 12 (SCAN_* expands to the scan lifecycle). */
    public static final Set<AuditEventType> REQUIRED_BY_PLAN = EnumSet.of(
        LOGIN_OK, LOGIN_FAIL, MFA_OK, MFA_FAIL, LOGOUT, SESSION_EXPIRED, SESSION_REPLACED,
        THROTTLED, RATE_LIMITED, ACCESS_DENIED, NOT_FOUND_SCOPED, USER_CREATED, USER_INVITED,
        USER_SUSPENDED, ROLE_CHANGED, PASSWORD_CHANGED, PASSWORD_RESET, MFA_ENROLLED, MFA_RESET,
        OAUTH_LINKED, REPORT_PUBLISHED, REPORT_VIEWED, REPORT_EXPORTED, SCAN_STARTED,
        SCAN_COMPLETED, SCAN_REJECTED, RANGE_DECLARED, CAPABILITY_CHANGED, OPS_COMMAND, STARTUP_CONFIG
    );
}

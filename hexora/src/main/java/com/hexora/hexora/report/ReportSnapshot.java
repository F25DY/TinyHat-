package com.hexora.hexora.report;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Immutable content of a report, serialized once to JSON when the report is created.
 *
 * <p>Every text field is plain text taken from user-entered data or from external catalogues (NVD):
 * renderers MUST escape it for their output format. Bump {@link #CURRENT_SCHEMA_VERSION} whenever the
 * shape changes (for example when the network annex is added) so old snapshots stay readable.
 */
public record ReportSnapshot(
    int schemaVersion,
    String generatedAt,
    String type,
    String audience,
    ProjectSection project,
    OrganizationSection organization,
    ScopeSection scope,
    SummarySection summary,
    List<FindingEntry> findings,
    List<MitigationEntry> mitigations,
    RiskMatrix riskMatrix
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public record ProjectSection(
        String publicId, String name, String auditType, String status, String startDate, String endDate
    ) {
    }

    public record OrganizationSection(String name, String taxId, String sector) {
    }

    /** Authorized network ranges declared for the project (the scan scope the audit relied on). */
    public record ScopeSection(List<AuthorizedRange> authorizedRanges) {
    }

    public record AuthorizedRange(String cidr, String authorizationReference, String declaredOn, String validUntil) {
    }

    public record SummarySection(
        int totalFindings,
        int areaCount,
        String overallRisk,
        Map<String, Integer> findingsByRiskLevel,
        Map<String, Integer> findingsByStatus,
        List<String> topRisks
    ) {
    }

    public record FindingEntry(
        String publicId,
        String areaPublicId,
        String areaName,
        String name,
        String type,
        String tool,
        String host,
        String ipAddress,
        Integer port,
        String service,
        String cveCode,
        CveInfo cve,
        int confidentiality,
        int integrity,
        int availability,
        int probability,
        BigDecimal impact,
        String riskLevel,
        String status,
        String detectedOn,
        String mitigationPlan
    ) {
    }

    /** Catalogue data for the finding's CVE, when the CVE exists in the local NVD cache. */
    public record CveInfo(
        String description,
        String descriptionEs,
        BigDecimal cvssScore,
        String cvssVersion,
        String severity,
        String cwe,
        List<String> patchReferences
    ) {
    }

    public record MitigationEntry(
        String findingPublicId,
        String areaName,
        String findingName,
        String riskLevel,
        String status,
        boolean planDocumented,
        String plan
    ) {
    }

    /**
     * Risk matrix: {@code cells[p][i]} counts findings whose probability falls in band {@code p}
     * (rows, low to high) and whose impact falls in band {@code i} (columns, low to high). Bands are
     * {@code 0-1, 2-3, 4-5, 6-7, 8-10}, see {@link RiskMatrixCalculator}.
     */
    public record RiskMatrix(List<String> bandLabels, List<List<Integer>> cells) {
    }
}

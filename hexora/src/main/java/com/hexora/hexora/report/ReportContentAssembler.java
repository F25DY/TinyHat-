package com.hexora.hexora.report;

import com.hexora.hexora.model.AreaAuditable;
import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.Cve;
import com.hexora.hexora.model.Empresa;
import com.hexora.hexora.model.Vulnerabilidad;
import com.hexora.hexora.repository.AreaAuditableRepository;
import com.hexora.hexora.repository.CveRepository;
import com.hexora.hexora.repository.VulnerabilidadRepository;
import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.network.repository.AuthorizedNetworkRangeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the {@link ReportSnapshot} of a project from the data the auditor is allowed to see.
 *
 * <p>The caller must pass a project already resolved through {@code PermissionService.requireProyecto}
 * (account and assignment checked); every repository call here also filters by the principal's account.
 * Findings, areas and ranges of other projects or accounts can therefore never enter a snapshot.
 */
@Component
public class ReportContentAssembler {

    private static final Pattern CVE_CODE = Pattern.compile("^CVE-\\d{4}-\\d{4,19}$", Pattern.CASE_INSENSITIVE);
    private static final int TOP_RISKS = 5;

    private final AreaAuditableRepository areaRepository;
    private final VulnerabilidadRepository findingRepository;
    private final CveRepository cveRepository;
    private final AuthorizedNetworkRangeRepository rangeRepository;
    private final Clock clock;

    public ReportContentAssembler(
        AreaAuditableRepository areaRepository,
        VulnerabilidadRepository findingRepository,
        CveRepository cveRepository,
        AuthorizedNetworkRangeRepository rangeRepository,
        Clock clock
    ) {
        this.areaRepository = areaRepository;
        this.findingRepository = findingRepository;
        this.cveRepository = cveRepository;
        this.rangeRepository = rangeRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReportSnapshot assemble(TinyHatPrincipal principal, Auditoria project, ReportType type) {
        long accountId = principal.getCuentaId();
        List<AreaAuditable> areas = areaRepository.findByAuditoriaAndCuentaId(project, accountId);
        Map<String, Optional<Cve>> cveCache = new HashMap<>();

        List<ReportSnapshot.FindingEntry> findings = new ArrayList<>();
        for (AreaAuditable area : areas) {
            for (Vulnerabilidad finding : findingRepository.findByAreaAuditableAndCuentaId(area, accountId)) {
                findings.add(toEntry(area, finding, cveCache));
            }
        }
        findings.sort(Comparator
            .comparingInt((ReportSnapshot.FindingEntry entry) -> RiskMatrixCalculator.severityRank(entry.riskLevel()))
            .thenComparing(ReportSnapshot.FindingEntry::impact, Comparator.reverseOrder())
            .thenComparing(ReportSnapshot.FindingEntry::name, String.CASE_INSENSITIVE_ORDER));

        return new ReportSnapshot(
            ReportSnapshot.CURRENT_SCHEMA_VERSION,
            Instant.now(clock).toString(),
            type.name(),
            type.audience(),
            projectSection(project),
            organizationSection(project.getEmpresa()),
            scopeSection(accountId, project.getIdAuditoria()),
            summary(areas.size(), findings),
            List.copyOf(findings),
            mitigations(findings),
            RiskMatrixCalculator.matrix(findings)
        );
    }

    private ReportSnapshot.ProjectSection projectSection(Auditoria project) {
        return new ReportSnapshot.ProjectSection(
            project.getPublicId(),
            project.getNombre(),
            project.getTipoAuditoria(),
            project.getEstado(),
            String.valueOf(project.getFechaInico()),
            String.valueOf(project.getFechaFin()));
    }

    private ReportSnapshot.OrganizationSection organizationSection(Empresa organization) {
        return new ReportSnapshot.OrganizationSection(
            organization.getNombre(), organization.getNit(), organization.getSector());
    }

    private ReportSnapshot.ScopeSection scopeSection(long accountId, Long projectId) {
        List<ReportSnapshot.AuthorizedRange> ranges = rangeRepository
            .findAllByCuentaIdAndProyectoIdOrderByDeclaradoEnDesc(accountId, projectId)
            .stream()
            .map(range -> new ReportSnapshot.AuthorizedRange(
                range.getCidr(),
                range.getReferenciaAutorizacion(),
                String.valueOf(range.getDeclaradoEn()),
                range.getVigenteHasta() == null ? null : range.getVigenteHasta().toString()))
            .toList();
        return new ReportSnapshot.ScopeSection(ranges);
    }

    private ReportSnapshot.FindingEntry toEntry(
        AreaAuditable area, Vulnerabilidad finding, Map<String, Optional<Cve>> cveCache
    ) {
        String cveCode = finding.getCve() == null ? null : finding.getCve().trim().toUpperCase(java.util.Locale.ROOT);
        ReportSnapshot.CveInfo cveInfo = null;
        if (cveCode != null && CVE_CODE.matcher(cveCode).matches()) {
            cveInfo = cveCache
                .computeIfAbsent(cveCode, cveRepository::findByCodigoCve)
                .map(this::toCveInfo)
                .orElse(null);
        }
        return new ReportSnapshot.FindingEntry(
            finding.getPublicId(),
            area.getPublicId(),
            area.getNombre(),
            finding.getNombre(),
            finding.getTipo(),
            finding.getHerramienta(),
            finding.getHost(),
            finding.getIpAfectada(),
            finding.getPuerto(),
            finding.getServicio(),
            cveCode,
            cveInfo,
            finding.getConfidencialidad(),
            finding.getIntegridad(),
            finding.getDisponibilidad(),
            finding.getProbabilidad(),
            finding.getImpacto(),
            finding.getNivelRiesgo(),
            finding.getEstado(),
            String.valueOf(finding.getFechaDeteccion()),
            finding.getPlanMitigacion());
    }

    private ReportSnapshot.CveInfo toCveInfo(Cve cve) {
        List<String> patches = cve.getReferenciasParche() == null || cve.getReferenciasParche().isBlank()
            ? List.of()
            : Arrays.stream(cve.getReferenciasParche().split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return new ReportSnapshot.CveInfo(
            cve.getDescripcion(),
            cve.getDescripcionEs(),
            cve.getPuntajeCvss(),
            cve.getVersionCvss(),
            cve.getSeveridad(),
            cve.getCwe(),
            patches);
    }

    private ReportSnapshot.SummarySection summary(int areaCount, List<ReportSnapshot.FindingEntry> findings) {
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (String status : List.of("Abierta", "En Progreso", "Mitigada", "Aceptada")) {
            byStatus.put(status, 0);
        }
        findings.forEach(finding -> byStatus.merge(finding.status(), 1, Integer::sum));
        List<String> topRisks = findings.stream()
            .filter(finding -> RiskMatrixCalculator.isUnresolved(finding.status()))
            .limit(TOP_RISKS)
            .map(ReportSnapshot.FindingEntry::name)
            .toList();
        return new ReportSnapshot.SummarySection(
            findings.size(),
            areaCount,
            RiskMatrixCalculator.overallRisk(findings),
            RiskMatrixCalculator.countByLevel(findings),
            byStatus,
            topRisks);
    }

    private List<ReportSnapshot.MitigationEntry> mitigations(List<ReportSnapshot.FindingEntry> findings) {
        return findings.stream()
            .map(finding -> {
                boolean documented = finding.mitigationPlan() != null && !finding.mitigationPlan().isBlank();
                return new ReportSnapshot.MitigationEntry(
                    finding.publicId(),
                    finding.areaName(),
                    finding.name(),
                    finding.riskLevel(),
                    finding.status(),
                    documented,
                    documented ? finding.mitigationPlan() : null);
            })
            .toList();
    }
}

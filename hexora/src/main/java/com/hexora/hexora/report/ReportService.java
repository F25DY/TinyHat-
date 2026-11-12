package com.hexora.hexora.report;

import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.Informe;
import com.hexora.hexora.repository.AuditoriaRepository;
import com.hexora.hexora.repository.InformeRepository;
import com.hexora.hexora.security.AuditEventType;
import com.hexora.hexora.security.AuditService;
import com.hexora.hexora.security.ScopedNotFoundException;
import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.service.Capability;
import com.hexora.hexora.service.CapabilityService;
import com.hexora.hexora.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Report lifecycle: draft (snapshot + SHA-256) -> published -> exported. Authorization always goes through
 * {@link PermissionService}: an AUDITOR needs the project assignment; a CLIENTE_ADMIN only reaches
 * PUBLICADO reports of their own organization; anything else is a scoped 404.
 */
@Service
public class ReportService {

    private final PermissionService permissionService;
    private final ReportContentAssembler assembler;
    private final InformeRepository reportRepository;
    private final AuditoriaRepository projectRepository;
    private final CapabilityService capabilityService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final List<ReportRenderer> renderers;
    private final Clock clock;

    public ReportService(
        PermissionService permissionService,
        ReportContentAssembler assembler,
        InformeRepository reportRepository,
        AuditoriaRepository projectRepository,
        CapabilityService capabilityService,
        AuditService auditService,
        ObjectMapper objectMapper,
        List<ReportRenderer> renderers,
        Clock clock
    ) {
        this.permissionService = permissionService;
        this.assembler = assembler;
        this.reportRepository = reportRepository;
        this.projectRepository = projectRepository;
        this.capabilityService = capabilityService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.renderers = renderers;
        this.clock = clock;
    }

    @Transactional
    public ReportDto createDraft(
        TinyHatPrincipal principal, String projectPublicId, CreateReportRequest request, HttpServletRequest http
    ) {
        requireAuditor(principal);
        requireCapability(principal);
        Auditoria project = permissionService.requireProyecto(principal, projectPublicId);

        ReportSnapshot snapshot = assembler.assemble(principal, project, request.tipo());
        String json = objectMapper.writeValueAsString(snapshot);

        Informe report = new Informe();
        report.setCuentaId(principal.getCuentaId());
        report.setOrganizacionId(project.getEmpresa().getId());
        report.setProyectoId(project.getIdAuditoria());
        report.setTipo(request.tipo().name());
        report.setTitulo(request.titulo().trim());
        report.setAudiencia(request.tipo().audience());
        report.setFormato(request.tipo().format());
        report.setEsquemaVersion(snapshot.schemaVersion());
        report.setEstado(Informe.ESTADO_BORRADOR);
        report.setContenidoJson(json);
        report.setHashContenido(sha256(json));
        report.setCreadoPor(principal.getUserId());
        reportRepository.saveAndFlush(report);

        auditService.reportEvent(AuditEventType.REPORT_CREATED, principal, http, report.getPublicId(),
            Map.of("type", report.getTipo(), "findings", snapshot.summary().totalFindings()));
        return ReportDto.from(report, project.getPublicId());
    }

    @Transactional(readOnly = true)
    public List<ReportDto> list(TinyHatPrincipal principal, String projectPublicId) {
        Auditoria project = permissionService.requireProyecto(principal, projectPublicId);
        List<Informe> reports = "AUDITOR".equals(principal.getRol())
            ? reportRepository.findByCuentaIdAndProyectoIdOrderByCreadoEnDesc(
                principal.getCuentaId(), project.getIdAuditoria())
            : reportRepository.findByCuentaIdAndProyectoIdAndEstadoOrderByPublicadoEnDesc(
                principal.getCuentaId(), project.getIdAuditoria(), Informe.ESTADO_PUBLICADO);
        return reports.stream().map(report -> ReportDto.from(report, project.getPublicId())).toList();
    }

    @Transactional(readOnly = true)
    public ReportDto get(TinyHatPrincipal principal, String reportPublicId) {
        Informe report = permissionService.requireInforme(principal, reportPublicId);
        return toDto(report);
    }

    /** Returns the stored snapshot JSON after verifying its integrity. */
    @Transactional
    public String content(TinyHatPrincipal principal, String reportPublicId, HttpServletRequest http) {
        Informe report = permissionService.requireInforme(principal, reportPublicId);
        String json = verifiedContent(report);
        auditService.reportEvent(AuditEventType.REPORT_VIEWED, principal, http, report.getPublicId(),
            Map.of("type", report.getTipo(), "state", report.getEstado()));
        return json;
    }

    @Transactional
    public ReportDto publish(TinyHatPrincipal principal, String reportPublicId, HttpServletRequest http) {
        requireAuditor(principal);
        Informe report = permissionService.requireInforme(principal, reportPublicId);
        if (!Informe.ESTADO_BORRADOR.equals(report.getEstado())) {
            throw new ReportStateException("Only a draft can be published");
        }
        verifiedContent(report);
        report.setEstado(Informe.ESTADO_PUBLICADO);
        report.setPublicadoEn(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
        report.setPublicadoPor(principal.getUserId());
        reportRepository.saveAndFlush(report);
        auditService.reportEvent(AuditEventType.REPORT_PUBLISHED, principal, http, report.getPublicId(),
            Map.of("type", report.getTipo(), "contentHash", report.getHashContenido()));
        return toDto(report);
    }

    @Transactional
    public ReportDto unpublish(TinyHatPrincipal principal, String reportPublicId, HttpServletRequest http) {
        requireAuditor(principal);
        Informe report = permissionService.requireInforme(principal, reportPublicId);
        if (!Informe.ESTADO_PUBLICADO.equals(report.getEstado())) {
            throw new ReportStateException("Only a published report can be unpublished");
        }
        report.setEstado(Informe.ESTADO_BORRADOR);
        report.setPublicadoEn(null);
        report.setPublicadoPor(null);
        reportRepository.saveAndFlush(report);
        auditService.reportEvent(AuditEventType.REPORT_UNPUBLISHED, principal, http, report.getPublicId(),
            Map.of("type", report.getTipo()));
        return toDto(report);
    }

    @Transactional
    public RenderedReport export(TinyHatPrincipal principal, String reportPublicId, HttpServletRequest http) {
        Informe report = permissionService.requireInforme(principal, reportPublicId);
        requireCapability(principal);
        ReportType type = ReportType.valueOf(report.getTipo());
        ReportRenderer renderer = renderers.stream()
            .filter(candidate -> candidate.supports(type))
            .findFirst()
            .orElseThrow(() -> new ReportRendererUnavailableException(type));

        ReportSnapshot snapshot = objectMapper.readValue(verifiedContent(report), ReportSnapshot.class);
        byte[] bytes = renderer.render(snapshot);
        auditService.reportEvent(AuditEventType.REPORT_EXPORTED, principal, http, report.getPublicId(),
            Map.of("type", report.getTipo(), "format", report.getFormato(), "bytes", bytes.length));
        return new RenderedReport(bytes, type.contentType(), type.fileName(report.getPublicId()));
    }

    private ReportDto toDto(Informe report) {
        String projectPublicId = projectRepository
            .findPublicIdByIdAndCuentaId(report.getProyectoId(), report.getCuentaId())
            .orElseThrow(ScopedNotFoundException::new);
        return ReportDto.from(report, projectPublicId);
    }

    private String verifiedContent(Informe report) {
        String json = report.getContenidoJson();
        if (json == null || report.getHashContenido() == null
            || !MessageDigest.isEqual(
                sha256(json).getBytes(StandardCharsets.US_ASCII),
                report.getHashContenido().getBytes(StandardCharsets.US_ASCII))) {
            throw new ReportIntegrityException(report.getPublicId());
        }
        return json;
    }

    private void requireAuditor(TinyHatPrincipal principal) {
        if (!"AUDITOR".equals(principal.getRol())) {
            throw new ScopedNotFoundException();
        }
    }

    private void requireCapability(TinyHatPrincipal principal) {
        if (!capabilityService.isEnabled(principal.getCuentaId(), Capability.INFORMES_PDF)) {
            throw new ReportCapabilityDisabledException();
        }
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

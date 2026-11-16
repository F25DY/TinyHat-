package com.hexora.hexora.report;

import com.hexora.hexora.security.RequireRecentAuth;
import com.hexora.hexora.security.TinyHatPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Report API. Auditors create, publish and unpublish; client administrators only read and download
 * PUBLICADO reports of their organization (enforced by {@code PermissionService}, not by this class).
 */
@RestController
@RequestMapping("/api")
@Validated
public class ReportController {

    private static final String UUID_PATTERN =
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/proyectos/{projectPublicId}/informes")
    @PreAuthorize("hasRole('AUDITOR')")
    public ResponseEntity<ReportDto> create(
        @PathVariable @Pattern(regexp = UUID_PATTERN) String projectPublicId,
        @Valid @RequestBody CreateReportRequest request,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest http
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(reportService.createDraft(principal, projectPublicId, request, http));
    }

    @GetMapping("/proyectos/{projectPublicId}/informes")
    @PreAuthorize("hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')")
    public List<ReportDto> list(
        @PathVariable @Pattern(regexp = UUID_PATTERN) String projectPublicId,
        @AuthenticationPrincipal TinyHatPrincipal principal
    ) {
        return reportService.list(principal, projectPublicId);
    }

    @GetMapping("/informes/{publicId}")
    @PreAuthorize("hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')")
    public ReportDto get(
        @PathVariable @Pattern(regexp = UUID_PATTERN) String publicId,
        @AuthenticationPrincipal TinyHatPrincipal principal
    ) {
        return reportService.get(principal, publicId);
    }

    @GetMapping(value = "/informes/{publicId}/contenido", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')")
    public ResponseEntity<String> content(
        @PathVariable @Pattern(regexp = UUID_PATTERN) String publicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest http
    ) {
        return ResponseEntity.ok(reportService.content(principal, publicId, http));
    }

    @PostMapping("/informes/{publicId}/publicar")
    @PreAuthorize("hasRole('AUDITOR')")
    @RequireRecentAuth(maxAgeMinutes = 5)
    public ReportDto publish(
        @PathVariable @Pattern(regexp = UUID_PATTERN) String publicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest http
    ) {
        return reportService.publish(principal, publicId, http);
    }

    @PostMapping("/informes/{publicId}/despublicar")
    @PreAuthorize("hasRole('AUDITOR')")
    @RequireRecentAuth(maxAgeMinutes = 5)
    public ReportDto unpublish(
        @PathVariable @Pattern(regexp = UUID_PATTERN) String publicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest http
    ) {
        return reportService.unpublish(principal, publicId, http);
    }

    @GetMapping("/informes/{publicId}/descarga")
    @PreAuthorize("hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')")
    public ResponseEntity<byte[]> download(
        @PathVariable @Pattern(regexp = UUID_PATTERN) String publicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest http
    ) {
        RenderedReport rendered = reportService.export(principal, publicId, http);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(rendered.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(rendered.fileName()).build().toString())
            .body(rendered.content());
    }
}

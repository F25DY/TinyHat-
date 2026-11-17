package com.hexora.hexora.service;

import com.hexora.hexora.model.AreaAuditable;
import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.Empresa;
import com.hexora.hexora.model.InformeArchivo;
import com.hexora.hexora.model.InformeVersion;
import com.hexora.hexora.model.Vulnerabilidad;
import com.hexora.hexora.repository.AreaAuditableRepository;
import com.hexora.hexora.repository.AuditoriaRepository;
import com.hexora.hexora.repository.EmpresaRepository;
import com.hexora.hexora.repository.InformeArchivoRepository;
import com.hexora.hexora.repository.InformeVersionRepository;
import com.hexora.hexora.repository.VulnerabilidadRepository;
import com.hexora.hexora.security.ScopedNotFoundException;
import com.hexora.hexora.security.TinyHatPrincipal;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionService {

    private final AuditoriaRepository auditoriaRepository;
    private final AreaAuditableRepository areaAuditableRepository;
    private final VulnerabilidadRepository vulnerabilidadRepository;
    private final InformeVersionRepository informeVersionRepository;
    private final InformeArchivoRepository informeArchivoRepository;
    private final EmpresaRepository empresaRepository;

    public PermissionService(
            AuditoriaRepository auditoriaRepository,
            AreaAuditableRepository areaAuditableRepository,
            VulnerabilidadRepository vulnerabilidadRepository,
            InformeVersionRepository informeVersionRepository,
            InformeArchivoRepository informeArchivoRepository,
            EmpresaRepository empresaRepository) {
        this.auditoriaRepository = auditoriaRepository;
        this.areaAuditableRepository = areaAuditableRepository;
        this.vulnerabilidadRepository = vulnerabilidadRepository;
        this.informeVersionRepository = informeVersionRepository;
        this.informeArchivoRepository = informeArchivoRepository;
        this.empresaRepository = empresaRepository;
    }

    @Transactional(readOnly = true)
    public List<Auditoria> proyectosVisibles(TinyHatPrincipal principal) {
        requireActivePrincipal(principal);
        return auditoriaRepository.findVisibleProjects(
                principal.getCuentaId(),
                principal.getOrganizacionId(),
                principal.getUserId(),
                principal.getRol());
    }

    @Transactional(readOnly = true)
    public Auditoria requireProyecto(TinyHatPrincipal principal, String publicId) {
        requireActivePrincipal(principal);
        return auditoriaRepository.findVisibleByPublicId(
                publicId,
                principal.getCuentaId(),
                principal.getOrganizacionId(),
                principal.getUserId(),
                principal.getRol()).orElseThrow(ScopedNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public AreaAuditable requireArea(TinyHatPrincipal principal, String areaPublicId) {
        requireActivePrincipal(principal);
        return areaAuditableRepository.findVisibleArea(
                areaPublicId,
                null,
                principal.getCuentaId(),
                principal.getOrganizacionId(),
                principal.getUserId(),
                principal.getRol()).orElseThrow(ScopedNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public AreaAuditable requireArea(
            TinyHatPrincipal principal,
            String proyectoPublicId,
            String areaPublicId) {
        requireActivePrincipal(principal);
        return areaAuditableRepository.findVisibleArea(
                areaPublicId,
                proyectoPublicId,
                principal.getCuentaId(),
                principal.getOrganizacionId(),
                principal.getUserId(),
                principal.getRol()).orElseThrow(ScopedNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Vulnerabilidad requireHallazgo(
            TinyHatPrincipal principal,
            String proyectoPublicId,
            String areaPublicId,
            String hallazgoPublicId) {
        requireActivePrincipal(principal);
        if (!"AUDITOR".equals(principal.getRol())) {
            throw new ScopedNotFoundException();
        }
        return vulnerabilidadRepository.findAssignedFinding(
                hallazgoPublicId,
                areaPublicId,
                proyectoPublicId,
                principal.getCuentaId(),
                principal.getUserId()).orElseThrow(ScopedNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public InformeVersion requireInformeVersion(TinyHatPrincipal principal, String publicId) {
        requireActivePrincipal(principal);
        return informeVersionRepository.findVisibleByPublicId(
                publicId,
                principal.getCuentaId(),
                principal.getOrganizacionId(),
                principal.getUserId(),
                principal.getRol()).orElseThrow(ScopedNotFoundException::new);
    }

    /** Archivo de un informe publicado; un CLIENTE_ADMIN no alcanza el de una versión despublicada (404). */
    @Transactional(readOnly = true)
    public InformeArchivo requireInformeArchivo(TinyHatPrincipal principal, String publicId) {
        requireActivePrincipal(principal);
        return informeArchivoRepository.findVisibleByPublicId(
                publicId,
                principal.getCuentaId(),
                principal.getOrganizacionId(),
                principal.getUserId(),
                principal.getRol()).orElseThrow(ScopedNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Empresa requireOrganizacion(TinyHatPrincipal principal, String publicId) {
        requireActivePrincipal(principal);
        if (!"AUDITOR".equals(principal.getRol())) {
            throw new ScopedNotFoundException();
        }
        return empresaRepository.findByPublicIdAndCuentaId(publicId, principal.getCuentaId())
                .orElseThrow(ScopedNotFoundException::new);
    }

    private void requireActivePrincipal(TinyHatPrincipal principal) {
        Objects.requireNonNull(principal, "Authenticated principal is required");
        if (!principal.isEnabled() || principal.getCuentaId() < 1) {
            throw new ScopedNotFoundException();
        }
        if (!"AUDITOR".equals(principal.getRol())
                && !"CLIENTE_ADMIN".equals(principal.getRol())) {
            throw new ScopedNotFoundException();
        }
    }
}

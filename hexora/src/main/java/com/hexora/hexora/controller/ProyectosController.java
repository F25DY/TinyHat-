package com.hexora.hexora.controller;

import com.hexora.hexora.dto.ProyectoForm;
import com.hexora.hexora.dto.ProyectoView;
import com.hexora.hexora.dto.ProyectoView.DepartamentoView;
import com.hexora.hexora.model.AreaAuditable;
import com.hexora.hexora.model.AsignacionAuditorProyecto;
import com.hexora.hexora.model.AsignacionAuditorProyectoId;
import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.Empresa;
import com.hexora.hexora.model.Vulnerabilidad;
import com.hexora.hexora.repository.AreaAuditableRepository;
import com.hexora.hexora.repository.AsignacionAuditorProyectoRepository;
import com.hexora.hexora.repository.AuditoriaRepository;
import com.hexora.hexora.repository.EmpresaRepository;
import com.hexora.hexora.repository.VulnerabilidadRepository;
import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.service.PermissionService;
import jakarta.validation.Valid;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/proyectos")
public class ProyectosController {

    private final AuditoriaRepository auditoriaRepository;
    private final EmpresaRepository empresaRepository;
    private final AreaAuditableRepository areaAuditableRepository;
    private final VulnerabilidadRepository vulnerabilidadRepository;
    private final PermissionService permissionService;
    private final AsignacionAuditorProyectoRepository asignacionRepository;

    public ProyectosController(
            AuditoriaRepository auditoriaRepository,
            EmpresaRepository empresaRepository,
            AreaAuditableRepository areaAuditableRepository,
            VulnerabilidadRepository vulnerabilidadRepository,
            PermissionService permissionService,
            AsignacionAuditorProyectoRepository asignacionRepository) {
        this.auditoriaRepository = auditoriaRepository;
        this.empresaRepository = empresaRepository;
        this.areaAuditableRepository = areaAuditableRepository;
        this.vulnerabilidadRepository = vulnerabilidadRepository;
        this.permissionService = permissionService;
        this.asignacionRepository = asignacionRepository;
    }

    @GetMapping({"", "/"})
    @PreAuthorize("hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')")
    public String listarProyectos(
            @AuthenticationPrincipal TinyHatPrincipal principal,
            Model model) {
        model.addAttribute("auditorias", permissionService.proyectosVisibles(principal));
        model.addAttribute("empresas", empresasVisibles(principal));
        model.addAttribute("canManageProjects", "AUDITOR".equals(principal.getRol()));
        return "proyectos";
    }

    @GetMapping("/nuproyectos")
    @PreAuthorize("hasRole('AUDITOR')")
    public String crearProyecto(Model model) {
        model.addAttribute("proyectoForm", new ProyectoForm());
        model.addAttribute("editing", false);
        model.addAttribute("formAction", "/proyectos/guardar");
        return "nuproyectos";
    }

    @PostMapping("/guardar")
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional
    public String guardarProyectos(
            @AuthenticationPrincipal TinyHatPrincipal principal,
            @Valid @ModelAttribute("proyectoForm") ProyectoForm form,
            BindingResult bindingResult,
            Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editing", false);
            model.addAttribute("formAction", "/proyectos/guardar");
            return "nuproyectos";
        }
        Empresa empresa = new Empresa();
        actualizarOrganizacion(empresa, form);
        empresa.setCuentaId(principal.getCuentaId());
        empresaRepository.save(empresa);

        Auditoria auditoria = new Auditoria();
        auditoria.setNombre(form.getNombre());
        auditoria.setTipoAuditoria(form.getTipoAuditoria());
        auditoria.setFechaInico(form.getFechaInicio());
        auditoria.setFechaFin(form.getFechaFin() == null ? form.getFechaInicio() : form.getFechaFin());
        auditoria.setEstado(form.getEstado());
        auditoria.setEmpresa(empresa);
        auditoria.setCuentaId(principal.getCuentaId());
        auditoriaRepository.save(auditoria);
        AsignacionAuditorProyecto assignment = new AsignacionAuditorProyecto(
                new AsignacionAuditorProyectoId(
                        principal.getUserId(), auditoria.getIdAuditoria(), principal.getCuentaId()),
                LocalDateTime.now(ZoneOffset.UTC));
        asignacionRepository.save(assignment);

        return "redirect:/proyectos";
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')")
    @Transactional(readOnly = true)
    public String detalleProyecto(
            @AuthenticationPrincipal TinyHatPrincipal principal,
            @PathVariable String publicId,
            Model model) {
        Auditoria auditoria = permissionService.requireProyecto(principal, publicId);
        Empresa empresa = auditoria.getEmpresa();
        List<AreaAuditable> areas = areaAuditableRepository.findByAuditoriaAndCuentaId(
                auditoria, principal.getCuentaId());
        boolean auditor = "AUDITOR".equals(principal.getRol());

        List<Vulnerabilidad> findings = auditor
                ? areas.stream()
                    .flatMap(area -> vulnerabilidadRepository
                            .findByAreaAuditableAndCuentaId(area, principal.getCuentaId()).stream())
                    .toList()
                : List.of();

        Map<String, Long> metrics = Map.of(
                "critico", countRisk(findings, "Critico"),
                "alto", countRisk(findings, "Alto"),
                "medio", countRisk(findings, "Medio"),
                "bajo", countRisk(findings, "Bajo"));
        List<DepartamentoView> areasView = areas.stream()
                .map(area -> {
                    List<Vulnerabilidad> areaFindings = auditor
                            ? vulnerabilidadRepository.findByAreaAuditableAndCuentaId(area, principal.getCuentaId())
                            : List.of();
                    return new DepartamentoView(
                            area.getPublicId(),
                            area.getNombre(),
                            area.getDescripcion(),
                            areaFindings.size(),
                            countRisk(areaFindings, "Critico"));
                })
                .toList();
        ProyectoView projectView = new ProyectoView(
                auditoria.getPublicId(),
                auditoria.getNombre(),
                empresa.getNombre(),
                auditoria.getEstado(),
                auditoria.getFechaInico(),
                auditoria.getTipoAuditoria(),
                areasView,
                metrics);

        model.addAttribute("auditoria", auditoria);
        model.addAttribute("empresa", empresa);
        model.addAttribute("proyecto", projectView);
        model.addAttribute("departamentos", areasView);
        model.addAttribute("areas", areas);
        model.addAttribute("metricas", metrics);
        model.addAttribute("canManageProjects", auditor);
        return "detalleproyecto";
    }

    @GetMapping("/{publicId}/editar")
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional(readOnly = true)
    public String proyectoEditar(
            @AuthenticationPrincipal TinyHatPrincipal principal,
            @PathVariable String publicId,
            Model model) {
        Auditoria auditoria = permissionService.requireProyecto(principal, publicId);
        ProyectoForm form = new ProyectoForm();
        form.setNombreEmpresa(auditoria.getEmpresa().getNombre());
        form.setNit(auditoria.getEmpresa().getNit());
        form.setSector(auditoria.getEmpresa().getSector());
        form.setNombre(auditoria.getNombre());
        form.setTipoAuditoria(auditoria.getTipoAuditoria());
        form.setFechaInicio(auditoria.getFechaInico());
        form.setFechaFin(auditoria.getFechaFin());
        form.setEstado(auditoria.getEstado());
        model.addAttribute("proyectoForm", form);
        model.addAttribute("proyectoPublicId", auditoria.getPublicId());
        model.addAttribute("editing", true);
        model.addAttribute("formAction", "/proyectos/" + auditoria.getPublicId() + "/actualizar");
        return "nuproyectos";
    }

    @PostMapping("/{publicId}/actualizar")
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional
    public String proyectoActualizar(
            @AuthenticationPrincipal TinyHatPrincipal principal,
            @PathVariable String publicId,
            @Valid @ModelAttribute("proyectoForm") ProyectoForm form,
            BindingResult bindingResult,
            Model model) {
        Auditoria auditoria = permissionService.requireProyecto(principal, publicId);
        if (bindingResult.hasErrors()) {
            model.addAttribute("proyectoForm", form);
            model.addAttribute("proyectoPublicId", auditoria.getPublicId());
            model.addAttribute("editing", true);
            model.addAttribute("formAction", "/proyectos/" + auditoria.getPublicId() + "/actualizar");
            return "nuproyectos";
        }
        actualizarOrganizacion(auditoria.getEmpresa(), form);
        empresaRepository.save(auditoria.getEmpresa());

        auditoria.setNombre(form.getNombre());
        auditoria.setTipoAuditoria(form.getTipoAuditoria());
        auditoria.setFechaInico(form.getFechaInicio());
        auditoria.setFechaFin(form.getFechaFin() == null ? form.getFechaInicio() : form.getFechaFin());
        auditoria.setEstado(form.getEstado());
        auditoriaRepository.save(auditoria);
        return "redirect:/proyectos/" + auditoria.getPublicId();
    }

    @PostMapping("/{publicId}/eliminar")
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional
    public String eliminarProyecto(
            @AuthenticationPrincipal TinyHatPrincipal principal,
            @PathVariable String publicId) {
        Auditoria auditoria = permissionService.requireProyecto(principal, publicId);
        Empresa empresa = auditoria.getEmpresa();
        List<AreaAuditable> areas = areaAuditableRepository.findByAuditoriaAndCuentaId(
                auditoria, principal.getCuentaId());
        for (AreaAuditable area : areas) {
            vulnerabilidadRepository.deleteAll(
                    vulnerabilidadRepository.findByAreaAuditableAndCuentaId(area, principal.getCuentaId()));
        }
        vulnerabilidadRepository.flush();
        areaAuditableRepository.deleteAll(areas);
        areaAuditableRepository.flush();
        auditoriaRepository.delete(auditoria);
        auditoriaRepository.flush();
        if (empresa != null && auditoriaRepository.countByEmpresa(empresa) == 0) {
            empresaRepository.delete(empresa);
        }
        return "redirect:/proyectos";
    }

    private List<Empresa> empresasVisibles(TinyHatPrincipal principal) {
        if ("CLIENTE_ADMIN".equals(principal.getRol())) {
            return empresaRepository.findByIdAndCuentaId(
                    principal.getOrganizacionId(), principal.getCuentaId()).stream().toList();
        }
        return empresaRepository.findByCuentaIdOrderByNombre(principal.getCuentaId());
    }

    private void actualizarOrganizacion(Empresa empresa, ProyectoForm form) {
        empresa.setNombre(form.getNombreEmpresa());
        empresa.setNit(form.getNit());
        empresa.setSector(form.getSector());
    }

    private long countRisk(List<Vulnerabilidad> findings, String... levels) {
        return findings.stream()
                .filter(finding -> finding.getNivelRiesgo() != null
                        && java.util.Arrays.stream(levels)
                                .anyMatch(level -> normalize(level).equals(normalize(finding.getNivelRiesgo()))))
                .count();
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }
}

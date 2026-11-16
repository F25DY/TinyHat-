package com.hexora.hexora.controller;

import com.hexora.hexora.dto.AreaForm;
import com.hexora.hexora.model.AreaAuditable;
import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.Vulnerabilidad;
import com.hexora.hexora.repository.AreaAuditableRepository;
import com.hexora.hexora.repository.VulnerabilidadRepository;
import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.service.PermissionService;
import jakarta.validation.Valid;
import java.text.Normalizer;
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

@Controller
public class AreaController {

    private final AreaAuditableRepository areaAuditableRepository;
    private final VulnerabilidadRepository vulnerabilidadRepository;
    private final PermissionService permissionService;

    public AreaController(
            AreaAuditableRepository areaAuditableRepository,
            VulnerabilidadRepository vulnerabilidadRepository,
            PermissionService permissionService) {
        this.areaAuditableRepository = areaAuditableRepository;
        this.vulnerabilidadRepository = vulnerabilidadRepository;
        this.permissionService = permissionService;
    }

    @GetMapping("/proyectos/{proyectoPublicId}/areas/nueva")
    @PreAuthorize("hasRole('AUDITOR')")
    public String crearArea(
            @AuthenticationPrincipal TinyHatPrincipal principal,
            @PathVariable String proyectoPublicId,
            Model model) {
        Auditoria project = permissionService.requireProyecto(principal, proyectoPublicId);
        model.addAttribute("auditoria", project);
        model.addAttribute("proyecto", project);
        model.addAttribute("areaForm", new AreaForm());
        model.addAttribute("areaEditing", false);
        model.addAttribute("areaAction", "/proyectos/" + project.getPublicId() + "/areas/guardar");
        return "nudepartamento";
    }

    @PostMapping("/proyectos/{proyectoPublicId}/areas/guardar")
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional
    public String guardarArea(
            @AuthenticationPrincipal TinyHatPrincipal principal,
            @PathVariable String proyectoPublicId,
            @Valid @ModelAttribute("areaForm") AreaForm form,
            BindingResult bindingResult,
            Model model) {
        Auditoria project = permissionService.requireProyecto(principal, proyectoPublicId);
        if (bindingResult.hasErrors()) {
            populateAreaForm(model, project, form, false, null);
            return "nudepartamento";
        }
        AreaAuditable area = new AreaAuditable();
        area.setNombre(form.getNombre());
        area.setDescripcion(form.getDescripcion());
        area.setAuditoria(project);
        area.setCuentaId(principal.getCuentaId());
        areaAuditableRepository.save(area);
        return "redirect:/proyectos/" + project.getPublicId();
    }

    @GetMapping("/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}")
    @PreAuthorize("hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')")
    @Transactional(readOnly = true)
    public String detalleAreaAnidada(
            @AuthenticationPrincipal TinyHatPrincipal principal,
            @PathVariable String proyectoPublicId,
            @PathVariable String areaPublicId,
            Model model) {
        return populateArea(principal, areaPublicId, proyectoPublicId, model);
    }

    @GetMapping("/areas/{areaPublicId}")
    @PreAuthorize("hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')")
    @Transactional(readOnly = true)
    public String detalleArea(
            @AuthenticationPrincipal TinyHatPrincipal principal,
            @PathVariable String areaPublicId,
            Model model) {
        return populateArea(principal, areaPublicId, null, model);
    }

    @GetMapping("/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}/editar")
    @PreAuthorize("hasRole('AUDITOR')")
    public String editarArea(
            @AuthenticationPrincipal TinyHatPrincipal principal,
            @PathVariable String proyectoPublicId,
            @PathVariable String areaPublicId,
            Model model) {
        AreaAuditable area = permissionService.requireArea(principal, proyectoPublicId, areaPublicId);
        model.addAttribute("areaForm", toForm(area));
        model.addAttribute("area", area);
        model.addAttribute("departamento", area);
        model.addAttribute("auditoria", area.getAuditoria());
        model.addAttribute("proyecto", area.getAuditoria());
        model.addAttribute("areaEditing", true);
        model.addAttribute("areaAction", "/proyectos/" + area.getAuditoria().getPublicId()
                + "/departamentos/" + area.getPublicId() + "/actualizar");
        return "nudepartamento";
    }

    @PostMapping("/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}/actualizar")
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional
    public String actualizarArea(
            @AuthenticationPrincipal TinyHatPrincipal principal,
            @PathVariable String proyectoPublicId,
            @PathVariable String areaPublicId,
            @Valid @ModelAttribute("areaForm") AreaForm form,
            BindingResult bindingResult,
            Model model) {
        AreaAuditable area = permissionService.requireArea(principal, proyectoPublicId, areaPublicId);
        if (bindingResult.hasErrors()) {
            populateAreaForm(model, area.getAuditoria(), form, true, area.getPublicId());
            model.addAttribute("area", area);
            model.addAttribute("departamento", area);
            return "nudepartamento";
        }
        area.setNombre(form.getNombre());
        area.setDescripcion(form.getDescripcion());
        areaAuditableRepository.save(area);
        return "redirect:/proyectos/" + area.getAuditoria().getPublicId();
    }

    @PostMapping("/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}/eliminar")
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional
    public String eliminarArea(
            @AuthenticationPrincipal TinyHatPrincipal principal,
            @PathVariable String proyectoPublicId,
            @PathVariable String areaPublicId) {
        AreaAuditable area = permissionService.requireArea(principal, proyectoPublicId, areaPublicId);
        String projectPublicId = area.getAuditoria().getPublicId();
        List<Vulnerabilidad> findings = vulnerabilidadRepository
                .findByAreaAuditableAndCuentaId(area, principal.getCuentaId());
        vulnerabilidadRepository.deleteAll(findings);
        vulnerabilidadRepository.flush();
        areaAuditableRepository.delete(area);
        return "redirect:/proyectos/" + projectPublicId;
    }

    private String populateArea(
            TinyHatPrincipal principal,
            String areaPublicId,
            String proyectoPublicId,
            Model model) {
        AreaAuditable area = proyectoPublicId == null
                ? permissionService.requireArea(principal, areaPublicId)
                : permissionService.requireArea(principal, proyectoPublicId, areaPublicId);
        Auditoria project = area.getAuditoria();
        boolean auditor = "AUDITOR".equals(principal.getRol());
        List<Vulnerabilidad> findings = auditor
                ? vulnerabilidadRepository.findByAreaAuditableAndCuentaId(area, principal.getCuentaId())
                : List.of();

        model.addAttribute("area", area);
        model.addAttribute("departamento", area);
        model.addAttribute("auditoria", project);
        model.addAttribute("proyecto", project);
        model.addAttribute("canManageProjects", auditor);
        model.addAttribute("vulnerabilidades", findings);
        model.addAttribute("canManageProjects", auditor);
        model.addAttribute("metricas", Map.of(
                "critico", countRisk(findings, "Critico"),
                "alto", countRisk(findings, "Alto"),
                "medio", countRisk(findings, "Medio"),
                "bajo", countRisk(findings, "Bajo")));
        return "detalle-area";
    }

    private void populateAreaForm(
            Model model, Auditoria project, AreaForm form, boolean editing, String areaPublicId) {
        model.addAttribute("auditoria", project);
        model.addAttribute("proyecto", project);
        model.addAttribute("areaForm", form);
        model.addAttribute("areaEditing", editing);
        model.addAttribute("areaAction", editing
                ? "/proyectos/" + project.getPublicId()
                        + "/departamentos/" + areaPublicId + "/actualizar"
                : "/proyectos/" + project.getPublicId() + "/areas/guardar");
    }

    private AreaForm toForm(AreaAuditable area) {
        AreaForm form = new AreaForm();
        form.setNombre(area.getNombre());
        form.setDescripcion(area.getDescripcion());
        return form;
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

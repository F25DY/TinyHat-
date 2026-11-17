package com.hexora.hexora.controller;

import com.hexora.hexora.dto.VulnerabilidadForm;
import com.hexora.hexora.model.AreaAuditable;
import com.hexora.hexora.model.Vulnerabilidad;
import com.hexora.hexora.repository.VulnerabilidadRepository;
import com.hexora.hexora.riesgo.EscalaRiesgo;
import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.service.PermissionService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
@PreAuthorize("hasRole('AUDITOR')")
public class VulnerabilidadController {

    private final PermissionService permissionService;
    private final VulnerabilidadRepository vulnerabilidadRepository;

    public VulnerabilidadController(
            PermissionService permissionService,
            VulnerabilidadRepository vulnerabilidadRepository) {
        this.permissionService = permissionService;
        this.vulnerabilidadRepository = vulnerabilidadRepository;
    }

    @GetMapping("/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}/vulnerabilidades/nueva")
    public String crearVulnerabilidad(
            @AuthenticationPrincipal TinyHatPrincipal principal,
            @PathVariable String proyectoPublicId,
            @PathVariable String areaPublicId,
            Model model) {
        AreaAuditable area = permissionService.requireArea(principal, proyectoPublicId, areaPublicId);
        VulnerabilidadForm form = new VulnerabilidadForm();
        form.setEstado("Abierta");
        form.setFechaDeteccion(LocalDate.now());
        form.setConfidencialidad(0);
        form.setIntegridad(0);
        form.setDisponibilidad(0);
        form.setProbabilidad(0);
        populateFormModel(model, area, form, false, formAction(area, null, false));
        return "nuvulnerabilidad";
    }

    @PostMapping("/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}/vulnerabilidades/guardar")
    @Transactional
    public String guardarVulnerabilidad(
            @AuthenticationPrincipal TinyHatPrincipal principal,
            @PathVariable String proyectoPublicId,
            @PathVariable String areaPublicId,
            @Valid @ModelAttribute("vulnerabilidadForm") VulnerabilidadForm form,
            BindingResult bindingResult,
            Model model) {
        AreaAuditable area = permissionService.requireArea(principal, proyectoPublicId, areaPublicId);
        if (bindingResult.hasErrors()) {
            populateFormModel(model, area, form, false, formAction(area, null, false));
            return "nuvulnerabilidad";
        }

        Vulnerabilidad vulnerability = new Vulnerabilidad();
        applyForm(vulnerability, form);
        vulnerability.setAreaAuditable(area);
        vulnerability.setCuentaId(principal.getCuentaId());
        calculateRisk(vulnerability);
        vulnerabilidadRepository.save(vulnerability);
        return "redirect:" + areaUrl(area);
    }

    @GetMapping("/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}/vulnerabilidades/{hallazgoPublicId}/editar")
    @Transactional(readOnly = true)
    public String editarVulnerabilidad(
            @AuthenticationPrincipal TinyHatPrincipal principal,
            @PathVariable String proyectoPublicId,
            @PathVariable String areaPublicId,
            @PathVariable String hallazgoPublicId,
            Model model) {
        Vulnerabilidad vulnerability = permissionService.requireHallazgo(
                principal, proyectoPublicId, areaPublicId, hallazgoPublicId);
        populateFormModel(
                model,
                vulnerability.getAreaAuditable(),
                toForm(vulnerability),
                true,
                formAction(vulnerability.getAreaAuditable(), vulnerability.getPublicId(), true));
        return "nuvulnerabilidad";
    }

    @GetMapping("/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}/vulnerabilidades/{hallazgoPublicId}")
    @Transactional(readOnly = true)
    public String detalleVulnerabilidad(
            @AuthenticationPrincipal TinyHatPrincipal principal,
            @PathVariable String proyectoPublicId,
            @PathVariable String areaPublicId,
            @PathVariable String hallazgoPublicId,
            Model model) {
        Vulnerabilidad vulnerability = permissionService.requireHallazgo(
                principal, proyectoPublicId, areaPublicId, hallazgoPublicId);
        AreaAuditable area = vulnerability.getAreaAuditable();
        model.addAttribute("proyecto", area.getAuditoria());
        model.addAttribute("departamento", area);
        model.addAttribute("vulnerabilidad", vulnerability);
        model.addAttribute("historial", List.of());
        return "detallevulnerabilidad";
    }

    @PostMapping("/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}/vulnerabilidades/{hallazgoPublicId}/actualizar")
    @Transactional
    public String actualizarVulnerabilidad(
            @AuthenticationPrincipal TinyHatPrincipal principal,
            @PathVariable String proyectoPublicId,
            @PathVariable String areaPublicId,
            @PathVariable String hallazgoPublicId,
            @Valid @ModelAttribute("vulnerabilidadForm") VulnerabilidadForm form,
            BindingResult bindingResult,
            Model model) {
        Vulnerabilidad vulnerability = permissionService.requireHallazgo(
                principal, proyectoPublicId, areaPublicId, hallazgoPublicId);
        AreaAuditable area = vulnerability.getAreaAuditable();
        if (bindingResult.hasErrors()) {
            populateFormModel(
                    model, area, form, true, formAction(area, vulnerability.getPublicId(), true));
            return "nuvulnerabilidad";
        }

        applyForm(vulnerability, form);
        calculateRisk(vulnerability);
        vulnerabilidadRepository.save(vulnerability);
        return "redirect:" + areaUrl(area);
    }

    @PostMapping("/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}/vulnerabilidades/{hallazgoPublicId}/eliminar")
    @Transactional
    public String eliminarVulnerabilidad(
            @AuthenticationPrincipal TinyHatPrincipal principal,
            @PathVariable String proyectoPublicId,
            @PathVariable String areaPublicId,
            @PathVariable String hallazgoPublicId) {
        Vulnerabilidad vulnerability = permissionService.requireHallazgo(
                principal, proyectoPublicId, areaPublicId, hallazgoPublicId);
        String target = areaUrl(vulnerability.getAreaAuditable());
        vulnerabilidadRepository.delete(vulnerability);
        return "redirect:" + target;
    }

    private void populateFormModel(
            Model model,
            AreaAuditable area,
            VulnerabilidadForm form,
            boolean editing,
            String formAction) {
        model.addAttribute("proyecto", area.getAuditoria());
        model.addAttribute("departamento", area);
        model.addAttribute("vulnerabilidadForm", form);
        model.addAttribute("editing", editing);
        model.addAttribute("returnUrl", areaUrl(area));
        model.addAttribute("formAction", formAction);
    }

    private VulnerabilidadForm toForm(Vulnerabilidad vulnerability) {
        VulnerabilidadForm form = new VulnerabilidadForm();
        form.setNombre(vulnerability.getNombre());
        form.setTipo(vulnerability.getTipo());
        form.setHerramienta(vulnerability.getHerramienta());
        form.setHost(vulnerability.getHost());
        form.setIpAfectada(vulnerability.getIpAfectada());
        form.setPuerto(vulnerability.getPuerto());
        form.setServicio(vulnerability.getServicio());
        form.setCve(vulnerability.getCve());
        form.setConfidencialidad(vulnerability.getConfidencialidad());
        form.setIntegridad(vulnerability.getIntegridad());
        form.setDisponibilidad(vulnerability.getDisponibilidad());
        form.setProbabilidad(vulnerability.getProbabilidad());
        form.setEstado(vulnerability.getEstado());
        form.setFechaDeteccion(vulnerability.getFechaDeteccion());
        form.setPlanMitigacion(vulnerability.getPlanMitigacion());
        form.setImpacto(vulnerability.getImpacto());
        form.setNivelRiesgo(vulnerability.getNivelRiesgo());
        return form;
    }

    private void applyForm(Vulnerabilidad vulnerability, VulnerabilidadForm form) {
        vulnerability.setNombre(form.getNombre());
        vulnerability.setTipo(form.getTipo());
        vulnerability.setHerramienta(form.getHerramienta());
        vulnerability.setHost(form.getHost());
        vulnerability.setIpAfectada(form.getIpAfectada());
        vulnerability.setPuerto(form.getPuerto());
        vulnerability.setServicio(form.getServicio());
        vulnerability.setCve(form.getCve());
        vulnerability.setConfidencialidad(form.getConfidencialidad());
        vulnerability.setIntegridad(form.getIntegridad());
        vulnerability.setDisponibilidad(form.getDisponibilidad());
        vulnerability.setProbabilidad(form.getProbabilidad());
        vulnerability.setEstado(form.getEstado());
        vulnerability.setFechaDeteccion(form.getFechaDeteccion());
        vulnerability.setPlanMitigacion(form.getPlanMitigacion());
    }

    /** El servidor recalcula siempre el riesgo con la regla única de {@link EscalaRiesgo}; lo que mande el cliente se ignora. */
    private void calculateRisk(Vulnerabilidad vulnerability) {
        int confidentiality = vulnerability.getConfidencialidad();
        int integrity = vulnerability.getIntegridad();
        int availability = vulnerability.getDisponibilidad();

        vulnerability.setImpacto(BigDecimal.valueOf(
                EscalaRiesgo.impactoGuardado(confidentiality, integrity, availability)).setScale(2));
        vulnerability.setNivelRiesgo(EscalaRiesgo.nivelDeVulnerabilidad(
                confidentiality, integrity, availability, vulnerability.getProbabilidad()).valorAlmacenado());
    }

    private String areaUrl(AreaAuditable area) {
        return "/proyectos/" + area.getAuditoria().getPublicId()
                + "/departamentos/" + area.getPublicId();
    }

    private String formAction(AreaAuditable area, String findingPublicId, boolean editing) {
        String base = areaUrl(area) + "/vulnerabilidades";
        if (!editing) {
            return base + "/guardar";
        }
        return base + "/" + findingPublicId + "/actualizar";
    }
}

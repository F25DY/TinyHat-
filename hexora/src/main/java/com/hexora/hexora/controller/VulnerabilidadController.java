package com.hexora.hexora.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.hexora.hexora.model.AreaAuditable;
import com.hexora.hexora.model.Vulnerabilidad;
import com.hexora.hexora.repository.AreaAuditableRepository;
import com.hexora.hexora.repository.VulnerabilidadRepository;

@Controller
@PreAuthorize("hasRole('AUDITOR')")
public class VulnerabilidadController {

    private final VulnerabilidadRepository vulnerabilidadRepository;
    private final AreaAuditableRepository areaAuditableRepository;

    public VulnerabilidadController(
            VulnerabilidadRepository vulnerabilidadRepository,
            AreaAuditableRepository areaAuditableRepository) {
        this.vulnerabilidadRepository = vulnerabilidadRepository;
        this.areaAuditableRepository = areaAuditableRepository;
    }

    @GetMapping("/proyectos/{pid}/departamentos/{did}/vulnerabilidades/nueva")
    public String crearVulnerabilidad(
            @PathVariable Long pid,
            @PathVariable Long did,
            Model model) {
        AreaAuditable departamento = obtenerDepartamento(did);
        Vulnerabilidad vulnerabilidad = new Vulnerabilidad();
        vulnerabilidad.setEstado("Abierta");
        vulnerabilidad.setNivelRiesgo("Bajo");
        vulnerabilidad.setImpacto(BigDecimal.ZERO);
        vulnerabilidad.setFechaDeteccion(LocalDate.now());

        cargarModeloFormulario(
                model,
                departamento,
                vulnerabilidad,
                "/proyectos/" + pid + "/departamentos/" + did + "/vulnerabilidades/guardar");
        return "nuvulnerabilidad";
    }

    @PostMapping("/proyectos/{pid}/departamentos/{did}/vulnerabilidades/guardar")
    public String guardarVulnerabilidad(
            @PathVariable Long pid,
            @PathVariable Long did,
            @ModelAttribute("vulnerabilidadForm") Vulnerabilidad vulnerabilidad) {
        AreaAuditable departamento = obtenerDepartamento(did);
        vulnerabilidad.setAreaAuditable(departamento);
        calcularRiesgo(vulnerabilidad);
        vulnerabilidadRepository.save(vulnerabilidad);

        return "redirect:/proyectos/" + pid + "/departamentos/" + did;
    }

    @GetMapping("/proyectos/{pid}/departamentos/{did}/vulnerabilidades/{vid}/editar")
    public String editarVulnerabilidad(
            @PathVariable Long pid,
            @PathVariable Long did,
            @PathVariable Long vid,
            Model model) {
        AreaAuditable departamento = obtenerDepartamento(did);
        Vulnerabilidad vulnerabilidad = vulnerabilidadRepository.findById(vid)
                .orElseThrow(() -> new IllegalArgumentException("Vulnerabilidad no encontrada"));

        cargarModeloFormulario(
                model,
                departamento,
                vulnerabilidad,
                "/proyectos/" + pid + "/departamentos/" + did + "/vulnerabilidades/" + vid + "/actualizar");
        return "nuvulnerabilidad";
    }

    @GetMapping("/proyectos/{pid}/departamentos/{did}/vulnerabilidades/{vid}")
    public String detalleVulnerabilidad(
            @PathVariable Long pid,
            @PathVariable Long did,
            @PathVariable Long vid,
            Model model) {
        AreaAuditable departamento = obtenerDepartamento(did);
        Vulnerabilidad vulnerabilidad = vulnerabilidadRepository.findById(vid)
                .orElseThrow(() -> new IllegalArgumentException("Vulnerabilidad no encontrada"));

        model.addAttribute("proyecto", departamento.getAuditoria());
        model.addAttribute("departamento", departamento);
        model.addAttribute("vulnerabilidad", vulnerabilidad);
        model.addAttribute("historial", List.of());
        return "detallevulnerabilidad";
    }

    @PostMapping("/proyectos/{pid}/departamentos/{did}/vulnerabilidades/{vid}/actualizar")
    public String actualizarVulnerabilidad(
            @PathVariable Long pid,
            @PathVariable Long did,
            @PathVariable Long vid,
            @ModelAttribute("vulnerabilidadForm") Vulnerabilidad vulnerabilidad) {
        AreaAuditable departamento = obtenerDepartamento(did);
        vulnerabilidad.setId(vid);
        vulnerabilidad.setAreaAuditable(departamento);
        calcularRiesgo(vulnerabilidad);
        vulnerabilidadRepository.save(vulnerabilidad);

        return "redirect:/proyectos/" + pid + "/departamentos/" + did;
    }

    @PostMapping("/proyectos/{pid}/departamentos/{did}/vulnerabilidades/eliminar/{vid}")
    public String eliminarVulnerabilidad(
            @PathVariable Long pid,
            @PathVariable Long did,
            @PathVariable Long vid) {
        vulnerabilidadRepository.deleteById(vid);
        return "redirect:/proyectos/" + pid + "/departamentos/" + did;
    }

    private AreaAuditable obtenerDepartamento(Long did) {
        return areaAuditableRepository.findById(did)
                .orElseThrow(() -> new IllegalArgumentException("Departamento no encontrado"));
    }

    private void calcularRiesgo(Vulnerabilidad vulnerabilidad) {
        BigDecimal confidencialidad = BigDecimal.valueOf(vulnerabilidad.getConfidencialidad());
        BigDecimal integridad = BigDecimal.valueOf(vulnerabilidad.getIntegridad());
        BigDecimal disponibilidad = BigDecimal.valueOf(vulnerabilidad.getDisponibilidad());
        BigDecimal probabilidad = BigDecimal.valueOf(vulnerabilidad.getProbabilidad());

        BigDecimal impacto = confidencialidad
                .add(integridad)
                .add(disponibilidad)
                .divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        BigDecimal puntaje = impacto
                .add(probabilidad)
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

        vulnerabilidad.setImpacto(impacto);
        vulnerabilidad.setNivelRiesgo(obtenerNivelRiesgo(puntaje));
    }

    private String obtenerNivelRiesgo(BigDecimal puntaje) {
        if (puntaje.compareTo(BigDecimal.valueOf(7)) >= 0) {
            return "Critico";
        }
        if (puntaje.compareTo(BigDecimal.valueOf(5)) >= 0) {
            return "Alto";
        }
        if (puntaje.compareTo(BigDecimal.valueOf(3)) >= 0) {
            return "Medio";
        }
        return "Bajo";
    }

    private void cargarModeloFormulario(
            Model model,
            AreaAuditable departamento,
            Vulnerabilidad vulnerabilidad,
            String formAction) {
        Long idProyecto = departamento.getAuditoria().getIdAuditoria();
        Long idDepartamento = departamento.getId();

        model.addAttribute("proyecto", departamento.getAuditoria());
        model.addAttribute("departamento", departamento);
        model.addAttribute("vulnerabilidadForm", vulnerabilidad);
        model.addAttribute("idProyecto", idProyecto);
        model.addAttribute("idDepartamento", idDepartamento);
        model.addAttribute("returnUrl", "/proyectos/" + idProyecto + "/departamentos/" + idDepartamento);
        model.addAttribute("formAction", formAction);
    }
}

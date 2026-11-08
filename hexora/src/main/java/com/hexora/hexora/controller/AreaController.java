package com.hexora.hexora.controller;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.hexora.hexora.model.AreaAuditable;
import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.Vulnerabilidad;
import com.hexora.hexora.repository.AreaAuditableRepository;
import com.hexora.hexora.repository.AuditoriaRepository;
import com.hexora.hexora.repository.VulnerabilidadRepository;

@Controller
@PreAuthorize("hasRole('AUDITOR')")
public class AreaController {

    private final AreaAuditableRepository areaAuditableRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final VulnerabilidadRepository vulnerabilidadRepository;

    public AreaController(
            AreaAuditableRepository areaAuditableRepository,
            AuditoriaRepository auditoriaRepository,
            VulnerabilidadRepository vulnerabilidadRepository) {
        this.areaAuditableRepository = areaAuditableRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.vulnerabilidadRepository = vulnerabilidadRepository;
    }

    @GetMapping({"/proyectos/{idProyecto}/areas/nueva", "/proyectos/{idProyecto}/departamentos/nuevo"})
    public String crearArea(@PathVariable Long idProyecto, Model model) {
        Auditoria auditoria = auditoriaRepository.findById(idProyecto)
                .orElseThrow(() -> new IllegalArgumentException("Auditoria no encontrada"));

        model.addAttribute("auditoria", auditoria);
        model.addAttribute("proyecto", auditoria);
        model.addAttribute("departamentoForm", new AreaAuditable());
        return "nudepartamento";
    }

    @PostMapping({"/proyectos/{idProyecto}/areas/guardar", "/proyectos/{idProyecto}/departamentos/guardar"})
    public String guardarArea(
            @PathVariable Long idProyecto,
            @RequestParam String nombre,
            @RequestParam(required = false) String descripcion) {

        Auditoria auditoria = auditoriaRepository.findById(idProyecto)
                .orElseThrow(() -> new IllegalArgumentException("Auditoria no encontrada"));

        AreaAuditable area = new AreaAuditable();
        area.setNombre(nombre);
        area.setDescripcion(descripcion);
        area.setAuditoria(auditoria);

        areaAuditableRepository.save(area);

        return "redirect:/proyectos/" + idProyecto;
    }

    @GetMapping({"/areas/{idArea}", "/proyectos/{idProyecto}/departamentos/{idArea}"})
    public String detalleArea(@PathVariable Map<String, String> pathVariables, Model model) {
        Long idArea = Long.valueOf(pathVariables.get("idArea"));
        AreaAuditable area = areaAuditableRepository.findById(idArea)
                .orElseThrow(() -> new IllegalArgumentException("Area no encontrada"));
        Auditoria auditoria = area.getAuditoria();
        List<Vulnerabilidad> vulnerabilidades = vulnerabilidadRepository.findByAreaAuditable(area);

        model.addAttribute("area", area);
        model.addAttribute("departamento", area);
        model.addAttribute("auditoria", auditoria);
        model.addAttribute("proyecto", auditoria);
        model.addAttribute("vulnerabilidades", vulnerabilidades);
        model.addAttribute("metricas", Map.of(
                "critico", contarRiesgo(vulnerabilidades, "Critico"),
                "alto", contarRiesgo(vulnerabilidades, "Alto"),
                "medio", contarRiesgo(vulnerabilidades, "Medio"),
                "bajo", contarRiesgo(vulnerabilidades, "Bajo")
        ));

        return "detalle-area";
    }

    @GetMapping("/areas/{idArea}/editar")
    public String editarArea(@PathVariable Long idArea, Model model) {
        AreaAuditable area = areaAuditableRepository.findById(idArea)
                .orElseThrow(() -> new IllegalArgumentException("Area no encontrada"));

        model.addAttribute("area", area);
        model.addAttribute("departamento", area);
        model.addAttribute("auditoria", area.getAuditoria());
        model.addAttribute("proyecto", area.getAuditoria());

        return "editar-area";
    }

    @PostMapping("/areas/{idArea}/actualizar")
    public String actualizarArea(
            @PathVariable Long idArea,
            @RequestParam String nombre,
            @RequestParam(required = false) String descripcion) {
        AreaAuditable area = areaAuditableRepository.findById(idArea)
                .orElseThrow(() -> new IllegalArgumentException("Area no encontrada"));

        area.setNombre(nombre);
        area.setDescripcion(descripcion);

        areaAuditableRepository.save(area);

        Long idProyecto = area.getAuditoria().getIdAuditoria();
        return "redirect:/proyectos/" + idProyecto;
    }

    @PostMapping("/areas/{idArea}/eliminar")
    public String eliminarArea(@PathVariable Long idArea) {
        AreaAuditable area = areaAuditableRepository.findById(idArea)
                .orElseThrow(() -> new IllegalArgumentException("Area no encontrada"));

        Long idProyecto = area.getAuditoria().getIdAuditoria();
        var vulnerabilidades = vulnerabilidadRepository.findByAreaAuditable(area);
        vulnerabilidadRepository.deleteAll(vulnerabilidades);
        vulnerabilidadRepository.flush();

        areaAuditableRepository.delete(area);

        return "redirect:/proyectos/" + idProyecto;
    }

    private long contarRiesgo(List<Vulnerabilidad> vulnerabilidades, String... niveles) {
        return vulnerabilidades.stream()
                .filter(vulnerabilidad -> {
                    String nivel = vulnerabilidad.getNivelRiesgo();
                    if (nivel == null) {
                        return false;
                    }
                    String nivelNormalizado = normalizar(nivel);
                    for (String esperado : niveles) {
                        if (nivelNormalizado.equals(normalizar(esperado))) {
                            return true;
                        }
                    }
                    return false;
                })
                .count();
    }

    private String normalizar(String valor) {
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }
}

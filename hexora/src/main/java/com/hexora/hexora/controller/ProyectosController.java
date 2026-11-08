package com.hexora.hexora.controller;

import java.time.LocalDate;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.hexora.hexora.dto.ProyectoView;
import com.hexora.hexora.dto.ProyectoView.DepartamentoView;
import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.Empresa;
import com.hexora.hexora.model.Vulnerabilidad;
import com.hexora.hexora.repository.AreaAuditableRepository;
import com.hexora.hexora.repository.AuditoriaRepository;
import com.hexora.hexora.repository.EmpresaRepository;
import com.hexora.hexora.repository.VulnerabilidadRepository;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;



@Controller
@RequestMapping("/proyectos")
@PreAuthorize("hasRole('AUDITOR')")
public class ProyectosController {

    private final AuditoriaRepository auditoriaRepository;
    private final EmpresaRepository empresaRepository;
    private final AreaAuditableRepository areaAuditableRepository;
    private final VulnerabilidadRepository vulnerabilidadRepository;

    
    public ProyectosController(
            AuditoriaRepository auditoriaRepository,
            EmpresaRepository empresaRepository,
            AreaAuditableRepository areaAuditableRepository,
            VulnerabilidadRepository vulnerabilidadRepository) {
        this.auditoriaRepository = auditoriaRepository;
        this.empresaRepository = empresaRepository;
        this.areaAuditableRepository = areaAuditableRepository;
        this.vulnerabilidadRepository = vulnerabilidadRepository;
    }

    @GetMapping({"", "/"})
    public String listarProyectos(Model model) {
        model.addAttribute("auditorias", auditoriaRepository.findAll());
        model.addAttribute("empresas", empresaRepository.findAll());
        return "proyectos";
    }
    
    @GetMapping("/nuproyectos")
    public String crearProyecto() {
        return "nuproyectos";
    }

    @PostMapping("/guardar")
    @Transactional
    public String guardarProyectos(
        @RequestParam String nombreEmpresa,
        @RequestParam(required=false) String nit,
        @RequestParam(required = false) String sector,

        @RequestParam String nombre,
        @RequestParam String tipoAuditoria,
        @RequestParam String fechaInicio,
        @RequestParam (required=false)String fechaFin,
        @RequestParam String estado
    ) {
        Empresa empresa = new Empresa();
        empresa.setNombre(nombreEmpresa);
        empresa.setNit(nit);
        empresa.setSector(sector);
        empresaRepository.save(empresa);
        
        Auditoria auditoria = new Auditoria();
        auditoria.setNombre(nombre);
        auditoria.setTipoAuditoria(tipoAuditoria);
        LocalDate inicio = LocalDate.parse(fechaInicio);
        auditoria.setFechaInico(inicio);

        if (fechaFin != null && !fechaFin.isEmpty()) {
            auditoria.setFechaFin(LocalDate.parse(fechaFin));
        } else {
            auditoria.setFechaFin(inicio);
        }

        auditoria.setEstado(estado);
        auditoria.setEmpresa(empresa);
        auditoriaRepository.save(auditoria);
        
        return "redirect:/proyectos";
    }
    
    @GetMapping("/{id}")
    public String detalleProyecto(@PathVariable Long id, Model model) {
        Auditoria auditoria = auditoriaRepository.findById(id).orElseThrow(() -> new RuntimeException("Auditoría no encontrada"));

        Empresa empresa = auditoria.getEmpresa();
        var departamentos = areaAuditableRepository.findByAuditoria(auditoria);
        var vulnerabilidades = departamentos.stream()
                .flatMap(departamento -> vulnerabilidadRepository.findByAreaAuditable(departamento).stream())
                .toList();
        var metricas = Map.of(
                "critico", contarRiesgo(vulnerabilidades, "Critico"),
                "alto", contarRiesgo(vulnerabilidades, "Alto"),
                "medio", contarRiesgo(vulnerabilidades, "Medio"),
                "bajo", contarRiesgo(vulnerabilidades, "Bajo")
        );
        var departamentosView = departamentos.stream()
                .map(departamento -> {
                    var vulnerabilidadesDepartamento = vulnerabilidadRepository.findByAreaAuditable(departamento);
                    return new DepartamentoView(
                            departamento.getId(),
                            departamento.getNombre(),
                            departamento.getDescripcion(),
                            vulnerabilidadesDepartamento.size(),
                            contarRiesgo(vulnerabilidadesDepartamento, "Critico"));
                })
                .toList();
        var proyecto = new ProyectoView(
                auditoria.getIdAuditoria(),
                auditoria.getNombre(),
                empresa.getNombre(),
                auditoria.getEstado(),
                auditoria.getFechaInico(),
                auditoria.getTipoAuditoria(),
                departamentosView,
                metricas);

        model.addAttribute("auditoria", auditoria);
        model.addAttribute("empresa", empresa);
        model.addAttribute("proyecto", proyecto);
        model.addAttribute("departamentos", departamentosView);
        model.addAttribute("areas", departamentos);
        model.addAttribute("metricas", metricas);
        return "detalleproyecto";
    }
    
    @GetMapping("/{id}/editar")
    public String proyectoEditar(@PathVariable Long id, Model model) {

        Auditoria auditoria = auditoriaRepository.findById(id).orElseThrow(() -> new RuntimeException("Auditoría no encontrada"));

        model.addAttribute("auditoria", auditoria);

        return "editar-auditoria";
    }

    @PostMapping("/{id}/actualizar")
    public String proyectoActualizar(
        @PathVariable Long id, 
        @RequestParam String nombreEmpresa,
        @RequestParam(required=false) String nit,
        @RequestParam(required = false) String sector,

        @RequestParam String nombre,
        @RequestParam String tipoAuditoria,
        @RequestParam String fechaInicio,
        @RequestParam (required=false)String fechaFin,
        @RequestParam String estado
    ) {
        Auditoria auditoria = auditoriaRepository.findById(id).orElseThrow(() -> new RuntimeException("Proyecto no encontrado"));

        Empresa empresa = auditoria.getEmpresa();

        empresa.setNombre(nombreEmpresa);
        empresa.setNit(nit);
        empresa.setSector(sector);
        empresaRepository.save(empresa);

        auditoria.setNombre(nombre);
        auditoria.setTipoAuditoria(tipoAuditoria);
        auditoria.setFechaInico(LocalDate.parse(fechaInicio));

        if (fechaFin != null && !fechaFin.isEmpty()) {
            auditoria.setFechaFin(LocalDate.parse(fechaFin));
        } else{
            auditoria.setFechaFin(auditoria.getFechaInico());
        }

        auditoria.setEstado(estado);

        auditoriaRepository.save(auditoria);

        return "redirect:/proyectos/" + id;
    }

    @PostMapping("/{id}/eliminar")
    @Transactional
    public String eliminarProyecto(@PathVariable Long id) {

        Auditoria auditoria = auditoriaRepository.findById(id).orElseThrow(() -> new RuntimeException("Proyecto no encontrado"));
        Empresa empresa = auditoria.getEmpresa();

        var departamentos = areaAuditableRepository.findByAuditoria(auditoria);

        for (var departamento : departamentos) {
            var vulnerabilidades = vulnerabilidadRepository.findByAreaAuditable(departamento);
            vulnerabilidadRepository.deleteAll(vulnerabilidades);
        }
        vulnerabilidadRepository.flush();

        areaAuditableRepository.deleteAll(departamentos);
        areaAuditableRepository.flush();

        auditoriaRepository.delete(auditoria);
        auditoriaRepository.flush();

        if (empresa != null && auditoriaRepository.countByEmpresa(empresa) == 0) {
            empresaRepository.delete(empresa);
        }

        return "redirect:/proyectos";
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

package com.hexora.hexora.controller;

import java.util.List;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hexora.hexora.model.Cve;
import com.hexora.hexora.service.CveService;

/**
 * Ventanilla de la app para buscar CVEs.
 */
@RestController
@RequestMapping("/api/cves")
@PreAuthorize("hasRole('AUDITOR')")
@Validated
public class CveController {

    private final CveService cveService;

    public CveController(CveService cveService) {
        this.cveService = cveService;
    }

    // Ejemplo: http://localhost:8080/api/cves/buscar?q=CVE-2021-44228
    @GetMapping("/buscar")
    public List<Cve> buscar(
            @RequestParam("q")
            @Size(min = 3, max = 100)
            @Pattern(regexp = "(?i)(?:CVE-\\d{4}-\\d{4,19}|[A-Za-z0-9][A-Za-z0-9 ._-]{2,99})")
            String q) {
        return cveService.buscar(q);
    }

    // Devuelve el CVE completo: con la descripción en español y los datos para rellenar
    // las casillas de riesgo. Lo que falte lo completa la primera vez que se pide.
    // Ejemplo: http://localhost:8080/api/cves/CVE-2021-44228/detalle
    @GetMapping("/{codigo}/detalle")
    public ResponseEntity<Cve> detalle(
            @PathVariable("codigo")
            @Pattern(regexp = "(?i)CVE-\\d{4}-\\d{4,19}")
            String codigo) {
        return cveService.obtenerDetalle(codigo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
package com.hexora.hexora.service;

import com.hexora.hexora.dto.ConteoHallazgos;
import com.hexora.hexora.dto.ConteoProyecto;
import com.hexora.hexora.dto.DashboardResumen;
import com.hexora.hexora.dto.DashboardResumen.HallazgoReciente;
import com.hexora.hexora.dto.DashboardResumen.Nivel;
import com.hexora.hexora.dto.DashboardResumen.ProyectoResumen;
import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.Vulnerabilidad;
import com.hexora.hexora.repository.VulnerabilidadRepository;
import com.hexora.hexora.riesgo.EscalaRiesgo;
import com.hexora.hexora.security.TinyHatPrincipal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calcula las cifras del panel. Parte siempre de {@link PermissionService#proyectosVisibles}: solo se cuenta lo que el
 * usuario puede ver (su cuenta y, para un auditor, los proyectos que tiene asignados). Ninguna consulta recibe un
 * identificador que venga del navegador.
 */
@Service
public class DashboardService {

    static final int MAX_RECIENTES = 6;

    /** Niveles en el orden en que se muestran: clave normalizada, nombre visible y clave de estilo. */
    private static final Map<String, String[]> NIVELES = new LinkedHashMap<>();

    static {
        // Del más grave al menos grave; los cinco niveles salen de la regla única de riesgo.
        for (EscalaRiesgo.NivelRiesgo nivel : EscalaRiesgo.NivelRiesgo.deMayorAMenor()) {
            NIVELES.put(normalizar(nivel.valorAlmacenado()), new String[] {nivel.etiqueta(), nivel.clave()});
        }
    }

    private static final Set<String> ESTADOS_ABIERTOS = Set.of("abierta", "en progreso");

    private final PermissionService permissionService;
    private final VulnerabilidadRepository vulnerabilidadRepository;

    public DashboardService(PermissionService permissionService, VulnerabilidadRepository vulnerabilidadRepository) {
        this.permissionService = permissionService;
        this.vulnerabilidadRepository = vulnerabilidadRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResumen resumen(TinyHatPrincipal principal) {
        List<Auditoria> proyectos = permissionService.proyectosVisibles(principal);
        if (proyectos.isEmpty()) {
            return DashboardResumen.vacio();
        }
        List<Long> proyectoIds = proyectos.stream().map(Auditoria::getIdAuditoria).toList();
        Long cuentaId = principal.getCuentaId();

        Map<String, Long> abiertosPorNivel = new LinkedHashMap<>();
        NIVELES.keySet().forEach(clave -> abiertosPorNivel.put(clave, 0L));
        long abiertos = 0;
        long mitigados = 0;
        long aceptados = 0;
        for (ConteoHallazgos conteo : vulnerabilidadRepository.contarPorRiesgoYEstado(cuentaId, proyectoIds)) {
            long total = conteo.total() == null ? 0 : conteo.total();
            String estado = normalizar(conteo.estado());
            if (ESTADOS_ABIERTOS.contains(estado)) {
                abiertos += total;
                abiertosPorNivel.merge(normalizar(conteo.nivelRiesgo()), total, Long::sum);
            } else if (estado.equals("mitigada")) {
                mitigados += total;
            } else if (estado.equals("aceptada")) {
                aceptados += total;
            }
        }

        List<Nivel> niveles = new ArrayList<>();
        for (Map.Entry<String, String[]> nivel : NIVELES.entrySet()) {
            long cantidad = abiertosPorNivel.getOrDefault(nivel.getKey(), 0L);
            niveles.add(new Nivel(nivel.getValue()[0], nivel.getValue()[1], cantidad, porcentaje(cantidad, abiertos)));
        }

        List<HallazgoReciente> recientes = vulnerabilidadRepository
            .recientes(cuentaId, proyectoIds, PageRequest.of(0, MAX_RECIENTES))
            .stream()
            .map(DashboardService::reciente)
            .toList();

        return new DashboardResumen(
            proyectos.size(), abiertos, mitigados, aceptados, List.copyOf(niveles), recientes,
            tarjetasDeProyecto(proyectos, vulnerabilidadRepository.contarPorProyecto(cuentaId, proyectoIds)));
    }

    /** Una tarjeta por proyecto visible: cuántos hallazgos abiertos tiene y cuál es su nivel más alto. */
    private static List<ProyectoResumen> tarjetasDeProyecto(List<Auditoria> proyectos, List<ConteoProyecto> conteos) {
        List<String> orden = new ArrayList<>(NIVELES.keySet());
        Map<Long, Long> abiertosPorProyecto = new LinkedHashMap<>();
        Map<Long, Integer> nivelMaximo = new LinkedHashMap<>();
        for (ConteoProyecto conteo : conteos) {
            long total = conteo.total() == null ? 0 : conteo.total();
            if (total == 0 || !ESTADOS_ABIERTOS.contains(normalizar(conteo.estado()))) {
                continue;
            }
            abiertosPorProyecto.merge(conteo.proyectoId(), total, Long::sum);
            int posicion = orden.indexOf(normalizar(conteo.nivelRiesgo()));
            if (posicion >= 0) {
                nivelMaximo.merge(conteo.proyectoId(), posicion, Math::min);
            }
        }
        List<ProyectoResumen> tarjetas = new ArrayList<>();
        for (Auditoria proyecto : proyectos) {
            Integer posicion = nivelMaximo.get(proyecto.getIdAuditoria());
            String[] nivel = posicion == null ? null : NIVELES.get(orden.get(posicion));
            tarjetas.add(new ProyectoResumen(
                proyecto.getPublicId(),
                proyecto.getNombre(),
                proyecto.getEmpresa() == null ? null : proyecto.getEmpresa().getNombre(),
                proyecto.getEstado(),
                abiertosPorProyecto.getOrDefault(proyecto.getIdAuditoria(), 0L),
                nivel == null ? null : nivel[0],
                nivel == null ? "sin-nivel" : nivel[1]));
        }
        return List.copyOf(tarjetas);
    }

    private static HallazgoReciente reciente(Vulnerabilidad hallazgo) {
        String clave = normalizar(hallazgo.getNivelRiesgo());
        String[] nivel = NIVELES.get(clave);
        return new HallazgoReciente(
            hallazgo.getNombre(),
            nivel == null ? hallazgo.getNivelRiesgo() : nivel[0],
            nivel == null ? "sin-nivel" : nivel[1],
            hallazgo.getEstado(),
            hallazgo.getAreaAuditable().getAuditoria().getNombre(),
            hallazgo.getAreaAuditable().getNombre(),
            hallazgo.getFechaDeteccion(),
            hallazgo.getAreaAuditable().getAuditoria().getPublicId(),
            hallazgo.getAreaAuditable().getPublicId(),
            hallazgo.getPublicId());
    }

    /** Ancho del segmento: de 1 a 100, y 0 si no hay hallazgos de ese nivel. */
    public static int porcentaje(long cantidad, long total) {
        if (cantidad <= 0 || total <= 0) {
            return 0;
        }
        return (int) Math.max(1, Math.min(100, Math.round(100.0 * cantidad / total)));
    }

    /** Minúsculas y sin tildes, para que "Crítico" y "Critico" cuenten como el mismo nivel. */
    static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    }
}

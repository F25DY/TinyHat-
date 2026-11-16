package com.hexora.hexora.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Lo que muestra el panel: cifras de los proyectos que el usuario puede ver y nada más.
 *
 * <p>Los hallazgos "abiertos" son los que están en estado Abierta o En Progreso. No existe una fecha de mitigación en
 * el modelo, por eso el panel muestra "mitigados" en total y no "mitigados este mes".
 */
public record DashboardResumen(
    int proyectos,
    long abiertos,
    long mitigados,
    long aceptados,
    List<Nivel> niveles,
    List<HallazgoReciente> recientes,
    List<ProyectoResumen> proyectosLista
) {

    /**
     * Una tarjeta de proyecto. {@code claveNivel} es el nivel más alto entre los hallazgos abiertos, o "sin-nivel" si no
     * hay ninguno abierto.
     */
    public record ProyectoResumen(
        String publicId,
        String nombre,
        String empresa,
        String estado,
        long abiertos,
        String nivel,
        String claveNivel
    ) {
    }

    /** Hallazgos abiertos de un nivel de riesgo. {@code porcentaje} (1 a 100) alimenta el ancho del segmento. */
    public record Nivel(String nombre, String clave, long cantidad, int porcentaje) {
    }

    /** Un hallazgo reciente con lo necesario para enlazarlo sin exponer identificadores numéricos. */
    public record HallazgoReciente(
        String nombre,
        String nivel,
        String claveNivel,
        String estado,
        String proyecto,
        String departamento,
        LocalDate fecha,
        String proyectoId,
        String departamentoId,
        String hallazgoId
    ) {
    }

    public static DashboardResumen vacio() {
        return new DashboardResumen(0, 0, 0, 0, List.of(), List.of(), List.of());
    }

    public boolean sinProyectos() {
        return proyectos == 0;
    }

    public boolean sinHallazgosAbiertos() {
        return proyectos > 0 && abiertos == 0;
    }
}

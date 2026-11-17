package com.hexora.hexora.informes.snapshot;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Contrato de datos de un informe (§4): la <b>única</b> entrada de los renderizadores. Es inmutable y se serializa a
 * JSON canónico (ver {@link SerializadorSnapshot}); su SHA-256 es la huella del informe.
 *
 * <p>Solo contiene campos que existen en la aplicación: descripción y alcance del proyecto, descripción, evidencia e
 * impacto textual del hallazgo y la justificación del Anexo A no existen y por eso no aparecen (decisión D5). Los
 * campos sin valor se omiten del JSON. Las fechas van en ISO-8601 con zona America/Bogota. Todo texto de usuario es
 * texto plano: los renderizadores deben escaparlo para su formato.
 */
public record InformeSnapshot(
    int schemaVersion,
    String plantillaVersion,
    String generadoEn,
    DatosInforme informe,
    DatosOrganizacion organizacion,
    DatosProyecto proyecto,
    DatosAuditor auditor,
    Metricas metricas,
    List<Area> areas,
    List<EntradaAnexoA> anexoA,
    List<EntradaTrazabilidad> trazabilidad,
    List<EntradaHistorial> historial,
    Map<String, String> narrativa,
    List<String> referenciasApa
) {

    public static final int VERSION_ESQUEMA = 1;

    public InformeSnapshot {
        areas = List.copyOf(areas);
        anexoA = List.copyOf(anexoA);
        trazabilidad = List.copyOf(trazabilidad);
        historial = List.copyOf(historial);
        narrativa = Map.copyOf(narrativa);
        referenciasApa = List.copyOf(referenciasApa);
    }

    /** Sin historial de versiones (para quien no lo necesita). */
    public InformeSnapshot(
        int schemaVersion, String plantillaVersion, String generadoEn, DatosInforme informe,
        DatosOrganizacion organizacion, DatosProyecto proyecto, DatosAuditor auditor, Metricas metricas,
        List<Area> areas, List<EntradaAnexoA> anexoA, List<EntradaTrazabilidad> trazabilidad,
        Map<String, String> narrativa, List<String> referenciasApa
    ) {
        this(schemaVersion, plantillaVersion, generadoEn, informe, organizacion, proyecto, auditor, metricas, areas,
            anexoA, trazabilidad, List.of(), narrativa, referenciasApa);
    }

    /** Tipo, número de versión, ID del documento (con sufijo -NP si no está publicado) y estado. */
    public record DatosInforme(String tipo, int version, String id, String estado) {
    }

    public record DatosOrganizacion(String nombre) {
    }

    /** Fechas en ISO-8601 (solo fecha). */
    public record DatosProyecto(String nombre, String fechaInicio, String fechaFin) {
    }

    /** Nombre de la cuenta auditora ({@code Cuenta.nombre}). */
    public record DatosAuditor(String nombre) {
    }

    /**
     * Todas las métricas se calculan al ensamblar, nunca en las plantillas. Solo cuentan los hallazgos del cuerpo; los
     * aceptados (Anexo A) se cuentan aparte. {@code mapaCalor[p-1][i-1]} es el número de hallazgos con esa probabilidad e
     * impacto (escala 1-5).
     */
    public record Metricas(
        Map<String, Integer> porSeveridad,
        List<FilaAreaSeveridad> porAreaSeveridad,
        Map<String, Integer> porEstado,
        List<List<Integer>> mapaCalor,
        int sinProbabilidad,
        int totalHallazgos,
        int totalAreas,
        int aceptados,
        int falsosPositivos
    ) {
        public Metricas {
            porSeveridad = Map.copyOf(porSeveridad);
            porAreaSeveridad = List.copyOf(porAreaSeveridad);
            porEstado = Map.copyOf(porEstado);
            mapaCalor = mapaCalor.stream().map(List::copyOf).toList();
        }
    }

    public record FilaAreaSeveridad(String area, Map<String, Integer> porSeveridad) {
        public FilaAreaSeveridad {
            porSeveridad = Map.copyOf(porSeveridad);
        }
    }

    public record Area(String nombre, String descripcion, List<Hallazgo> hallazgos) {
        public Area {
            hallazgos = List.copyOf(hallazgos);
        }
    }

    /**
     * Hallazgo del cuerpo. {@code cia} conserva los valores 0-10 de la aplicación; {@code probabilidad} e
     * {@code impacto15} están en la escala 1-5 de NIST; {@code riesgo} es nulo si no hay probabilidad.
     */
    public record Hallazgo(
        String codigo,
        String titulo,
        String mitigacion,
        String estado,
        String severidad,
        Cia cia,
        Integer probabilidad,
        int impacto15,
        Riesgo riesgo,
        List<Cve> cves,
        List<String> referencias,
        String faseSugerida
    ) {
        public Hallazgo {
            cves = List.copyOf(cves);
            referencias = List.copyOf(referencias);
        }

        /** Suma de las tres dimensiones, criterio de desempate en los órdenes de §4.3. */
        public int sumaCia() {
            return cia.c() + cia.i() + cia.a();
        }
    }

    public record Cia(int c, int i, int a) {
    }

    /** {@code puntaje} es probabilidad × impacto (1-25); {@code nivel} sale de la tabla de NIST. */
    public record Riesgo(int puntaje, String nivel) {
    }

    /** CVE tomado de la caché local; {@code cvss} y {@code publicado} faltan si el CVE no está en caché. */
    public record Cve(String id, BigDecimal cvss, String publicado, String url) {
    }

    /** Hallazgo con riesgo aceptado. No cuenta en las métricas principales. */
    public record EntradaAnexoA(String codigo, String titulo, String area, String severidad, String tipo) {
        public static final String ACEPTADO = "ACEPTADO";
        public static final String FALSO_POSITIVO = "FALSO_POSITIVO";
    }

    /**
     * Una versión de la serie del tipo, para el "Control del documento" (§6.3). Se captura al ensamblar: es parte del
     * contenido del documento y por eso va en el snapshot (desviación D25). Las fechas son ISO-8601 con zona Bogotá;
     * {@code publicadoEn} es nulo en un borrador, y {@code despublicadoEn} y {@code motivo} solo existen si se despublicó.
     */
    public record EntradaHistorial(int version, String estado, String publicadoEn, String despublicadoEn, String motivo) {
    }

    /** Informe vigente (publicado) de otro tipo del mismo proyecto, con el hash de cada archivo (solo FIN). */
    public record EntradaTrazabilidad(String id, String tipo, String formato, String sha256, String publicadoEn) {
    }
}

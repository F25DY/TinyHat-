package com.hexora.hexora.informes.render.pdf;

import java.util.List;
import java.util.Map;

/**
 * Todo lo que las plantillas del PDF necesitan, ya calculado y formateado. Las plantillas no calculan nada ni
 * pintan datos sin escapar: todo texto de usuario va con {@code th:text}. La única excepción son los campos
 * {@code html}, que ya salieron del saneado de Markdown (ver {@link Seccion}).
 */
public record VistaInforme(
    Documento doc,
    List<FilaHistorial> historial,
    Seccion avisoReferencia,
    Seccion avisoAlcance,
    Seccion avisoNoPublicado,
    Seccion avisoAnexoA,
    Seccion avisoFase,
    Seccion avisoOrdenMr,
    Map<String, Seccion> secciones,
    List<EntradaIndice> indice,
    Seccion escala,
    List<FilaMatrizNivel> matrizNiveles,
    Seccion glosario,
    List<FilaConteo> conteoSeveridad,
    int totalHallazgos,
    List<String> etiquetasSeveridad,
    List<FilaArea> areaSeveridad,
    List<FilaRiesgoPrincipal> riesgosPrincipales,
    List<FilaResumen> resumenHallazgos,
    List<AreaFichas> areasFichas,
    List<FilaMitigacion> mitigacion,
    List<FilaMatriz> registroMatriz,
    List<String> etiquetasImpacto,
    List<FilaCalor> mapaCalor,
    int sinProbabilidad,
    List<FilaNivel> conteoNiveles,
    List<FilaAnexoA> anexoA,
    List<FilaTraza> trazabilidad,
    List<Referencia> referencias
) {

    /** Datos del documento y rótulos fijos. */
    public record Documento(
        String codigoTipo, String tipoNombre, String id, int version, String estado, String fechaEmision,
        String fechaInicio, String fechaFin, String proyecto, String organizacion, String auditor,
        boolean noPublicado, String hashCompleto, String hashCorto, String plantillaVersion, String pie,
        String marcaAgua, String pendiente, int totalAreas, int aceptados
    ) {
    }

    public record FilaHistorial(int version, String estado, String publicado, String despublicado, String motivo) {
    }

    /**
     * Texto de Markdown ya saneado. {@code html} es un fragmento sin atributos con solo las etiquetas de la lista
     * blanca de {@code MarkdownSaneado}; es lo único que las plantillas insertan sin escapar, en un único punto
     * ({@code fragmentos/narrativa.html}). {@code pendiente} marca una sección obligatoria vacía en una copia NP.
     */
    public record Seccion(String titulo, String html, boolean presente, boolean pendiente) {
    }

    public record EntradaIndice(String texto, String ancla, int nivel) {
    }

    /** Texto con su clase de color (paleta de severidad y riesgo). */
    public record Celda(String texto, String clase) {
    }

    public record FilaMatrizNivel(String probabilidad, List<Celda> celdas) {
    }

    public record FilaConteo(String etiqueta, String clase, int cantidad, String porcentaje, int ancho) {
    }

    public record FilaNivel(String etiqueta, String clase, int cantidad) {
    }

    public record Segmento(String clase, int ancho, String etiqueta) {
    }

    public record FilaArea(String area, List<Integer> conteos, int total, List<Segmento> segmentos) {
    }

    public record FilaRiesgoPrincipal(
        int posicion, String titulo, String area, Celda severidad, Celda riesgo, String dimension
    ) {
    }

    public record FilaResumen(
        String codigo, String titulo, String area, Celda severidad, String probabilidad, Celda riesgo, String estado
    ) {
    }

    public record CveVista(String id, String cvss) {
    }

    public record Ficha(
        String codigo, String titulo, Celda severidad, String cia, String probabilidad, String impacto, Celda riesgo,
        List<CveVista> cves, String mitigacion, String estado, List<String> referencias, String fase
    ) {
    }

    public record AreaFichas(String nombre, String descripcion, String ancla, List<Ficha> fichas) {
    }

    public record FilaMitigacion(
        int prioridad, String codigo, String titulo, String area, Celda severidad, String fase, String accion,
        String estado
    ) {
    }

    public record FilaMatriz(
        String codigo, String area, String titulo, String c, String i, String a, Celda severidad, String probabilidad,
        String impacto, String puntaje, Celda riesgo, String clase, String cve, String cvss, String estado,
        String mitigacion
    ) {
    }

    public record CeldaCalor(int valor, String clase) {
    }

    public record FilaCalor(String probabilidad, List<CeldaCalor> celdas) {
    }

    public record FilaAnexoA(String codigo, String titulo, String area, Celda severidad, String tipo) {
    }

    public record FilaTraza(String id, String tipo, String formato, String sha256, String publicado) {
    }

    public record SegmentoTexto(String texto, boolean cursiva) {
    }

    public record Referencia(List<SegmentoTexto> segmentos) {
    }
}

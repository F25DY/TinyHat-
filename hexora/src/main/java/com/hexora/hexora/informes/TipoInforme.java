package com.hexora.hexora.informes;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Tipos de informe y los formatos válidos de cada uno. Es una lista blanca: la interfaz solo ofrece estas
 * combinaciones y el backend rechaza cualquier otra.
 *
 * <pre>
 *            PDF  DOCX  XLSX
 *   MR  Matriz de riesgo     si   no    si
 *   TEC Técnico              si   si    no
 *   EJE Ejecutivo            si   si    no
 *   MIT Mitigación           si   si    si
 *   FIN Final de entrega     si   si    no
 * </pre>
 *
 * El nombre de cada constante es el código que se guarda en {@code informe.tipo}.
 */
public enum TipoInforme {

    MR("Matriz de riesgo", EnumSet.of(FormatoInforme.PDF, FormatoInforme.XLSX),
        SeccionNarrativa.opcional("notas", "Notas")),
    TEC("Técnico", EnumSet.of(FormatoInforme.PDF, FormatoInforme.DOCX),
        SeccionNarrativa.obligatoria("alcanceMetodologia", "Alcance y metodología"), SeccionNarrativa.opcional("conclusiones", "Conclusiones")),
    EJE("Ejecutivo", EnumSet.of(FormatoInforme.PDF, FormatoInforme.DOCX),
        SeccionNarrativa.obligatoria("resumenEjecutivo", "Resumen ejecutivo"), SeccionNarrativa.opcional("recomendaciones", "Recomendaciones")),
    MIT("Mitigación", EnumSet.of(FormatoInforme.PDF, FormatoInforme.DOCX, FormatoInforme.XLSX),
        SeccionNarrativa.opcional("introduccion", "Introducción")),
    FIN("Final de entrega", EnumSet.of(FormatoInforme.PDF, FormatoInforme.DOCX),
        SeccionNarrativa.obligatoria("cartaEntrega", "Carta de entrega"), SeccionNarrativa.obligatoria("resumenEjecutivo", "Resumen ejecutivo"),
        SeccionNarrativa.obligatoria("alcanceMetodologia", "Alcance y metodología"), SeccionNarrativa.obligatoria("conclusiones", "Conclusiones"));

    /**
     * Sección de la narrativa (§6.3). Las obligatorias bloquean la publicación; el resto son opcionales.
     * {@code clave} es la que se guarda en {@code narrativa_json}; {@code etiqueta} es el nombre que ve el auditor.
     */
    public record SeccionNarrativa(String clave, String etiqueta, boolean obligatoria) {

        static SeccionNarrativa obligatoria(String clave, String etiqueta) {
            return new SeccionNarrativa(clave, etiqueta, true);
        }

        static SeccionNarrativa opcional(String clave, String etiqueta) {
            return new SeccionNarrativa(clave, etiqueta, false);
        }
    }

    private final String nombre;
    private final Set<FormatoInforme> formatos;
    private final List<SeccionNarrativa> secciones;

    TipoInforme(String nombre, Set<FormatoInforme> formatos, SeccionNarrativa... secciones) {
        this.nombre = nombre;
        this.formatos = Set.copyOf(formatos);
        this.secciones = List.of(secciones);
    }

    /** Secciones narrativas de este tipo, en el orden en que se muestran. */
    public List<SeccionNarrativa> secciones() {
        return secciones;
    }

    public boolean admiteSeccion(String clave) {
        return secciones.stream().anyMatch(seccion -> seccion.clave().equals(clave));
    }

    public String nombre() {
        return nombre;
    }

    /** Formatos válidos para este tipo (inmutable). */
    public Set<FormatoInforme> formatos() {
        return formatos;
    }

    public boolean admite(FormatoInforme formato) {
        return formato != null && formatos.contains(formato);
    }

    /**
     * Valida un formato pedido.
     *
     * @throws IllegalArgumentException si la combinación no es válida (el controlador la responde como 400 genérico)
     */
    public void requerirFormato(FormatoInforme formato) {
        if (!admite(formato)) {
            throw new IllegalArgumentException("Combinación de tipo y formato no válida");
        }
    }
}

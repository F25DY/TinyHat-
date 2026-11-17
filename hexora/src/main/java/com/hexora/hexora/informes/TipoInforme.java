package com.hexora.hexora.informes;

import java.util.EnumSet;
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

    MR("Matriz de riesgo", EnumSet.of(FormatoInforme.PDF, FormatoInforme.XLSX)),
    TEC("Técnico", EnumSet.of(FormatoInforme.PDF, FormatoInforme.DOCX)),
    EJE("Ejecutivo", EnumSet.of(FormatoInforme.PDF, FormatoInforme.DOCX)),
    MIT("Mitigación", EnumSet.of(FormatoInforme.PDF, FormatoInforme.DOCX, FormatoInforme.XLSX)),
    FIN("Final de entrega", EnumSet.of(FormatoInforme.PDF, FormatoInforme.DOCX));

    private final String nombre;
    private final Set<FormatoInforme> formatos;

    TipoInforme(String nombre, Set<FormatoInforme> formatos) {
        this.nombre = nombre;
        this.formatos = Set.copyOf(formatos);
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

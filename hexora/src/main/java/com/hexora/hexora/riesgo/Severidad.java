package com.hexora.hexora.riesgo;

/**
 * Severidad técnica de un hallazgo (§4.2): se deriva del impacto 1-5 y no depende de la probabilidad. El riesgo,
 * en cambio, combina probabilidad e impacto (ver {@link EscalaRiesgo}). Orden: CRITICA mayor que ALTA mayor que
 * MEDIA mayor que BAJA mayor que INFORMATIVA.
 */
public enum Severidad {

    INFORMATIVA("Informativa", Fase.F3),
    BAJA("Baja", Fase.F3),
    MEDIA("Media", Fase.F3),
    ALTA("Alta", Fase.F2),
    CRITICA("Crítica", Fase.F1);

    /** Fases de remediación sugeridas (§4.4). Siempre se rotulan como "sugerida": los plazos los acuerda el cliente. */
    public enum Fase {
        F1("F1 — Contención inmediata (0–7 días)"),
        F2("F2 — Corto plazo (8–30 días)"),
        F3("F3 — Endurecimiento (31–90 días)");

        private final String descripcion;

        Fase(String descripcion) {
            this.descripcion = descripcion;
        }

        public String descripcion() {
            return descripcion;
        }
    }

    private final String etiqueta;
    private final Fase fase;

    Severidad(String etiqueta, Fase fase) {
        this.etiqueta = etiqueta;
        this.fase = fase;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** Fase sugerida: Crítica F1; Alta F2; Media, Baja e Informativa F3. */
    public Fase faseSugerida() {
        return fase;
    }

    /** Posición en el orden de gravedad: 0 es la más grave. */
    public int posicionDeGravedad() {
        return values().length - 1 - ordinal();
    }

    /** Severidad a partir del impacto 1-5: 5 Crítica, 4 Alta, 3 Media, 2 Baja, 1 Informativa. */
    public static Severidad desdeImpacto(int impacto1a5) {
        if (impacto1a5 < EscalaRiesgo.MINIMO || impacto1a5 > EscalaRiesgo.MAXIMO) {
            throw new IllegalArgumentException("Valor fuera de la escala 1-5");
        }
        return values()[impacto1a5 - 1];
    }
}

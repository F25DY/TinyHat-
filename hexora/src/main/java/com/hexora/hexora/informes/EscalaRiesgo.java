package com.hexora.hexora.informes;

import java.util.List;

/**
 * Escala de calificación de los informes (§4.2 del plan de informes).
 *
 * <p>La aplicación guarda confidencialidad, integridad, disponibilidad y probabilidad en escala 0-10. Los
 * informes las presentan en escala 1-5 con las mismas bandas que usa la matriz de riesgo existente
 * (0-1, 2-3, 4-5, 6-7, 8-10). Funciones puras: sin acceso a base de datos ni a red.
 */
public final class EscalaRiesgo {

    public static final int MINIMO = 1;
    public static final int MAXIMO = 5;

    /** Etiquetas de probabilidad indexadas por banda 1-5. */
    private static final List<String> ETIQUETAS_PROBABILIDAD =
        List.of("Rara", "Improbable", "Posible", "Probable", "Casi segura");

    /**
     * Tabla de búsqueda del nivel de riesgo, {@code TABLA[probabilidad - 1][impacto - 1]}.
     * No se usan umbrales sobre el puntaje: el nivel sale de esta tabla.
     */
    private static final NivelRiesgo[][] TABLA = {
        // impacto:        1                  2                  3                  4                  5
        /* P1 Rara        */ {NivelRiesgo.BAJO, NivelRiesgo.BAJO, NivelRiesgo.BAJO, NivelRiesgo.MEDIO, NivelRiesgo.MEDIO},
        /* P2 Improbable  */ {NivelRiesgo.BAJO, NivelRiesgo.BAJO, NivelRiesgo.MEDIO, NivelRiesgo.MEDIO, NivelRiesgo.ALTO},
        /* P3 Posible     */ {NivelRiesgo.BAJO, NivelRiesgo.MEDIO, NivelRiesgo.MEDIO, NivelRiesgo.ALTO, NivelRiesgo.CRITICO},
        /* P4 Probable    */ {NivelRiesgo.BAJO, NivelRiesgo.MEDIO, NivelRiesgo.ALTO, NivelRiesgo.CRITICO, NivelRiesgo.CRITICO},
        /* P5 Casi segura */ {NivelRiesgo.MEDIO, NivelRiesgo.ALTO, NivelRiesgo.CRITICO, NivelRiesgo.CRITICO, NivelRiesgo.CRITICO},
    };

    private EscalaRiesgo() {
    }

    /** Nivel de riesgo con el nombre que se imprime en los informes. */
    public enum NivelRiesgo {
        BAJO("Bajo"),
        MEDIO("Medio"),
        ALTO("Alto"),
        CRITICO("Crítico");

        private final String etiqueta;

        NivelRiesgo(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String etiqueta() {
            return etiqueta;
        }
    }

    /** Convierte un valor 0-10 de la aplicación a la banda 1-5; los valores fuera de rango se acotan. */
    public static int banda(int valor0a10) {
        int acotado = Math.max(0, Math.min(10, valor0a10));
        return Math.min(MAXIMO, acotado / 2 + 1);
    }

    /** Probabilidad 1-5 a partir de la probabilidad 0-10 guardada. */
    public static int probabilidad(int probabilidad0a10) {
        return banda(probabilidad0a10);
    }

    /** Impacto 1-5: la dimensión CIA más alta, llevada a la banda 1-5. */
    public static int impacto(int confidencialidad, int integridad, int disponibilidad) {
        return banda(Math.max(confidencialidad, Math.max(integridad, disponibilidad)));
    }

    /** Etiqueta textual de una probabilidad 1-5 ("Rara" ... "Casi segura"). */
    public static String etiquetaProbabilidad(int probabilidad1a5) {
        requerirEscala(probabilidad1a5);
        return ETIQUETAS_PROBABILIDAD.get(probabilidad1a5 - 1);
    }

    /** Puntaje de riesgo: probabilidad × impacto (1-25). */
    public static int puntaje(int probabilidad1a5, int impacto1a5) {
        requerirEscala(probabilidad1a5);
        requerirEscala(impacto1a5);
        return probabilidad1a5 * impacto1a5;
    }

    /** Nivel de riesgo por búsqueda en la tabla de §4.2. */
    public static NivelRiesgo nivel(int probabilidad1a5, int impacto1a5) {
        requerirEscala(probabilidad1a5);
        requerirEscala(impacto1a5);
        return TABLA[probabilidad1a5 - 1][impacto1a5 - 1];
    }

    private static void requerirEscala(int valor) {
        if (valor < MINIMO || valor > MAXIMO) {
            throw new IllegalArgumentException("Valor fuera de la escala 1-5");
        }
    }
}

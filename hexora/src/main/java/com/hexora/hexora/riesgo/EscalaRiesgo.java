package com.hexora.hexora.riesgo;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Regla única de riesgo de TinyHat, alineada con NIST:
 * <ul>
 *   <li><b>Impacto:</b> regla del punto más alto de FIPS 199: la mayor de las tres dimensiones
 *       (confidencialidad, integridad, disponibilidad).</li>
 *   <li><b>Escalas:</b> cinco niveles (NIST SP 800-30 Rev. 1, Tablas G y H). Los valores 0-10 de la aplicación
 *       pasan a nivel con los cortes de NIST escalados a 0-10: 0 Muy bajo, 1-2 Bajo, 3-7 Medio, 8-9 Alto, 10 Muy alto
 *       (rangos 0-4, 5-20, 21-79, 80-95 y 96-100 sobre 100).</li>
 *   <li><b>Riesgo:</b> tabla de búsqueda probabilidad × impacto, la Tabla I-2 de SP 800-30 Rev. 1 (p. I-1).
 *       Cinco niveles de riesgo: Muy bajo, Bajo, Medio, Alto y Crítico (el "Very High" de NIST).</li>
 * </ul>
 * Funciones puras: sin base de datos ni red. La replican el formulario ({@code vulnerability-form.js}) y la migración
 * V31; las pruebas comprueban que las tres coinciden.
 */
public final class EscalaRiesgo {

    public static final int MINIMO = 1;
    public static final int MAXIMO = 5;

    /** Etiquetas de probabilidad indexadas por nivel 1-5 (escala de probabilidad de NIST, Tabla G-3). */
    private static final List<String> ETIQUETAS_PROBABILIDAD =
        List.of("Muy baja", "Baja", "Media", "Alta", "Muy alta");

    private static final NivelRiesgo MB = NivelRiesgo.MUY_BAJO;
    private static final NivelRiesgo B = NivelRiesgo.BAJO;
    private static final NivelRiesgo M = NivelRiesgo.MEDIO;
    private static final NivelRiesgo A = NivelRiesgo.ALTO;
    private static final NivelRiesgo C = NivelRiesgo.CRITICO;

    /**
     * NIST SP 800-30 Rev. 1, Tabla I-2: nivel de riesgo, {@code TABLA[probabilidad - 1][impacto - 1]}.
     * Filas: probabilidad Muy baja a Muy alta. Columnas: impacto Muy bajo a Muy alto.
     */
    private static final NivelRiesgo[][] TABLA = {
        /* Muy baja */ {MB, MB, MB, B, B},
        /* Baja     */ {MB, B, B, B, M},
        /* Media    */ {MB, B, M, M, A},
        /* Alta     */ {MB, B, M, A, C},
        /* Muy alta */ {MB, B, M, A, C},
    };

    private EscalaRiesgo() {
    }

    /** Nivel de riesgo, de menor a mayor. */
    public enum NivelRiesgo {
        MUY_BAJO("Muy bajo", "Muy bajo", "muy-bajo", "badge-azul"),
        BAJO("Bajo", "Bajo", "bajo", "badge-verde"),
        MEDIO("Medio", "Medio", "medio", "badge-amarillo"),
        ALTO("Alto", "Alto", "alto", "badge-naranja"),
        CRITICO("Crítico", "Critico", "critico", "badge-rojo");

        private final String etiqueta;
        private final String valorAlmacenado;
        private final String clave;
        private final String claseInsignia;

        NivelRiesgo(String etiqueta, String valorAlmacenado, String clave, String claseInsignia) {
            this.etiqueta = etiqueta;
            this.valorAlmacenado = valorAlmacenado;
            this.clave = clave;
            this.claseInsignia = claseInsignia;
        }

        /** Texto para personas (con tilde). */
        public String etiqueta() {
            return etiqueta;
        }

        /** Valor guardado en {@code vulnerabilidad.nivel_riesgo} (sin tilde, como lo usan dashboard y consultas). */
        public String valorAlmacenado() {
            return valorAlmacenado;
        }

        /** Clave de estilo (sufijo de las clases {@code riesgo-*}). */
        public String clave() {
            return clave;
        }

        /** Clase CSS de la insignia del nivel. */
        public String claseInsignia() {
            return claseInsignia;
        }

        /** Posición en el orden de criticidad: 0 es el más grave. */
        public int posicionDeCriticidad() {
            return values().length - 1 - ordinal();
        }

        /** Niveles del más grave al menos grave, en el orden en que se muestran. */
        public static List<NivelRiesgo> deMayorAMenor() {
            return List.of(CRITICO, ALTO, MEDIO, BAJO, MUY_BAJO);
        }

        /** Interpreta el valor guardado, sin distinguir mayúsculas ni tildes; {@code null} si no es un nivel. */
        public static NivelRiesgo desdeAlmacenado(String valor) {
            if (valor == null) {
                return null;
            }
            String normalizado = normalizar(valor);
            for (NivelRiesgo nivel : values()) {
                if (normalizar(nivel.valorAlmacenado).equals(normalizado)) {
                    return nivel;
                }
            }
            return null;
        }

        private static String normalizar(String texto) {
            return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Convierte un valor 0-10 de la aplicación al nivel 1-5 con los cortes de NIST; los valores fuera de rango se
     * acotan. 0 → 1, 1-2 → 2, 3-7 → 3, 8-9 → 4, 10 → 5.
     */
    public static int banda(int valor0a10) {
        int acotado = Math.max(0, Math.min(10, valor0a10));
        if (acotado == 0) {
            return 1;
        }
        if (acotado <= 2) {
            return 2;
        }
        if (acotado <= 7) {
            return 3;
        }
        return acotado <= 9 ? 4 : 5;
    }

    /** Probabilidad 1-5 a partir de la probabilidad 0-10 guardada. */
    public static int probabilidad(int probabilidad0a10) {
        return banda(probabilidad0a10);
    }

    /** Impacto 1-5: la dimensión CIA más alta (regla del punto más alto), llevada al nivel 1-5. */
    public static int impacto(int confidencialidad, int integridad, int disponibilidad) {
        return banda(impactoGuardado(confidencialidad, integridad, disponibilidad));
    }

    /**
     * Impacto que se guarda en {@code vulnerabilidad.impacto} (0-10): la dimensión CIA más alta. Es el mismo valor
     * del que sale el nivel 1-5, así lo que se muestra explica el nivel.
     */
    public static int impactoGuardado(int confidencialidad, int integridad, int disponibilidad) {
        int maximo = Math.max(confidencialidad, Math.max(integridad, disponibilidad));
        return Math.max(0, Math.min(10, maximo));
    }

    /** Etiqueta textual de una probabilidad 1-5 ("Muy baja" ... "Muy alta"). */
    public static String etiquetaProbabilidad(int probabilidad1a5) {
        requerirEscala(probabilidad1a5);
        return ETIQUETAS_PROBABILIDAD.get(probabilidad1a5 - 1);
    }

    /** Puntaje de riesgo: probabilidad × impacto (1-25). Sirve para ordenar; el nivel sale de la tabla. */
    public static int puntaje(int probabilidad1a5, int impacto1a5) {
        requerirEscala(probabilidad1a5);
        requerirEscala(impacto1a5);
        return probabilidad1a5 * impacto1a5;
    }

    /** Nivel de riesgo por búsqueda en la Tabla I-2 de NIST SP 800-30 Rev. 1. */
    public static NivelRiesgo nivel(int probabilidad1a5, int impacto1a5) {
        requerirEscala(probabilidad1a5);
        requerirEscala(impacto1a5);
        return TABLA[probabilidad1a5 - 1][impacto1a5 - 1];
    }

    /** Único cálculo del nivel de riesgo de una vulnerabilidad (servidor, formulario, migración e informes). */
    public static NivelRiesgo nivelDeVulnerabilidad(
        int confidencialidad, int integridad, int disponibilidad, int probabilidad0a10
    ) {
        return nivel(probabilidad(probabilidad0a10), impacto(confidencialidad, integridad, disponibilidad));
    }

    private static void requerirEscala(int valor) {
        if (valor < MINIMO || valor > MAXIMO) {
            throw new IllegalArgumentException("Valor fuera de la escala 1-5");
        }
    }
}

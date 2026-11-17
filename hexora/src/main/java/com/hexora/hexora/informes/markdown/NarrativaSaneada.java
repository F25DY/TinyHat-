package com.hexora.hexora.informes.markdown;

import java.util.List;

/**
 * Las tres salidas de una sección de la narrativa, obtenidas del mismo árbol analizado una sola vez.
 *
 * @param html        fragmento HTML ya saneado para el PDF. Es el <b>único</b> texto que se inserta en las plantillas
 *                    sin escapar (por eso se documenta y se prueba); todo lo demás va con {@code th:text}
 * @param textoPlano  para las celdas de XLSX
 * @param bloques     modelo para los párrafos y fragmentos de Word
 */
public record NarrativaSaneada(String html, String textoPlano, List<BloqueNarrativa> bloques) {

    public static final NarrativaSaneada VACIA = new NarrativaSaneada("", "", List.of());

    public NarrativaSaneada {
        bloques = List.copyOf(bloques);
    }

    public boolean estaVacia() {
        return textoPlano.isBlank();
    }
}

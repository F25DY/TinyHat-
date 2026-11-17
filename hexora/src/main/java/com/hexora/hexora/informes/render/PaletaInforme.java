package com.hexora.hexora.informes.render;

import java.util.Map;

/**
 * Colores de la paleta de severidad y riesgo (§6.2) para los formatos de oficina. Las clases son las mismas que usa el
 * PDF ({@code sev-*}, {@code niv-*}); el color nunca es el único portador de información, porque cada celda lleva su
 * etiqueta en texto.
 */
public final class PaletaInforme {

    /** Colores en hexadecimal RGB sin almohadilla. */
    public record Colores(String fondo, String texto) {
    }

    private static final Colores CRITICO = new Colores("7A0019", "FFFFFF");
    private static final Colores ALTO = new Colores("C62828", "FFFFFF");
    private static final Colores MEDIO = new Colores("F9A825", "000000");
    private static final Colores BAJO = new Colores("2E7D32", "FFFFFF");
    private static final Colores MUY_BAJO = new Colores("1565C0", "FFFFFF");
    private static final Colores SIN_DATO = new Colores("E0E0E0", "000000");

    private static final Map<String, Colores> POR_CLASE = Map.ofEntries(
        Map.entry("sev-critica", CRITICO), Map.entry("niv-critico", CRITICO),
        Map.entry("sev-alta", ALTO), Map.entry("niv-alto", ALTO),
        Map.entry("sev-media", MEDIO), Map.entry("niv-medio", MEDIO),
        Map.entry("sev-baja", BAJO), Map.entry("niv-bajo", BAJO),
        Map.entry("sev-informativa", MUY_BAJO), Map.entry("niv-muy-bajo", MUY_BAJO),
        Map.entry("sev-sin", SIN_DATO));

    private PaletaInforme() {
    }

    /** Colores de una clase; una clase desconocida usa el gris de "sin dato". */
    public static Colores de(String clase) {
        return POR_CLASE.getOrDefault(clase, SIN_DATO);
    }

    public static Colores sinDato() {
        return SIN_DATO;
    }
}

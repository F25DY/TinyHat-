package com.hexora.hexora.informes.render;

/** Limpieza de texto de usuario antes de escribirlo en un documento XML (PDF, DOCX) o en una celda (XLSX). */
public final class TextoSeguro {

    /** Máximo de caracteres que admite una celda de Excel. */
    public static final int MAXIMO_CELDA = 32_767;

    private TextoSeguro() {
    }

    /**
     * Elimina lo que no es XML 1.0 válido (caracteres de control y suplentes sueltos), que corrompería el documento.
     * Nunca devuelve {@code null}.
     */
    public static String limpiarXml(String texto) {
        if (texto == null) {
            return "";
        }
        StringBuilder limpio = new StringBuilder(texto.length());
        texto.codePoints()
            .filter(cp -> cp == 0x9 || cp == 0xA || cp == 0xD || (cp >= 0x20 && cp <= 0xD7FF)
                || (cp >= 0xE000 && cp <= 0xFFFD) || (cp >= 0x10000 && cp <= 0x10FFFF))
            .forEach(limpio::appendCodePoint);
        return limpio.toString();
    }

    /** Texto apto para una celda: sin caracteres inválidos y dentro del límite de Excel. */
    public static String paraCelda(String texto) {
        String limpio = limpiarXml(texto);
        return limpio.length() <= MAXIMO_CELDA ? limpio : limpio.substring(0, MAXIMO_CELDA - 1) + "…";
    }
}

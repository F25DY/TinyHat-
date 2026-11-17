package com.hexora.hexora.informes.render.pdf;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Segunda barrera (defensa en profundidad) antes de generar el PDF: recorre el XHTML ya producido y comprueba que
 * todo recurso que declara sea un ancla interna o una ruta de {@code classpath:informes/}. Revisa los atributos de
 * recurso (src, href, data, poster, background, action...) y el CSS que contiene el documento (bloques
 * {@code <style>} y atributos {@code style}: {@code url(...)} y {@code @import}). El texto corriente no se revisa: si
 * un auditor escribe {@code url(http://x)} en un título, sale escapado como prosa y no es un recurso.
 *
 * <p>Los textos de usuario salen escapados y no pueden crear etiquetas ni atributos, así que en un documento
 * correcto esto nunca se activa; si una plantilla futura dejara pasar un recurso externo, el documento se rechaza
 * aquí en lugar de depender solo del resolvedor.
 */
final class VerificadorRecursosHtml {

    private static final Pattern ATRIBUTO = Pattern.compile(
        "\\b(?:src|href|data|poster|background|action|formaction|xlink:href)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_CSS = Pattern.compile("url\\(\\s*(['\"]?)([^)'\"]*)\\1\\s*\\)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern IMPORT_CSS = Pattern.compile("@import\\s+(?:url\\()?\\s*['\"]?([^'\")\\s;]+)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOQUE_ESTILO = Pattern.compile("<style\\b[^>]*>(.*?)</style>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATRIBUTO_ESTILO = Pattern.compile("\\bstyle\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')",
        Pattern.CASE_INSENSITIVE);

    private VerificadorRecursosHtml() {
    }

    /** @throws IllegalStateException si el documento declara un recurso que no es de {@code classpath:informes/} */
    static void verificar(String xhtml) {
        Matcher atributo = ATRIBUTO.matcher(xhtml);
        while (atributo.find()) {
            exigirPermitido(atributo.group(1) != null ? atributo.group(1) : atributo.group(2));
        }
        Matcher bloque = BLOQUE_ESTILO.matcher(xhtml);
        while (bloque.find()) {
            verificarCss(bloque.group(1));
        }
        Matcher estilo = ATRIBUTO_ESTILO.matcher(xhtml);
        while (estilo.find()) {
            verificarCss(estilo.group(1) != null ? estilo.group(1) : estilo.group(2));
        }
    }

    private static void verificarCss(String css) {
        Matcher url = URL_CSS.matcher(css);
        while (url.find()) {
            exigirPermitido(url.group(2));
        }
        Matcher importacion = IMPORT_CSS.matcher(css);
        while (importacion.find()) {
            exigirPermitido(importacion.group(1));
        }
    }

    private static void exigirPermitido(String uri) {
        String limpio = uri == null ? "" : uri.strip();
        if (limpio.isEmpty() || limpio.startsWith("#")) {
            return;
        }
        if (!limpio.startsWith(ResolvedorRecursosSeguro.ESQUEMA)
            || !ResolvedorRecursosSeguro.rutaValida(limpio.substring(ResolvedorRecursosSeguro.ESQUEMA.length()))) {
            throw new IllegalStateException("La plantilla declara un recurso no permitido");
        }
    }
}

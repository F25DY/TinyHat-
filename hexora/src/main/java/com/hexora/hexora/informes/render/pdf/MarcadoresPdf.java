package com.hexora.hexora.informes.render.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Marcadores (bookmarks) del PDF. OpenHTMLtoPDF los toma de un elemento {@code <bookmarks>} en el {@code <head>}; aquí
 * se arma a partir de los títulos de nivel 1 y 2 del XHTML ya producido (el nivel 2 queda anidado en el nivel 1). Los
 * títulos de la narrativa nunca son de nivel 1 o 2 (el saneado los baja a 3), así que los marcadores solo salen de
 * las plantillas. El texto ya viene escapado por Thymeleaf, de modo que se reutiliza tal cual dentro del atributo.
 */
final class MarcadoresPdf {

    private static final Pattern TITULO = Pattern.compile("<h([12])((?:\\s[^>]*)?)>(.*?)</h\\1>", Pattern.DOTALL);
    private static final Pattern ID = Pattern.compile("\\bid=\"([^\"]+)\"");
    private static final Pattern ETIQUETA = Pattern.compile("<[^>]+>");

    private MarcadoresPdf() {
    }

    private record Titulo(int nivel, String texto, String ancla) {
    }

    static String agregar(String xhtml) {
        List<Titulo> titulos = new ArrayList<>();
        StringBuilder resultado = new StringBuilder(xhtml.length() + 2048);
        Matcher coincidencia = TITULO.matcher(xhtml);
        int ultimo = 0;
        int contador = 0;
        while (coincidencia.find()) {
            int nivel = Integer.parseInt(coincidencia.group(1));
            String atributos = coincidencia.group(2);
            String texto = ETIQUETA.matcher(coincidencia.group(3)).replaceAll("").replaceAll("\\s+", " ").strip();
            Matcher id = ID.matcher(atributos);
            String ancla;
            String etiquetaAbierta;
            if (id.find()) {
                ancla = id.group(1);
                etiquetaAbierta = "<h" + nivel + atributos + ">";
            } else {
                ancla = "marcador-" + (++contador);
                etiquetaAbierta = "<h" + nivel + atributos + " id=\"" + ancla + "\">";
            }
            resultado.append(xhtml, ultimo, coincidencia.start()).append(etiquetaAbierta).append(coincidencia.group(3))
                .append("</h").append(nivel).append('>');
            ultimo = coincidencia.end();
            if (!texto.isEmpty()) {
                titulos.add(new Titulo(nivel, texto, ancla));
            }
        }
        resultado.append(xhtml, ultimo, xhtml.length());
        if (titulos.isEmpty()) {
            return resultado.toString();
        }
        return insertarEnCabecera(resultado.toString(), bloque(titulos));
    }

    private static String bloque(List<Titulo> titulos) {
        StringBuilder xml = new StringBuilder("<bookmarks>");
        boolean abierto = false;
        for (Titulo titulo : titulos) {
            if (titulo.nivel() == 1) {
                if (abierto) {
                    xml.append("</bookmark>");
                }
                xml.append("<bookmark name=\"").append(titulo.texto()).append("\" href=\"#").append(titulo.ancla())
                    .append("\">");
                abierto = true;
            } else {
                xml.append("<bookmark name=\"").append(titulo.texto()).append("\" href=\"#").append(titulo.ancla())
                    .append("\"/>");
            }
        }
        if (abierto) {
            xml.append("</bookmark>");
        }
        return xml.append("</bookmarks>").toString();
    }

    private static String insertarEnCabecera(String xhtml, String marcadores) {
        int fin = xhtml.indexOf("</head>");
        return fin < 0 ? xhtml : xhtml.substring(0, fin) + marcadores + xhtml.substring(fin);
    }
}

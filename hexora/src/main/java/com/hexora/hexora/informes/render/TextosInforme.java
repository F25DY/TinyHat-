package com.hexora.hexora.informes.render;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Textos fijos de los informes, versionados junto con las plantillas en {@code informes/textos/}: avisos (§6.4),
 * escala de calificación y glosario. Son Markdown limitado; quien los usa los pasa por el mismo saneado que la
 * narrativa.
 */
@Component
public class TextosInforme {

    private static final String DIRECTORIO = "informes/textos/";

    private final Map<String, String> avisos;
    private final String escalaCalificacion;
    private final String glosario;

    public TextosInforme() {
        this.avisos = cargarSecciones(leer("avisos.md"));
        this.escalaCalificacion = leer("escala-calificacion.md");
        this.glosario = leer("glosario.md");
    }

    /** Texto fijo por su clave (referencia, alcance, no-publicado, orden-mr, anexo-a, fase-sugerida, cia-c/i/a). */
    public String aviso(String clave) {
        String texto = avisos.get(clave);
        if (texto == null) {
            throw new IllegalArgumentException("Texto fijo desconocido: " + clave);
        }
        return texto;
    }

    /** Recuadro del Anexo A (§6.4-e) con los conteos. */
    public String avisoAnexoA(int aceptados, int falsosPositivos) {
        return aviso("anexo-a").replace("{N}", Integer.toString(aceptados))
            .replace("{M}", Integer.toString(falsosPositivos));
    }

    public String escalaCalificacion() {
        return escalaCalificacion;
    }

    public String glosario() {
        return glosario;
    }

    private static String leer(String archivo) {
        try (InputStream entrada = new ClassPathResource(DIRECTORIO + archivo).getInputStream()) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException excepcion) {
            throw new IllegalStateException("No se pudo leer " + DIRECTORIO + archivo, excepcion);
        }
    }

    /** Secciones "## clave" con su texto; las líneas que empiezan por "# " son comentarios. */
    private static Map<String, String> cargarSecciones(String contenido) {
        Map<String, String> secciones = new LinkedHashMap<>();
        String clave = null;
        StringBuilder texto = new StringBuilder();
        for (String linea : contenido.split("\\R")) {
            if (linea.startsWith("## ")) {
                guardar(secciones, clave, texto);
                clave = linea.substring(3).strip();
                texto = new StringBuilder();
            } else if (clave != null) {
                texto.append(linea).append('\n');
            }
        }
        guardar(secciones, clave, texto);
        return Map.copyOf(secciones);
    }

    private static void guardar(Map<String, String> secciones, String clave, StringBuilder texto) {
        if (clave != null) {
            secciones.put(clave, texto.toString().strip());
        }
    }
}

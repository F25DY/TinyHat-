package com.hexora.hexora.informes.render.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Convierte XHTML en PDF con OpenHTMLtoPDF, solo con recursos del classpath:
 * <ul>
 *   <li>Las fuentes (Plus Jakarta Sans y JetBrains Mono, TTF estáticos con su licencia OFL) se cargan con
 *       {@code useFont} desde {@code informes/fuentes/}.</li>
 *   <li>Todo recurso pasa por el {@link ResolvedorRecursosSeguro}; los protocolos de red y de archivos quedan
 *       conectados a una fábrica que no entrega nada.</li>
 * </ul>
 */
public final class GeneradorPdf {

    /** URI base de las plantillas; los recursos relativos se resuelven contra ella, siempre dentro de informes/. */
    public static final String URI_BASE = ResolvedorRecursosSeguro.ESQUEMA + "informes/pdf/";

    private static final String DIRECTORIO_FUENTES = "informes/fuentes/";

    private record Fuente(String archivo, String familia, int peso, FontStyle estilo) {
    }

    private static final List<Fuente> FUENTES = List.of(
        new Fuente("PlusJakartaSans-Regular.ttf", "Plus Jakarta Sans", 400, FontStyle.NORMAL),
        new Fuente("PlusJakartaSans-Bold.ttf", "Plus Jakarta Sans", 700, FontStyle.NORMAL),
        new Fuente("PlusJakartaSans-Italic.ttf", "Plus Jakarta Sans", 400, FontStyle.ITALIC),
        new Fuente("PlusJakartaSans-BoldItalic.ttf", "Plus Jakarta Sans", 700, FontStyle.ITALIC),
        new Fuente("JetBrainsMono-Regular.ttf", "JetBrains Mono", 400, FontStyle.NORMAL),
        new Fuente("JetBrainsMono-Bold.ttf", "JetBrains Mono", 700, FontStyle.NORMAL));

    /** Referencia fuerte: si el registro se recolecta, se pierde el nivel configurado. */
    private static final Logger REGISTRO_LIBRERIA = Logger.getLogger("com.openhtmltopdf");

    static {
        // La librería escribe en java.util.logging a nivel INFO cada recurso que carga o rechaza; los rechazos los
        // registra el resolvedor con SLF4J, así que aquí solo interesan las advertencias reales.
        REGISTRO_LIBRERIA.setLevel(Level.WARNING);
    }

    private GeneradorPdf() {
    }

    /**
     * Genera el PDF. Un fallo del renderizado se informa con un mensaje genérico: el detalle queda en la causa para el
     * registro con correlationId, nunca para el usuario.
     */
    public static void generar(String xhtml, ResolvedorRecursosSeguro resolvedor, OutputStream salida)
        throws IOException {
        try {
            PdfRendererBuilder constructor = new PdfRendererBuilder();
            constructor.useUriResolver(resolvedor);
            constructor.useProtocolsStreamImplementation(resolvedor, ResolvedorRecursosSeguro.ESQUEMA.replace(":", ""));
            ResolvedorRecursosSeguro.FabricaRechazada rechazada = new ResolvedorRecursosSeguro.FabricaRechazada(resolvedor);
            constructor.useHttpStreamImplementation(rechazada);
            constructor.useProtocolsStreamImplementation(rechazada, "http", "https", "file", "ftp", "jar");
            for (Fuente fuente : FUENTES) {
                constructor.useFont(() -> abrirFuente(fuente.archivo()), fuente.familia(), fuente.peso(),
                    fuente.estilo(), true);
            }
            constructor.withProducer("TinyHat");
            constructor.withHtmlContent(xhtml, URI_BASE);
            constructor.toStream(salida);
            constructor.run();
        } catch (IOException | RuntimeException excepcion) {
            throw new IOException("No se pudo generar el PDF", excepcion);
        }
    }

    private static InputStream abrirFuente(String archivo) {
        InputStream flujo = GeneradorPdf.class.getClassLoader().getResourceAsStream(DIRECTORIO_FUENTES + archivo);
        if (flujo == null) {
            throw new IllegalStateException("Falta la fuente " + archivo);
        }
        return flujo;
    }
}

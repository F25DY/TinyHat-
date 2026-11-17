package com.hexora.hexora.informes.render.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.markdown.MarkdownSaneado;
import com.hexora.hexora.informes.render.DatosDemo;
import com.hexora.hexora.informes.render.OpcionesRender;
import com.hexora.hexora.informes.render.TextosInforme;
import com.hexora.hexora.informes.snapshot.InformeSnapshot;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

/**
 * Genera una muestra de cada tipo con datos ficticios. Con {@code -Dinformes.muestras.dir=RUTA} guarda los PDF; con
 * {@code -Dinformes.muestras.png=RUTA} guarda además cada página como imagen para revisarla.
 */
class MuestrasPdfTest {

    static RenderizadorPdf renderizador() {
        return new RenderizadorPdf(new ConstructorVistaPdf(new MarkdownSaneado(20000), new TextosInforme()),
            new MotorPlantillasPdf());
    }

    static byte[] renderizar(InformeSnapshot snapshot, boolean noPublicado) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        renderizador().render(snapshot, new OpcionesRender(noPublicado), salida);
        return salida.toByteArray();
    }

    @Test
    void generaUnaMuestraDeCadaTipoPublicadaYNoPublicada() throws Exception {
        String directorio = System.getProperty("informes.muestras.dir");
        String png = System.getProperty("informes.muestras.png");
        for (TipoInforme tipo : TipoInforme.values()) {
            for (boolean np : new boolean[] {false, true}) {
                byte[] pdf = renderizar(DatosDemo.snapshot(tipo, np), np);
                assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
                String nombre = DatosDemo.snapshot(tipo, np).informe().id();
                if (directorio != null) {
                    Files.createDirectories(Path.of(directorio));
                    Files.write(Path.of(directorio, nombre + ".pdf"), pdf);
                }
                if (png != null) {
                    guardarPaginas(pdf, Path.of(png), nombre);
                }
            }
        }
    }

    static void guardarPaginas(byte[] pdf, Path carpeta, String nombre) throws IOException {
        Files.createDirectories(carpeta);
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            PDFRenderer pintor = new PDFRenderer(documento);
            for (int p = 0; p < documento.getNumberOfPages(); p++) {
                BufferedImage imagen = pintor.renderImageWithDPI(p, 70);
                ImageIO.write(imagen, "png", carpeta.resolve(nombre + "-p" + (p + 1) + ".png").toFile());
            }
        }
    }
}

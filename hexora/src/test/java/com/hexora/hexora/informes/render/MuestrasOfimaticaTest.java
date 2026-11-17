package com.hexora.hexora.informes.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexora.hexora.informes.FormatoInforme;
import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.markdown.MarkdownSaneado;
import com.hexora.hexora.informes.render.docx.RenderizadorDocx;
import com.hexora.hexora.informes.render.pdf.ConstructorVistaPdf;
import com.hexora.hexora.informes.render.pdf.MotorPlantillasPdf;
import com.hexora.hexora.informes.render.pdf.RenderizadorPdf;
import com.hexora.hexora.informes.render.xlsx.RenderizadorXlsx;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Registro de renderizadores con los tres formatos y muestras de DOCX y XLSX con datos ficticios. Con
 * {@code -Dinformes.muestras.dir=RUTA} guarda los archivos para abrirlos en Word, Excel o LibreOffice.
 */
class MuestrasOfimaticaTest {

    private static RegistroRenderers registro() {
        MarkdownSaneado markdown = new MarkdownSaneado(20000);
        TextosInforme textos = new TextosInforme();
        ConstructorVistaPdf vistas = new ConstructorVistaPdf(markdown, textos);
        return new RegistroRenderers(List.of(new RenderizadorPdf(vistas, new MotorPlantillasPdf()),
            new RenderizadorDocx(vistas, markdown, textos), new RenderizadorXlsx(vistas, markdown, textos)));
    }

    @Test
    void elRegistroResuelveCadaCombinacionValidaYRechazaElRestoConElMismoError() {
        RegistroRenderers registro = registro();
        int validas = 0;
        for (TipoInforme tipo : TipoInforme.values()) {
            for (FormatoInforme formato : FormatoInforme.values()) {
                if (tipo.admite(formato)) {
                    assertThat(registro.resolver(tipo, formato).formato()).isEqualTo(formato);
                    validas++;
                } else {
                    assertThatThrownBy(() -> registro.resolver(tipo, formato)).isInstanceOf(IllegalArgumentException.class);
                }
            }
        }
        assertThat(validas).as("§1.2: 11 combinaciones").isEqualTo(11);
    }

    @Test
    void generaUnaMuestraDeCadaCombinacionDeDocxYXlsx() throws Exception {
        String directorio = System.getProperty("informes.muestras.dir");
        RegistroRenderers registro = registro();
        for (TipoInforme tipo : TipoInforme.values()) {
            for (FormatoInforme formato : List.of(FormatoInforme.DOCX, FormatoInforme.XLSX)) {
                if (!tipo.admite(formato)) {
                    continue;
                }
                for (boolean np : new boolean[] {false, true}) {
                    var snapshot = DatosDemo.snapshot(tipo, np);
                    ByteArrayOutputStream salida = new ByteArrayOutputStream();
                    registro.resolver(tipo, formato).render(snapshot, new OpcionesRender(np), salida);
                    byte[] bytes = salida.toByteArray();
                    assertThat(bytes).isNotEmpty();
                    assertThat(new String(bytes, 0, 2, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("PK");
                    OfimaticaPrueba.verificarContenedorSinRiesgos(bytes);
                    if (directorio != null) {
                        Files.createDirectories(Path.of(directorio));
                        Files.write(Path.of(directorio, snapshot.informe().id() + "." + formato.name().toLowerCase()), bytes);
                    }
                }
            }
        }
    }
}

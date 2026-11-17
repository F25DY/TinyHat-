package com.hexora.hexora.informes.render.docx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.junit.jupiter.api.Test;

/**
 * La plantilla base del DOCX: solo estilos, sin contenido, sin macros ni relaciones externas. Con
 * {@code -Dinformes.base-docx=RUTA} la regenera (la ruta es {@code src/main/resources/informes/docx/base.docx}).
 */
class BaseDocxTest {

    @Test
    void regeneraLaBaseSiSePide() throws IOException {
        String destino = System.getProperty("informes.base-docx");
        if (destino != null) {
            Files.createDirectories(Path.of(destino).getParent());
            Files.write(Path.of(destino), GeneradorBaseDocx.generar());
        }
    }

    @Test
    void laBaseTieneLosEstilosDelPlan() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(ConstructorDocx.BASE)) {
            assertThat(in).as("informes/docx/base.docx en el classpath").isNotNull();
            try (XWPFDocument base = new XWPFDocument(in)) {
                List<String> ids = new ArrayList<>();
                for (XWPFStyle estilo : base.getStyles().getStyles()) {
                    ids.add(estilo.getStyleId());
                }
                assertThat(ids).contains("Heading1", "Heading2", "Heading3", "Heading4", "Tabla", "Nota", "Codigo",
                    "Encabezado", "Pie", "Leyenda", "Referencia", "Aviso", "AvisoNP", "Ficha");
                assertThat(base.getParagraphs().stream().map(p -> p.getText()).filter(t -> !t.isBlank()).toList())
                    .as("la base no trae texto").isEmpty();
            }
        }
    }

    @Test
    void laBaseNoTraeMacrosNiRelacionesExternas() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(ConstructorDocx.BASE);
             ZipInputStream zip = new ZipInputStream(in)) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                assertThat(e.getName()).doesNotContain("vbaProject").doesNotContain("embeddings");
                if (e.getName().endsWith(".rels")) {
                    assertThat(new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                        .doesNotContain("TargetMode=\"External\"");
                }
            }
        }
    }
}

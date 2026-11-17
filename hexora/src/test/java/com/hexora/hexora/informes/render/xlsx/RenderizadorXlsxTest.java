package com.hexora.hexora.informes.render.xlsx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexora.hexora.informes.FormatoInforme;
import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.markdown.MarkdownSaneado;
import com.hexora.hexora.informes.render.DatosDemo;
import com.hexora.hexora.informes.render.OfimaticaPrueba;
import com.hexora.hexora.informes.render.OpcionesRender;
import com.hexora.hexora.informes.render.TextosInforme;
import com.hexora.hexora.informes.render.pdf.ConstructorVistaPdf;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.FuenteVulnerabilidad;
import com.hexora.hexora.informes.snapshot.InformeSnapshot;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class RenderizadorXlsxTest {

    static RenderizadorXlsx renderizador() {
        MarkdownSaneado markdown = new MarkdownSaneado(20000);
        TextosInforme textos = new TextosInforme();
        return new RenderizadorXlsx(new ConstructorVistaPdf(markdown, textos), markdown, textos);
    }

    static byte[] renderizar(InformeSnapshot snapshot, boolean noPublicado) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        renderizador().render(snapshot, new OpcionesRender(noPublicado), salida);
        return salida.toByteArray();
    }

    static XSSFWorkbook abrir(byte[] xlsx) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx));
    }

    private static String hex(XSSFColor color) {
        byte[] rgb = color.getRGB();
        return String.format("%02X%02X%02X", rgb[0], rgb[1], rgb[2]);
    }

    private static String texto(Cell celda) {
        return celda == null ? "" : celda.getStringCellValue();
    }

    // ---------------------------------------------------------------- estructura de MR

    @Test
    void laMatrizDeRiesgoTieneLasCincoHojasEnOrden() throws Exception {
        try (XSSFWorkbook libro = abrir(renderizar(DatosDemo.snapshot(TipoInforme.MR, false), false))) {
            List<String> nombres = new ArrayList<>();
            libro.forEach(h -> nombres.add(h.getSheetName()));
            assertThat(nombres).containsExactly("Resumen", "Mapa de calor", "Registro", "Anexo A", "Control");
        }
    }

    @Test
    void elRegistroTieneEncabezadoFijoAutofiltroYColumnasDelClienteVacias() throws Exception {
        InformeSnapshot snapshot = DatosDemo.snapshot(TipoInforme.MR, false);
        try (XSSFWorkbook libro = abrir(renderizar(snapshot, false))) {
            XSSFSheet registro = libro.getSheet("Registro");
            assertThat(registro.getPaneInformation()).as("primera fila fija").isNotNull();
            assertThat(registro.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 1);
            assertThat(registro.getCTWorksheet().isSetAutoFilter()).isTrue();
            assertThat(registro.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A1:R1");
            Row cabecera = registro.getRow(0);
            assertThat(texto(cabecera.getCell(0))).isEqualTo("Código");
            assertThat(texto(cabecera.getCell(15))).isEqualTo("Activo");
            assertThat(texto(cabecera.getCell(16))).isEqualTo("Responsable");
            assertThat(texto(cabecera.getCell(17))).isEqualTo("Plan de mitigación (cliente)");
            int filas = registro.getLastRowNum();
            assertThat(filas).isEqualTo(snapshot.metricas().totalHallazgos());
            for (int f = 1; f <= filas; f++) {
                for (int c = 15; c <= 17; c++) {
                    Cell celda = registro.getRow(f).getCell(c);
                    assertThat(celda.getCellType()).as("fila " + f + " col " + c).isEqualTo(CellType.BLANK);
                }
            }
        }
    }

    @Test
    void cadaFilaDelRegistroVaColoreadaPorSuNivelDeRiesgoYConSuEtiqueta() throws Exception {
        Set<String> fondosVistos = new HashSet<>();
        try (XSSFWorkbook libro = abrir(renderizar(DatosDemo.snapshot(TipoInforme.MR, false), false))) {
            XSSFSheet registro = libro.getSheet("Registro");
            java.util.Map<String, String> esperado = java.util.Map.of("Crítico", "7A0019", "Alto", "C62828",
                "Medio", "F9A825", "Bajo", "2E7D32", "Muy bajo", "1565C0");
            for (int f = 1; f <= registro.getLastRowNum(); f++) {
                Row fila = registro.getRow(f);
                String nivel = texto(fila.getCell(10));
                assertThat(esperado).as("nivel de la fila " + f).containsKey(nivel);
                String fondo = hex(((XSSFCellStyle) fila.getCell(0).getCellStyle()).getFillForegroundXSSFColor());
                assertThat(fondo).isEqualTo(esperado.get(nivel));
                assertThat(hex(((XSSFCellStyle) fila.getCell(14).getCellStyle()).getFillForegroundXSSFColor()))
                    .as("toda la fila lleva el mismo color").isEqualTo(esperado.get(nivel));
                fondosVistos.add(fondo);
            }
        }
        assertThat(fondosVistos.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void elRegistroSigueElOrdenPorRiesgoDelSnapshot() throws Exception {
        InformeSnapshot snapshot = DatosDemo.snapshot(TipoInforme.MR, false);
        try (XSSFWorkbook libro = abrir(renderizar(snapshot, false))) {
            Sheet registro = libro.getSheet("Registro");
            List<Integer> puntajes = new ArrayList<>();
            for (int f = 1; f <= registro.getLastRowNum(); f++) {
                String p = texto(registro.getRow(f).getCell(9));
                puntajes.add("—".equals(p) ? -1 : Integer.parseInt(p));
            }
            assertThat(puntajes).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        }
    }

    @Test
    void elResumenTieneBarrasDeDatosYElMapaDeCalorLosConteos() throws Exception {
        InformeSnapshot snapshot = DatosDemo.snapshot(TipoInforme.MR, false);
        try (XSSFWorkbook libro = abrir(renderizar(snapshot, false))) {
            XSSFSheet resumen = libro.getSheet("Resumen");
            assertThat(resumen.getCTWorksheet().getConditionalFormattingList()).as("barras de datos").isNotEmpty();
            assertThat(resumen.getCTWorksheet().getConditionalFormattingList().get(0).getCfRuleList().get(0).isSetDataBar())
                .isTrue();
            XSSFSheet mapa = libro.getSheet("Mapa de calor");
            int suma = 0;
            for (int f = 3; f <= 7; f++) {
                for (int c = 1; c <= 5; c++) {
                    suma += (int) mapa.getRow(f).getCell(c).getNumericCellValue();
                }
            }
            assertThat(suma).isEqualTo(snapshot.metricas().totalHallazgos() - snapshot.metricas().sinProbabilidad());
        }
    }

    @Test
    void elAnexoAYElControlTraenLosDatos() throws Exception {
        InformeSnapshot snapshot = DatosDemo.snapshot(TipoInforme.MR, true);
        try (XSSFWorkbook libro = abrir(renderizar(snapshot, true))) {
            Sheet anexo = libro.getSheet("Anexo A");
            assertThat(anexo.getLastRowNum()).isGreaterThanOrEqualTo(snapshot.anexoA().size());
            assertThat(texto(anexo.getRow(1).getCell(0))).startsWith("H-");
            Sheet control = libro.getSheet("Control");
            StringBuilder todo = new StringBuilder();
            control.forEach(r -> r.forEach(c -> todo.append(c.getStringCellValue()).append('\n')));
            assertThat(todo.toString()).contains(snapshot.informe().id()).contains("SHA-256").contains("Historial de versiones")
                .contains("DOCUMENTO NO PUBLICADO").contains("Comercializadora Andina");
        }
    }

    // ---------------------------------------------------------------- MIT

    @Test
    void laMitigacionTieneElPlanConColumnasDelClienteYElControl() throws Exception {
        InformeSnapshot snapshot = DatosDemo.snapshot(TipoInforme.MIT, false);
        try (XSSFWorkbook libro = abrir(renderizar(snapshot, false))) {
            List<String> nombres = new ArrayList<>();
            libro.forEach(h -> nombres.add(h.getSheetName()));
            assertThat(nombres).containsExactly("Plan de mitigación", "Control");
            XSSFSheet plan = libro.getSheet("Plan de mitigación");
            assertThat(plan.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 1);
            assertThat(plan.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A1:K1");
            assertThat(texto(plan.getRow(0).getCell(8))).isEqualTo("Activo");
            assertThat(texto(plan.getRow(0).getCell(10))).isEqualTo("Plan de mitigación (cliente)");
            assertThat(plan.getLastRowNum()).isEqualTo(snapshot.metricas().totalHallazgos());
            // Prioridad ascendente y severidad coloreada con su etiqueta.
            for (int f = 1; f <= plan.getLastRowNum(); f++) {
                assertThat((int) plan.getRow(f).getCell(0).getNumericCellValue()).isEqualTo(f);
                assertThat(plan.getRow(f).getCell(8).getCellType()).isEqualTo(CellType.BLANK);
            }
            String primera = texto(plan.getRow(1).getCell(4));
            assertThat(primera).isEqualTo("Crítica");
            assertThat(hex(((XSSFCellStyle) plan.getRow(1).getCell(4).getCellStyle()).getFillForegroundXSSFColor()))
                .isEqualTo("7A0019");
        }
    }

    // ---------------------------------------------------------------- seguridad (§7.3)

    @Test
    void lasCeldasConFormulasDeUsuarioQuedanComoTextoYNoHayNingunaFormula() throws Exception {
        for (TipoInforme tipo : List.of(TipoInforme.MR, TipoInforme.MIT)) {
            byte[] xlsx = renderizar(OfimaticaPrueba.snapshotConFormulas(tipo, false), false);
            Set<String> encontradas = new HashSet<>();
            try (XSSFWorkbook libro = abrir(xlsx)) {
                for (Sheet hoja : libro) {
                    for (Row fila : hoja) {
                        for (Cell celda : fila) {
                            assertThat(celda.getCellType()).as(hoja.getSheetName() + "!" + celda.getAddress())
                                .isNotEqualTo(CellType.FORMULA).isNotEqualTo(CellType.ERROR);
                            if (celda.getCellType() == CellType.STRING
                                && OfimaticaPrueba.FORMULAS.contains(celda.getStringCellValue())) {
                                encontradas.add(celda.getStringCellValue());
                            }
                        }
                    }
                }
            }
            assertThat(encontradas).as(tipo.name() + ": los textos peligrosos están, pero como STRING")
                .contains("-1+1", "@SUM(1+1)", "+cmd|' /C calc'!A0",
                    "=HYPERLINK(\"http://169.254.169.254/latest/meta-data/\",\"clic\")");
            OfimaticaPrueba.verificarContenedorSinRiesgos(xlsx);
            // Ninguna cadena compartida se declara como fórmula ni hay hipervínculos.
            OfimaticaPrueba.textos(xlsx).forEach((nombre, xml) -> assertThat(xml).as(nombre).doesNotContain("<hyperlinks"));
        }
    }

    @Test
    void elTextoLargoYLosCaracteresDeControlNoCorrompenElLibro() throws Exception {
        byte[] xlsx = renderizar(OfimaticaPrueba.snapshotConFormulas(TipoInforme.MR, false), false);
        try (XSSFWorkbook libro = abrir(xlsx)) {
            Sheet registro = libro.getSheet("Registro");
            boolean largo = false;
            boolean control = false;
            for (Row fila : registro) {
                for (Cell celda : fila) {
                    if (celda.getCellType() == CellType.STRING) {
                        String valor = celda.getStringCellValue();
                        assertThat(valor.length()).isLessThanOrEqualTo(32_767);
                        assertThat(valor).doesNotContain("\u0000").doesNotContain("\u0007");
                        largo |= valor.startsWith("xxxx") && valor.endsWith("…");
                        control |= valor.startsWith("Control ");
                    }
                }
            }
            assertThat(largo).as("el texto de 40 000 caracteres se recorta al máximo de Excel").isTrue();
            assertThat(control).isTrue();
        }
    }

    @Test
    void losEstilosSeReutilizanYNoCrecenConLasFilas() throws Exception {
        List<FuenteVulnerabilidad> mil = new ArrayList<>();
        var areas = List.of(DatosDemo.RED, DatosDemo.WEB, DatosDemo.IDENTIDAD);
        for (int n = 0; n < 1000; n++) {
            mil.add(DatosDemo.v(String.format("%06x", 0x100000 + n), areas.get(n % 3), "Hallazgo número " + n,
                n % 11, (n / 3) % 11, (n / 7) % 11, (n / 5) % 11, "Abierta", "Mitigación " + n, null, null));
        }
        InformeSnapshot pocos = DatosDemo.snapshot(TipoInforme.MR, false);
        InformeSnapshot muchos = DatosDemo.snapshotConVulnerabilidades(TipoInforme.MR, mil);
        long inicio = System.nanoTime();
        byte[] xlsx = renderizar(muchos, false);
        long segundos = (System.nanoTime() - inicio) / 1_000_000_000L;
        assertThat(segundos).as("1000 hallazgos").isLessThan(60);
        int estilosConPocos;
        try (XSSFWorkbook libro = abrir(renderizar(pocos, false))) {
            estilosConPocos = libro.getNumCellStyles();
        }
        try (XSSFWorkbook libro = abrir(xlsx)) {
            assertThat(libro.getSheet("Registro").getLastRowNum()).isEqualTo(1000);
            assertThat(libro.getNumCellStyles()).as("un estilo por clase de la paleta, no por fila")
                .isLessThanOrEqualTo(Math.max(estilosConPocos, 40) + 5).isLessThan(64);
        }
    }

    @Test
    void sinEstilosEnFilasNiAnchosAutomaticos() throws Exception {
        try (XSSFWorkbook libro = abrir(renderizar(DatosDemo.snapshot(TipoInforme.MR, false), false))) {
            XSSFSheet registro = libro.getSheet("Registro");
            assertThat(registro.getColumnWidth(14)).isEqualTo(50 * 256);
            assertThat(registro.getColumnWidth(0)).isEqualTo(12 * 256);
        }
    }

    // ---------------------------------------------------------------- NP y compatibilidad

    @Test
    void laCopiaNoPublicadaMarcaElResumenYElEncabezadoDeImpresion() throws Exception {
        try (XSSFWorkbook libro = abrir(renderizar(DatosDemo.snapshot(TipoInforme.MR, true), true))) {
            XSSFSheet resumen = libro.getSheet("Resumen");
            assertThat(texto(resumen.getRow(1).getCell(0))).contains("DOCUMENTO NO PUBLICADO");
            assertThat(resumen.getHeader().getCenter()).contains("DOCUMENTO NO PUBLICADO");
        }
        try (XSSFWorkbook libro = abrir(renderizar(DatosDemo.snapshot(TipoInforme.MR, false), false))) {
            assertThat(libro.getSheet("Resumen").getHeader().getCenter()).isEmpty();
        }
    }

    @Test
    void proyectoSinHallazgosSeRenderiza() throws Exception {
        for (TipoInforme tipo : List.of(TipoInforme.MR, TipoInforme.MIT)) {
            try (XSSFWorkbook libro = abrir(renderizar(DatosDemo.snapshotSinHallazgos(tipo), false))) {
                assertThat(libro.getNumberOfSheets()).isGreaterThanOrEqualTo(2);
            }
        }
    }

    @Test
    void soloSoportaMrYMit() {
        RenderizadorXlsx r = renderizador();
        assertThat(r.formato()).isEqualTo(FormatoInforme.XLSX);
        for (TipoInforme tipo : TipoInforme.values()) {
            assertThat(r.soporta(tipo)).as(tipo.name()).isEqualTo(tipo == TipoInforme.MR || tipo == TipoInforme.MIT);
        }
        assertThatThrownBy(() -> r.render(DatosDemo.snapshot(TipoInforme.TEC, false), new OpcionesRender(false),
            new ByteArrayOutputStream())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elFlujoDeSalidaNoSeCierra() throws Exception {
        boolean[] cerrado = {false};
        ByteArrayOutputStream salida = new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                cerrado[0] = true;
                super.close();
            }
        };
        renderizador().render(DatosDemo.snapshot(TipoInforme.MIT, false), new OpcionesRender(false), salida);
        assertThat(cerrado[0]).as("quien llama es dueño del flujo").isFalse();
        assertThat(salida.size()).isPositive();
    }
}

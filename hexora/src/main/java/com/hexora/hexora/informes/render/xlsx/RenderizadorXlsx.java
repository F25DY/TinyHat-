package com.hexora.hexora.informes.render.xlsx;

import com.hexora.hexora.informes.FormatoInforme;
import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.markdown.MarkdownSaneado;
import com.hexora.hexora.informes.render.InformeRenderer;
import com.hexora.hexora.informes.render.OpcionesRender;
import com.hexora.hexora.informes.render.PaletaInforme;
import com.hexora.hexora.informes.render.PaletaInforme.Colores;
import com.hexora.hexora.informes.render.TextoSeguro;
import com.hexora.hexora.informes.render.TextosInforme;
import com.hexora.hexora.informes.render.pdf.ConstructorVistaInforme;
import com.hexora.hexora.informes.render.pdf.VistaInforme;
import com.hexora.hexora.informes.snapshot.InformeSnapshot;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ConditionalFormattingRule;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.SheetConditionalFormatting;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.springframework.stereotype.Component;

/**
 * Renderizador XLSX (Matriz de riesgo y Mitigación) con SXSSF y una ventana acotada de filas. Reglas de seguridad
 * (§7.3): todo texto de usuario entra con {@code setCellValue(String)} (celda de tipo STRING, nunca fórmula); no hay
 * fórmulas, macros, hipervínculos ni conexiones; los estilos se reutilizan (uno por nivel de la paleta) y los anchos de
 * columna son fijos. Las cifras salen de la misma vista que usa el PDF.
 */
@Component
public class RenderizadorXlsx implements InformeRenderer {

    /** Filas que SXSSF conserva en memoria antes de volcarlas al disco. */
    static final int VENTANA = 100;
    private static final String SANS = "Plus Jakarta Sans";
    private static final String[] COLUMNAS_REGISTRO = {"Código", "Área", "Hallazgo", "C", "I", "A", "Severidad",
        "Probabilidad", "Impacto", "Puntaje", "Nivel de riesgo", "CVE", "CVSS", "Estado", "Mitigación recomendada",
        "Activo", "Responsable", "Plan de mitigación (cliente)"};
    private static final int[] ANCHO_REGISTRO = {12, 22, 38, 5, 5, 5, 13, 16, 14, 8, 14, 18, 8, 14, 50, 20, 20, 36};
    private static final String[] COLUMNAS_PLAN = {"Prioridad", "Código", "Hallazgo", "Área", "Severidad",
        "Fase sugerida", "Acción de mitigación", "Estado", "Activo", "Responsable", "Plan de mitigación (cliente)"};
    private static final int[] ANCHO_PLAN = {10, 12, 38, 22, 13, 30, 50, 14, 20, 20, 36};

    private final ConstructorVistaInforme vistas;
    private final MarkdownSaneado markdown;
    private final TextosInforme textos;

    public RenderizadorXlsx(ConstructorVistaInforme vistas, MarkdownSaneado markdown, TextosInforme textos) {
        this.vistas = vistas;
        this.markdown = markdown;
        this.textos = textos;
    }

    @Override
    public FormatoInforme formato() {
        return FormatoInforme.XLSX;
    }

    @Override
    public boolean soporta(TipoInforme tipo) {
        return tipo.admite(FormatoInforme.XLSX);
    }

    @Override
    public void render(InformeSnapshot snapshot, OpcionesRender opciones, OutputStream salida) throws IOException {
        TipoInforme tipo = TipoInforme.valueOf(snapshot.informe().tipo());
        tipo.requerirFormato(FormatoInforme.XLSX);
        VistaInforme v = vistas.construir(snapshot, opciones);
        // close() borra los temporales de SXSSF (el método dispose() está obsoleto en POI 5.5.1).
        try (SXSSFWorkbook libro = new SXSSFWorkbook(VENTANA)) {
            libro.setCompressTempFiles(true);
            Libro l = new Libro(libro, v, snapshot);
            switch (tipo) {
                case MR -> l.matrizDeRiesgo();
                case MIT -> l.mitigacion();
                default -> throw new IllegalArgumentException("No hay renderizador XLSX para ese tipo");
            }
            libro.write(new FilterOutputStream(salida) {
                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    out.write(b, off, len);
                }

                @Override
                public void close() throws IOException {
                    flush(); // el que llama es dueño del flujo
                }
            });
        }
    }

    /** Estado de un renderizado: libro, estilos reutilizados, vista y snapshot. */
    private final class Libro {

        private final SXSSFWorkbook libro;
        private final VistaInforme v;
        private final InformeSnapshot s;
        private final Map<String, CellStyle> estilos = new HashMap<>();

        Libro(SXSSFWorkbook libro, VistaInforme v, InformeSnapshot s) {
            this.libro = libro;
            this.v = v;
            this.s = s;
        }

        // ------------------------------------------------------------ estilos (uno por clase de la paleta)

        private XSSFFont fuente(boolean negrita, boolean cursiva, int puntos, String colorHex) {
            XSSFFont f = (XSSFFont) libro.createFont();
            f.setFontName(SANS);
            f.setBold(negrita);
            f.setItalic(cursiva);
            f.setFontHeightInPoints((short) puntos);
            if (colorHex != null) {
                f.setColor(color(colorHex));
            }
            return f;
        }

        private XSSFColor color(String hex) {
            return new XSSFColor(new byte[] {(byte) Integer.parseInt(hex.substring(0, 2), 16),
                (byte) Integer.parseInt(hex.substring(2, 4), 16), (byte) Integer.parseInt(hex.substring(4, 6), 16)}, null);
        }

        private CellStyle estilo(String clave, boolean negrita, boolean cursiva, int puntos, String texto, String fondo,
            HorizontalAlignment horizontal, boolean borde) {
            return estilos.computeIfAbsent(clave, k -> {
                XSSFCellStyle e = (XSSFCellStyle) libro.createCellStyle();
                e.setFont(fuente(negrita, cursiva, puntos, texto));
                e.setAlignment(horizontal);
                e.setVerticalAlignment(VerticalAlignment.TOP);
                e.setWrapText(true);
                if (fondo != null) {
                    e.setFillForegroundColor(color(fondo));
                    e.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                }
                if (borde) {
                    e.setBorderBottom(BorderStyle.THIN);
                    e.setBorderTop(BorderStyle.THIN);
                    e.setBorderLeft(BorderStyle.THIN);
                    e.setBorderRight(BorderStyle.THIN);
                    e.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
                    e.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
                    e.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
                    e.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
                }
                return e;
            });
        }

        CellStyle titulo() {
            return estilo("titulo", true, false, 16, "0F2A43", null, HorizontalAlignment.LEFT, false);
        }

        CellStyle subtitulo() {
            return estilo("subtitulo", true, false, 12, "0F2A43", null, HorizontalAlignment.LEFT, false);
        }

        CellStyle texto() {
            return estilo("texto", false, false, 10, null, null, HorizontalAlignment.LEFT, true);
        }

        CellStyle centro() {
            return estilo("centro", false, false, 10, null, null, HorizontalAlignment.CENTER, true);
        }

        CellStyle etiqueta() {
            return estilo("etiqueta", true, false, 10, null, "F2F4F7", HorizontalAlignment.LEFT, true);
        }

        CellStyle nota() {
            return estilo("nota", false, true, 9, "444444", null, HorizontalAlignment.LEFT, false);
        }

        CellStyle aviso() {
            return estilo("aviso", true, false, 11, "C62828", "FDECEA", HorizontalAlignment.LEFT, false);
        }

        CellStyle cabecera() {
            return estilo("cabecera", true, false, 10, "FFFFFF", "0F2A43", HorizontalAlignment.CENTER, true);
        }

        CellStyle cabeceraCliente() {
            return estilo("cabecera-cliente", true, false, 10, "000000", "F9E79F", HorizontalAlignment.CENTER, true);
        }

        /** Celda coloreada con la paleta (una sola por clase). {@code centrada} para valores cortos. */
        CellStyle paleta(String clase, boolean centrada) {
            Colores c = PaletaInforme.de(clase);
            return estilo("paleta:" + clase + (centrada ? ":c" : ":t"), true, false, 10, c.texto(), c.fondo(),
                centrada ? HorizontalAlignment.CENTER : HorizontalAlignment.LEFT, true);
        }

        /** Fila entera coloreada por nivel de riesgo (texto de la celda siempre presente). */
        CellStyle fila(String clase, boolean centrada) {
            Colores c = PaletaInforme.de(clase);
            return estilo("fila:" + clase + (centrada ? ":c" : ":t"), false, false, 10, c.texto(), c.fondo(),
                centrada ? HorizontalAlignment.CENTER : HorizontalAlignment.LEFT, true);
        }

        // ------------------------------------------------------------ celdas

        /** Todo texto de usuario entra aquí: celda STRING, sin fórmulas posibles. */
        void texto(Row fila, int columna, String valor, CellStyle estilo) {
            Cell celda = fila.createCell(columna);
            celda.setCellValue(TextoSeguro.paraCelda(valor));
            celda.setCellStyle(estilo);
        }

        void numero(Row fila, int columna, int valor, CellStyle estilo) {
            Cell celda = fila.createCell(columna);
            celda.setCellValue(valor);
            celda.setCellStyle(estilo);
        }

        void vacia(Row fila, int columna, CellStyle estilo) {
            fila.createCell(columna).setCellStyle(estilo);
        }

        SXSSFSheet hoja(String nombre, int[] anchos, boolean horizontal) {
            SXSSFSheet hoja = libro.createSheet(nombre);
            for (int i = 0; i < anchos.length; i++) {
                hoja.setColumnWidth(i, anchos[i] * 256);
            }
            hoja.getPrintSetup().setLandscape(horizontal);
            hoja.setFitToPage(true);
            hoja.getPrintSetup().setFitWidth((short) 1);
            hoja.getPrintSetup().setFitHeight((short) 0);
            hoja.getPrintSetup().setPaperSize((short) 1);
            hoja.getFooter().setCenter(TextoSeguro.limpiarXml(v.doc().pie()));
            hoja.getFooter().setRight("Página &P de &N");
            if (v.doc().noPublicado()) {
                hoja.getHeader().setCenter("DOCUMENTO NO PUBLICADO");
            }
            return hoja;
        }

        /** Filas de texto sueltas (título, avisos); devuelve el siguiente índice de fila. */
        int bloqueTexto(SXSSFSheet hoja, int fila, String valor, CellStyle estilo, int columnas) {
            Row r = hoja.createRow(fila);
            texto(r, 0, valor, estilo);
            if (columnas > 1) {
                hoja.addMergedRegion(new CellRangeAddress(fila, fila, 0, columnas - 1));
            }
            altura(r, hoja, valor, 0, columnas);
            return fila + 1;
        }

        /** Excel no ajusta solo la altura de celdas combinadas: se estima por la longitud del texto y el ancho. */
        void altura(Row fila, SXSSFSheet hoja, String valor, int desde, int hasta) {
            int ancho = 0;
            for (int c = desde; c < hasta; c++) {
                ancho += hoja.getColumnWidth(c) / 256;
            }
            int lineas = 0;
            for (String linea : TextoSeguro.limpiarXml(valor).split("\n", -1)) {
                lineas += Math.max(1, (int) Math.ceil(linea.length() / Math.max(1.0, ancho * 0.95)));
            }
            if (lineas > 1) {
                fila.setHeightInPoints(Math.min(400, 14.5f * lineas));
            }
        }

        // ------------------------------------------------------------ Matriz de riesgo

        void matrizDeRiesgo() {
            resumen();
            mapaCalor();
            registro();
            anexoA();
            control();
        }

        private void resumen() {
            SXSSFSheet h = hoja("Resumen", new int[] {30, 16, 16, 40}, false);
            int f = 0;
            f = bloqueTexto(h, f, "Matriz de riesgo · " + v.doc().proyecto(), titulo(), 4);
            if (v.doc().noPublicado()) {
                f = bloqueTexto(h, f, textos.aviso("no-publicado"), aviso(), 4);
            }
            f++;
            f = parClaveValor(h, f, "Organización", v.doc().organizacion());
            f = parClaveValor(h, f, "Auditor", v.doc().auditor());
            f = parClaveValor(h, f, "Fecha de emisión", v.doc().fechaEmision());
            f = parClaveValor(h, f, "Identificador", v.doc().id());
            f = parClaveValor(h, f, "Versión", Integer.toString(v.doc().version()));
            f = parClaveValor(h, f, "Hallazgos", v.totalHallazgos() + " en " + v.doc().totalAreas() + " área(s)");
            f++;
            f = bloqueTexto(h, f, "Hallazgos por nivel de riesgo", subtitulo(), 4);
            Row cab = h.createRow(f++);
            texto(cab, 0, "Nivel de riesgo", cabecera());
            texto(cab, 1, "Hallazgos", cabecera());
            int inicio = f;
            for (VistaInforme.FilaNivel n : v.conteoNiveles()) {
                Row r = h.createRow(f++);
                texto(r, 0, n.etiqueta(), paleta(n.clase(), true));
                numero(r, 1, n.cantidad(), centro());
            }
            barrasDeDatos(h, inicio, f - 1, 1);
            f++;
            f = bloqueTexto(h, f, "Hallazgos por severidad", subtitulo(), 4);
            Row cab2 = h.createRow(f++);
            texto(cab2, 0, "Severidad", cabecera());
            texto(cab2, 1, "Hallazgos", cabecera());
            texto(cab2, 2, "Proporción", cabecera());
            int inicio2 = f;
            for (VistaInforme.FilaConteo c : v.conteoSeveridad()) {
                Row r = h.createRow(f++);
                texto(r, 0, c.etiqueta(), paleta(c.clase(), true));
                numero(r, 1, c.cantidad(), centro());
                texto(r, 2, c.porcentaje(), centro());
            }
            barrasDeDatos(h, inicio2, f - 1, 1);
            f++;
            f = bloqueTexto(h, f, v.postura(), nota(), 4);
            f++;
            f = bloqueTexto(h, f, "Hoja de ruta sugerida por fases", subtitulo(), 4);
            Row cab3 = h.createRow(f++);
            texto(cab3, 0, "Fase", cabecera());
            texto(cab3, 1, "Hallazgos", cabecera());
            texto(cab3, 2, "Qué incluye", cabecera());
            for (VistaInforme.FilaFase fase : v.fases()) {
                Row r = h.createRow(f++);
                texto(r, 0, fase.nombre(), paleta(fase.clase(), true));
                numero(r, 1, fase.cantidad(), centro());
                texto(r, 2, fase.descripcion(), texto());
            }
            f++;
            f = bloqueTexto(h, f, "Avance de la remediación", subtitulo(), 4);
            Row cab4 = h.createRow(f++);
            texto(cab4, 0, "Estado", cabecera());
            texto(cab4, 1, "Hallazgos", cabecera());
            texto(cab4, 2, "Proporción", cabecera());
            for (VistaInforme.FilaEstado estado : v.avance()) {
                Row r = h.createRow(f++);
                texto(r, 0, estado.estado(), etiqueta());
                numero(r, 1, estado.cantidad(), centro());
                texto(r, 2, estado.porcentaje(), centro());
            }
            f++;
            if (v.sinProbabilidad() > 0) {
                f = bloqueTexto(h, f, "Hallazgos sin probabilidad: " + v.sinProbabilidad() + ".", nota(), 4);
            }
            if (v.avisoAnexoA() != null) {
                f = bloqueTexto(h, f, plano(textos.avisoAnexoA(s.metricas().aceptados(), s.metricas().falsosPositivos())),
                    nota(), 4);
            }
            f = bloqueTexto(h, f, plano(textos.aviso("orden-mr")), nota(), 4);
            String notas = plano(s.narrativa().get("notas"));
            if (!notas.isBlank()) {
                f++;
                f = bloqueTexto(h, f, "Notas", subtitulo(), 4);
                bloqueTexto(h, f, notas, texto(), 4);
            }
        }

        private int parClaveValor(SXSSFSheet h, int fila, String clave, String valor) {
            Row r = h.createRow(fila);
            texto(r, 0, clave, etiqueta());
            texto(r, 1, valor, texto());
            h.addMergedRegion(new CellRangeAddress(fila, fila, 1, 3));
            altura(r, h, valor, 1, 4);
            return fila + 1;
        }

        /** Barras de datos con formato condicional sobre una columna de conteos (columna fija, rango fijo). */
        private void barrasDeDatos(SXSSFSheet h, int desde, int hasta, int columna) {
            if (hasta < desde) {
                return;
            }
            SheetConditionalFormatting formato = h.getSheetConditionalFormatting();
            ConditionalFormattingRule regla = formato.createConditionalFormattingRule(color("5B9BD5"));
            formato.addConditionalFormatting(new CellRangeAddress[] {new CellRangeAddress(desde, hasta, columna, columna)},
                regla);
        }

        private void mapaCalor() {
            SXSSFSheet h = hoja("Mapa de calor", new int[] {24, 16, 16, 16, 16, 16}, false);
            int f = 0;
            f = bloqueTexto(h, f, "Mapa de calor de riesgo: probabilidad por impacto", titulo(), 6);
            f++;
            Row cab = h.createRow(f++);
            texto(cab, 0, "Probabilidad \\ Impacto", cabecera());
            for (int i = 0; i < v.etiquetasImpacto().size(); i++) {
                texto(cab, i + 1, (i + 1) + " · " + v.etiquetasImpacto().get(i), cabecera());
            }
            for (VistaInforme.FilaCalor fila : v.mapaCalor()) {
                Row r = h.createRow(f++);
                texto(r, 0, fila.probabilidad(), etiqueta());
                for (int c = 0; c < fila.celdas().size(); c++) {
                    numero(r, c + 1, fila.celdas().get(c).valor(), paleta(fila.celdas().get(c).clase(), true));
                }
            }
            f++;
            f = bloqueTexto(h, f, "Cada celda indica cuántos hallazgos tienen esa probabilidad e impacto; el color es el "
                + "nivel de riesgo de la matriz de NIST SP 800-30 Rev. 1." + (v.sinProbabilidad() > 0
                ? " Hallazgos sin probabilidad: " + v.sinProbabilidad() + "." : ""), nota(), 6);
        }

        private void registro() {
            SXSSFSheet h = hoja("Registro", ANCHO_REGISTRO, true);
            Row cab = h.createRow(0);
            for (int c = 0; c < COLUMNAS_REGISTRO.length; c++) {
                texto(cab, c, COLUMNAS_REGISTRO[c], c >= 15 ? cabeceraCliente() : cabecera());
            }
            int f = 1;
            for (VistaInforme.FilaMatriz m : v.registroMatriz()) {
                Row r = h.createRow(f++);
                String clase = m.clase();
                texto(r, 0, m.codigo(), fila(clase, false));
                texto(r, 1, m.area(), fila(clase, false));
                texto(r, 2, m.titulo(), fila(clase, false));
                texto(r, 3, m.c(), fila(clase, true));
                texto(r, 4, m.i(), fila(clase, true));
                texto(r, 5, m.a(), fila(clase, true));
                texto(r, 6, m.severidad().texto(), fila(clase, true));
                texto(r, 7, m.probabilidad(), fila(clase, false));
                texto(r, 8, m.impacto(), fila(clase, false));
                texto(r, 9, m.puntaje(), fila(clase, true));
                texto(r, 10, m.riesgo().texto(), fila(clase, true));
                texto(r, 11, m.cve(), fila(clase, false));
                texto(r, 12, m.cvss(), fila(clase, true));
                texto(r, 13, m.estado(), fila(clase, false));
                texto(r, 14, m.mitigacion(), fila(clase, false));
                for (int c = 15; c <= 17; c++) {
                    vacia(r, c, fila(clase, false));
                }
            }
            h.createFreezePane(0, 1);
            h.setAutoFilter(new CellRangeAddress(0, 0, 0, COLUMNAS_REGISTRO.length - 1));
            h.setRepeatingRows(new CellRangeAddress(0, 0, 0, COLUMNAS_REGISTRO.length - 1));
            if (v.registroMatriz().isEmpty()) {
                texto(h.createRow(1), 0, "El informe no incluye hallazgos en el cuerpo.", nota());
            }
        }

        private void anexoA() {
            SXSSFSheet h = hoja("Anexo A", new int[] {14, 44, 26, 14, 18}, false);
            Row cab = h.createRow(0);
            String[] columnas = {"Código", "Hallazgo", "Área", "Severidad", "Tipo"};
            for (int c = 0; c < columnas.length; c++) {
                texto(cab, c, columnas[c], cabecera());
            }
            int f = 1;
            for (VistaInforme.FilaAnexoA a : v.anexoA()) {
                Row r = h.createRow(f++);
                texto(r, 0, a.codigo(), texto());
                texto(r, 1, a.titulo(), texto());
                texto(r, 2, a.area(), texto());
                texto(r, 3, a.severidad().texto(), paleta(a.severidad().clase(), true));
                texto(r, 4, a.tipo(), texto());
            }
            f++;
            if (v.anexoA().isEmpty()) {
                f = bloqueTexto(h, f, "El proyecto no tiene riesgos aceptados ni falsos positivos.", nota(), 5);
            }
            bloqueTexto(h, f, "Estos hallazgos no se incluyen en las métricas principales. La aplicación no registra una "
                + "justificación para el riesgo aceptado.", nota(), 5);
            h.createFreezePane(0, 1);
        }

        // ------------------------------------------------------------ Mitigación

        void mitigacion() {
            SXSSFSheet h = hoja("Plan de mitigación", ANCHO_PLAN, true);
            Row cab = h.createRow(0);
            for (int c = 0; c < COLUMNAS_PLAN.length; c++) {
                texto(cab, c, COLUMNAS_PLAN[c], c >= 8 ? cabeceraCliente() : cabecera());
            }
            int f = 1;
            for (VistaInforme.FilaMitigacion m : v.mitigacion()) {
                Row r = h.createRow(f++);
                numero(r, 0, m.prioridad(), centro());
                texto(r, 1, m.codigo(), texto());
                texto(r, 2, m.titulo(), texto());
                texto(r, 3, m.area(), texto());
                texto(r, 4, m.severidad().texto(), paleta(m.severidad().clase(), true));
                texto(r, 5, m.fase(), texto());
                texto(r, 6, m.accion(), texto());
                texto(r, 7, m.estado(), texto());
                for (int c = 8; c <= 10; c++) {
                    vacia(r, c, texto());
                }
            }
            h.createFreezePane(0, 1);
            h.setAutoFilter(new CellRangeAddress(0, 0, 0, COLUMNAS_PLAN.length - 1));
            h.setRepeatingRows(new CellRangeAddress(0, 0, 0, COLUMNAS_PLAN.length - 1));
            if (v.mitigacion().isEmpty()) {
                texto(h.createRow(1), 0, "El informe no incluye hallazgos en el cuerpo.", nota());
            }
            control();
        }

        // ------------------------------------------------------------ Control (común)

        private void control() {
            SXSSFSheet h = hoja("Control", new int[] {30, 22, 22, 22, 40}, false);
            int f = 0;
            f = bloqueTexto(h, f, "Control del documento", titulo(), 5);
            if (v.doc().noPublicado()) {
                f = bloqueTexto(h, f, textos.aviso("no-publicado"), aviso(), 5);
            }
            f++;
            f = parClaveValorControl(h, f, "Tipo", v.doc().tipoNombre());
            f = parClaveValorControl(h, f, "Identificador", v.doc().id());
            f = parClaveValorControl(h, f, "Versión", Integer.toString(v.doc().version()));
            f = parClaveValorControl(h, f, "Estado", v.doc().estado());
            f = parClaveValorControl(h, f, "SHA-256 del contenido", v.doc().hashCompleto());
            f = parClaveValorControl(h, f, "Versión de la plantilla", v.doc().plantillaVersion());
            f = parClaveValorControl(h, f, "Periodo del proyecto", v.doc().fechaInicio() + " al " + v.doc().fechaFin());
            f = parClaveValorControl(h, f, "Proyecto", v.doc().proyecto());
            f = parClaveValorControl(h, f, "Organización", v.doc().organizacion());
            f = parClaveValorControl(h, f, "Auditor", v.doc().auditor());
            f = parClaveValorControl(h, f, "Fecha de emisión", v.doc().fechaEmision());
            f++;
            f = bloqueTexto(h, f, "Historial de versiones", subtitulo(), 5);
            Row cab = h.createRow(f++);
            String[] columnas = {"Versión", "Estado", "Publicada", "Despublicada", "Motivo de despublicación"};
            for (int c = 0; c < columnas.length; c++) {
                texto(cab, c, columnas[c], cabecera());
            }
            for (VistaInforme.FilaHistorial hs : v.historial()) {
                Row r = h.createRow(f++);
                texto(r, 0, Integer.toString(hs.version()), centro());
                texto(r, 1, hs.estado(), texto());
                texto(r, 2, hs.publicado(), texto());
                texto(r, 3, hs.despublicado(), texto());
                texto(r, 4, hs.motivo(), texto());
            }
            f++;
            if (s.narrativa().get("introduccion") != null && !plano(s.narrativa().get("introduccion")).isBlank()) {
                f = bloqueTexto(h, f, "Introducción", subtitulo(), 5);
                f = bloqueTexto(h, f, plano(s.narrativa().get("introduccion")), texto(), 5);
                f++;
            }
            f = bloqueTexto(h, f, plano(textos.aviso("referencia")), nota(), 5);
            f = bloqueTexto(h, f, plano(textos.aviso("alcance")), nota(), 5);
            if (v.avisoAnexoA() != null) {
                f = bloqueTexto(h, f, plano(textos.avisoAnexoA(s.metricas().aceptados(), s.metricas().falsosPositivos())),
                    nota(), 5);
            }
            bloqueTexto(h, f, plano(textos.aviso("fase-sugerida")), nota(), 5);
        }

        private int parClaveValorControl(SXSSFSheet h, int fila, String clave, String valor) {
            Row r = h.createRow(fila);
            texto(r, 0, clave, etiqueta());
            texto(r, 1, valor, texto());
            h.addMergedRegion(new CellRangeAddress(fila, fila, 1, 4));
            altura(r, h, valor, 1, 5);
            return fila + 1;
        }

        private String plano(String markdownOrigen) {
            return markdownOrigen == null ? "" : markdown.analizar(markdownOrigen).textoPlano();
        }
    }

    /** Solo para las pruebas: lista de columnas del registro. */
    static List<String> columnasRegistro() {
        return List.of(COLUMNAS_REGISTRO);
    }
}

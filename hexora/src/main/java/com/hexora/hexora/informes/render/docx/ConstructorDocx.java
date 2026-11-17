package com.hexora.hexora.informes.render.docx;

import com.hexora.hexora.informes.markdown.BloqueNarrativa;
import com.hexora.hexora.informes.markdown.BloqueNarrativa.Fragmento;
import com.hexora.hexora.informes.render.PaletaInforme;
import com.hexora.hexora.informes.render.PaletaInforme.Colores;
import com.hexora.hexora.informes.render.TextoSeguro;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.List;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSimpleField;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTrPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHdrFtr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;

/**
 * Operaciones de bajo nivel sobre un documento XWPF partiendo de {@code informes/docx/base.docx} (solo estilos).
 * No crea hipervínculos, relaciones externas, objetos incrustados ni macros: el texto de usuario siempre entra como
 * texto de un run, y la única relación que agrega es la imagen del logo (interna al paquete).
 */
final class ConstructorDocx implements AutoCloseable {

    static final String BASE = "informes/docx/base.docx";
    static final String SANS = "Plus Jakarta Sans";
    static final String MONO = "JetBrains Mono";
    private static final int MARGEN = 1440;

    /** Celda con su aspecto. {@code fondo} y {@code color} en hexadecimal sin almohadilla, o {@code null}. */
    record Cd(String texto, String fondo, String color, boolean negrita, boolean mono, boolean centro) {

        static Cd t(String texto) {
            return new Cd(texto, null, null, false, false, false);
        }

        static Cd negrita(String texto) {
            return new Cd(texto, null, null, true, false, false);
        }

        static Cd mono(String texto) {
            return new Cd(texto, null, null, false, true, false);
        }

        static Cd centro(String texto) {
            return new Cd(texto, null, null, false, false, true);
        }

        static Cd vacia() {
            return t("");
        }

        /** Celda coloreada con la paleta de severidad o riesgo; el texto siempre lleva la etiqueta. */
        static Cd paleta(String texto, String clase) {
            Colores c = PaletaInforme.de(clase);
            return new Cd(texto, c.fondo(), c.texto(), true, false, true);
        }
    }

    private final XWPFDocument documento;
    private int anchoUtil;
    private int tablas;
    private int figuras;

    private ConstructorDocx(XWPFDocument documento) {
        this.documento = documento;
    }

    static ConstructorDocx desdeBase() throws IOException {
        try (InputStream base = ConstructorDocx.class.getClassLoader().getResourceAsStream(BASE)) {
            if (base == null) {
                throw new IOException("Falta la plantilla base del DOCX");
            }
            return new ConstructorDocx(new XWPFDocument(base));
        }
    }

    XWPFDocument documento() {
        return documento;
    }

    int anchoUtil() {
        return anchoUtil;
    }

    @Override
    public void close() throws IOException {
        documento.close();
    }

    // ---------------------------------------------------------------- página, encabezado y pie

    /** Carta, vertical u horizontal, con márgenes de 2,54 cm. */
    void pagina(boolean horizontal) {
        CTSectPr seccion = seccion();
        var tamano = seccion.isSetPgSz() ? seccion.getPgSz() : seccion.addNewPgSz();
        int ancho = horizontal ? 15840 : 12240;
        int alto = horizontal ? 12240 : 15840;
        tamano.setW(BigInteger.valueOf(ancho));
        tamano.setH(BigInteger.valueOf(alto));
        tamano.setOrient(horizontal ? STPageOrientation.LANDSCAPE : STPageOrientation.PORTRAIT);
        var margenes = seccion.isSetPgMar() ? seccion.getPgMar() : seccion.addNewPgMar();
        margenes.setTop(BigInteger.valueOf(MARGEN));
        margenes.setBottom(BigInteger.valueOf(MARGEN));
        margenes.setLeft(BigInteger.valueOf(MARGEN));
        margenes.setRight(BigInteger.valueOf(MARGEN));
        margenes.setHeader(BigInteger.valueOf(708));
        margenes.setFooter(BigInteger.valueOf(708));
        anchoUtil = ancho - 2 * MARGEN;
    }

    private CTSectPr seccion() {
        var cuerpo = documento.getDocument().getBody();
        return cuerpo.isSetSectPr() ? cuerpo.getSectPr() : cuerpo.addNewSectPr();
    }

    /** Encabezado "N de M" (campos PAGE y NUMPAGES) y pie con el identificador, la confidencialidad y el hash. */
    void encabezadoYPie(String rotulo, boolean noPublicado, String pie) {
        XWPFHeaderFooterPolicy politica = new XWPFHeaderFooterPolicy(documento, seccion());
        XWPFHeader encabezado = politica.createHeader(STHdrFtr.DEFAULT);
        XWPFParagraph p = encabezado.getParagraphs().isEmpty() ? encabezado.createParagraph() : encabezado.getParagraphs().get(0);
        p.setStyle("Encabezado");
        if (noPublicado) {
            XWPFRun aviso = p.createRun();
            aviso.setBold(true);
            aviso.setColor("C62828");
            aviso.setText("DOCUMENTO NO PUBLICADO  ·  ");
        }
        p.createRun().setText(TextoSeguro.limpiarXml(rotulo) + "  ·  Página ");
        campo(p, " PAGE ", "1");
        p.createRun().setText(" de ");
        campo(p, " NUMPAGES ", "1");

        XWPFFooter pieDoc = politica.createFooter(STHdrFtr.DEFAULT);
        XWPFParagraph q = pieDoc.getParagraphs().isEmpty() ? pieDoc.createParagraph() : pieDoc.getParagraphs().get(0);
        q.setStyle("Pie");
        q.createRun().setText(TextoSeguro.limpiarXml(pie));
    }

    private static void campo(XWPFParagraph p, String instruccion, String marcador) {
        CTP ctp = p.getCTP();
        CTSimpleField campo = ctp.addNewFldSimple();
        campo.setInstr(instruccion);
        CTR run = campo.addNewR();
        run.addNewT().setStringValue(marcador);
    }

    void propiedades(String titulo, String asunto) {
        var nucleo = documento.getProperties().getCoreProperties();
        nucleo.setTitle(TextoSeguro.limpiarXml(titulo));
        nucleo.setSubjectProperty(TextoSeguro.limpiarXml(asunto));
        nucleo.setCreator("TinyHat");
    }

    // ---------------------------------------------------------------- texto

    XWPFParagraph titulo(int nivel, String texto, boolean nuevaPagina) {
        XWPFParagraph p = documento.createParagraph();
        p.setStyle("Heading" + nivel);
        p.setPageBreak(nuevaPagina);
        p.createRun().setText(TextoSeguro.limpiarXml(texto));
        return p;
    }

    XWPFParagraph parrafo(String estilo, String texto) {
        XWPFParagraph p = documento.createParagraph();
        if (estilo != null) {
            p.setStyle(estilo);
        }
        escribirConSaltos(p.createRun(), texto);
        return p;
    }

    XWPFParagraph parrafoCentrado(String estilo, String texto) {
        XWPFParagraph p = parrafo(estilo, texto);
        p.setAlignment(ParagraphAlignment.CENTER);
        return p;
    }

    /** "Nota." en cursiva seguida del texto. */
    void nota(String texto) {
        XWPFParagraph p = documento.createParagraph();
        p.setStyle("Nota");
        XWPFRun etiqueta = p.createRun();
        etiqueta.setItalic(true);
        etiqueta.setText("Nota. ");
        p.createRun().setText(TextoSeguro.limpiarXml(texto));
    }

    void leyendaTabla(String titulo) {
        parrafo("Leyenda", "Tabla " + (++tablas));
        parrafo("LeyendaTitulo", titulo);
    }

    void leyendaFigura(String titulo) {
        parrafo("Leyenda", "Figura " + (++figuras));
        parrafo("LeyendaTitulo", titulo);
    }

    void logo(InputStream imagen, int lado) throws IOException {
        XWPFParagraph p = documento.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        try {
            p.createRun().addPicture(imagen, org.apache.poi.common.usermodel.PictureType.PNG, "logo-tinyhat.png", Units.toEMU(lado),
                Units.toEMU(lado));
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new IOException("Logo inválido", e);
        }
    }

    private static void escribirConSaltos(XWPFRun run, String texto) {
        String[] lineas = TextoSeguro.limpiarXml(texto).split("\n", -1);
        for (int i = 0; i < lineas.length; i++) {
            if (i > 0) {
                run.addBreak();
            }
            run.setText(lineas[i]);
        }
    }

    // ---------------------------------------------------------------- tablas

    /** Tabla de anchos fijos (en veinteavos de punto) con la fila de encabezado repetida en cada página. */
    XWPFTable tabla(int[] anchos, List<Cd> encabezado, List<List<Cd>> filas) {
        int columnas = anchos.length;
        boolean conEncabezado = encabezado != null && !encabezado.isEmpty();
        int total = 0;
        for (int ancho : anchos) {
            total += ancho;
        }
        XWPFTable t = documento.createTable(filas.size() + (conEncabezado ? 1 : 0), columnas);
        t.setStyleID("Tabla");
        CTTblPr propiedades = t.getCTTbl().getTblPr();
        if (propiedades.isSetTblBorders()) {
            propiedades.unsetTblBorders();
        }
        CTTblWidth ancho = propiedades.isSetTblW() ? propiedades.getTblW() : propiedades.addNewTblW();
        ancho.setType(STTblWidth.DXA);
        ancho.setW(BigInteger.valueOf(total));
        (propiedades.isSetTblLayout() ? propiedades.getTblLayout() : propiedades.addNewTblLayout())
            .setType(STTblLayoutType.FIXED);
        CTTblGrid cuadricula = t.getCTTbl().getTblGrid() == null ? t.getCTTbl().addNewTblGrid() : t.getCTTbl().getTblGrid();
        while (cuadricula.sizeOfGridColArray() > 0) {
            cuadricula.removeGridCol(0);
        }
        for (int a : anchos) {
            cuadricula.addNewGridCol().setW(BigInteger.valueOf(a));
        }
        int fila = 0;
        if (conEncabezado) {
            XWPFTableRow cabecera = t.getRow(0);
            CTTrPr pr = cabecera.getCtRow().isSetTrPr() ? cabecera.getCtRow().getTrPr() : cabecera.getCtRow().addNewTrPr();
            pr.addNewTblHeader();
            for (int c = 0; c < columnas; c++) {
                Cd origen = encabezado != null && c < encabezado.size() ? encabezado.get(c) : Cd.vacia();
                escribir(cabecera.getCell(c), new Cd(origen.texto(), "E8EDF2", null, true, false, origen.centro()),
                    anchos[c]);
            }
            fila = 1;
        }
        for (List<Cd> datos : filas) {
            XWPFTableRow r = t.getRow(fila++);
            CTTrPr pr = r.getCtRow().isSetTrPr() ? r.getCtRow().getTrPr() : r.getCtRow().addNewTrPr();
            pr.addNewCantSplit();
            for (int c = 0; c < columnas; c++) {
                escribir(r.getCell(c), c < datos.size() ? datos.get(c) : Cd.vacia(), anchos[c]);
            }
        }
        // Un párrafo vacío tras la tabla evita que Word una tablas consecutivas.
        documento.createParagraph().setSpacingAfter(0);
        return t;
    }

    private void escribir(XWPFTableCell celda, Cd d, int ancho) {
        CTTcPr pr = celda.getCTTc().isSetTcPr() ? celda.getCTTc().getTcPr() : celda.getCTTc().addNewTcPr();
        CTTblWidth w = pr.isSetTcW() ? pr.getTcW() : pr.addNewTcW();
        w.setType(STTblWidth.DXA);
        w.setW(BigInteger.valueOf(ancho));
        if (d.fondo() != null) {
            celda.setColor(d.fondo());
        }
        XWPFParagraph p = celda.getParagraphs().get(0);
        p.setAlignment(d.centro() ? ParagraphAlignment.CENTER : ParagraphAlignment.LEFT);
        XWPFRun run = p.createRun();
        run.setBold(d.negrita());
        if (d.mono()) {
            run.setFontFamily(MONO);
        }
        if (d.color() != null) {
            run.setColor(d.color());
        }
        escribirConSaltos(run, d.texto());
    }

    // ---------------------------------------------------------------- narrativa (Markdown ya saneado)

    void narrativa(List<BloqueNarrativa> bloques, String estiloBase) {
        escribirBloques(bloques, estiloBase, 0, null);
    }

    private void escribirBloques(List<BloqueNarrativa> bloques, String estilo, int sangria, String marca) {
        String pendiente = marca;
        for (BloqueNarrativa bloque : bloques) {
            switch (bloque) {
                case BloqueNarrativa.Parrafo p -> {
                    XWPFParagraph parrafo = documento.createParagraph();
                    if (estilo != null) {
                        parrafo.setStyle(estilo);
                    }
                    aplicarSangria(parrafo, sangria, pendiente != null);
                    if (pendiente != null) {
                        parrafo.createRun().setText(pendiente);
                        pendiente = null;
                    }
                    fragmentos(parrafo, p.fragmentos());
                }
                case BloqueNarrativa.Encabezado e -> {
                    XWPFParagraph parrafo = documento.createParagraph();
                    parrafo.setStyle("Heading" + Math.min(4, Math.max(3, e.nivel())));
                    fragmentos(parrafo, e.fragmentos());
                }
                case BloqueNarrativa.Lista l -> {
                    int numero = 1;
                    for (List<BloqueNarrativa> elemento : l.elementos()) {
                        String viñeta = l.ordenada() ? (numero++) + ". " : "• ";
                        escribirBloques(elemento, estilo, sangria + 1, viñeta);
                    }
                }
                case BloqueNarrativa.BloqueCodigo c -> {
                    for (String linea : TextoSeguro.limpiarXml(c.texto()).split("\n", -1)) {
                        XWPFParagraph parrafo = documento.createParagraph();
                        parrafo.setStyle("Codigo");
                        aplicarSangria(parrafo, sangria, false);
                        XWPFRun run = parrafo.createRun();
                        run.setText(linea);
                    }
                }
                case BloqueNarrativa.Cita c -> escribirBloques(c.bloques(), "Cita", sangria, null);
                case BloqueNarrativa.Tabla t -> tablaNarrativa(t);
                case BloqueNarrativa.Separador _ -> parrafoCentrado(null, "— — —");
            }
        }
    }

    private static void aplicarSangria(XWPFParagraph p, int nivel, boolean conMarca) {
        if (nivel > 0) {
            p.setIndentationLeft(360 * nivel);
            if (conMarca) {
                p.setIndentationHanging(260);
            }
        }
    }

    private void fragmentos(XWPFParagraph p, List<Fragmento> fragmentos) {
        for (Fragmento f : fragmentos) {
            XWPFRun run = p.createRun();
            if (f.esSaltoDeLinea()) {
                run.addBreak(BreakType.TEXT_WRAPPING);
                continue;
            }
            run.setBold(f.negrita());
            run.setItalic(f.cursiva());
            if (f.codigo()) {
                run.setFontFamily(MONO);
            }
            run.setText(TextoSeguro.limpiarXml(f.texto()));
        }
    }

    private void tablaNarrativa(BloqueNarrativa.Tabla tabla) {
        if (tabla.filas().isEmpty()) {
            return;
        }
        int columnas = tabla.filas().stream().mapToInt(List::size).max().orElse(1);
        int[] anchos = new int[columnas];
        java.util.Arrays.fill(anchos, Math.max(1, anchoUtil / columnas));
        List<List<Cd>> filas = new java.util.ArrayList<>();
        for (List<List<Fragmento>> fila : tabla.filas()) {
            List<Cd> celdas = new java.util.ArrayList<>();
            for (List<Fragmento> celda : fila) {
                String texto = celda.stream().map(f -> f.esSaltoDeLinea() ? "\n" : f.texto())
                    .collect(java.util.stream.Collectors.joining());
                celdas.add(Cd.t(texto));
            }
            filas.add(celdas);
        }
        List<Cd> encabezado = tabla.conEncabezado() ? filas.remove(0) : null;
        tabla(anchos, encabezado, filas);
    }
}

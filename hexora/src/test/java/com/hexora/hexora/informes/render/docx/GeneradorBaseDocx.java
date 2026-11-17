package com.hexora.hexora.informes.render.docx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles;

/**
 * Herramienta de desarrollo que produce {@code informes/docx/base.docx}: un documento sin contenido, solo con los
 * estilos (Título 1–4, Tabla, Nota, Código, etc.). Se ejecuta a mano con
 * {@code -Dinformes.base-docx=RUTA} (ver {@code BaseDocxTest}); el resultado se versiona en los recursos.
 * El tipo de letra se nombra pero no se incrusta: si el equipo no tiene Plus Jakarta Sans, Word usa uno de reemplazo.
 */
final class GeneradorBaseDocx {

    private static final String W = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"";
    private static final String SANS = "Plus Jakarta Sans";
    private static final String MONO = "JetBrains Mono";

    private GeneradorBaseDocx() {
    }

    static byte[] generar() throws IOException {
        try (XWPFDocument documento = new XWPFDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            documento.createStyles().setStyles(estilos());
            documento.write(salida);
            return salida.toByteArray();
        } catch (org.apache.xmlbeans.XmlException e) {
            throw new IOException("Estilos inválidos", e);
        }
    }

    private static CTStyles estilos() throws org.apache.xmlbeans.XmlException {
        return org.openxmlformats.schemas.wordprocessingml.x2006.main.StylesDocument.Factory.parse(xml()).getStyles();
    }

    private static String fuente(String nombre) {
        return "<w:rFonts w:ascii=\"" + nombre + "\" w:hAnsi=\"" + nombre + "\" w:cs=\"" + nombre + "\" w:eastAsia=\""
            + nombre + "\"/>";
    }

    private static String parrafo(String id, String nombre, String basadoEn, String siguiente, String pPr, String rPr) {
        return "<w:style w:type=\"paragraph\" w:styleId=\"" + id + "\"><w:name w:val=\"" + nombre + "\"/>"
            + (basadoEn == null ? "" : "<w:basedOn w:val=\"" + basadoEn + "\"/>")
            + (siguiente == null ? "" : "<w:next w:val=\"" + siguiente + "\"/>")
            + "<w:qFormat/><w:pPr>" + pPr + "</w:pPr><w:rPr>" + rPr + "</w:rPr></w:style>";
    }

    private static String xml() {
        StringBuilder s = new StringBuilder("<w:styles " + W + ">");
        s.append("<w:docDefaults><w:rPrDefault><w:rPr>").append(fuente(SANS))
            .append("<w:color w:val=\"1A1A1A\"/><w:sz w:val=\"21\"/><w:szCs w:val=\"21\"/><w:lang w:val=\"es-MX\"/>")
            .append("</w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after=\"120\" w:line=\"276\" w:lineRule=\"auto\"/>")
            .append("</w:pPr></w:pPrDefault></w:docDefaults>");
        s.append("<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>");
        s.append("<w:style w:type=\"character\" w:default=\"1\" w:styleId=\"Fuentepredeter\"><w:name w:val=\"Default Paragraph Font\"/><w:uiPriority w:val=\"1\"/></w:style>");
        s.append("<w:style w:type=\"table\" w:default=\"1\" w:styleId=\"TablaNormal\"><w:name w:val=\"Normal Table\"/>")
            .append("<w:tblPr><w:tblInd w:w=\"0\" w:type=\"dxa\"/><w:tblCellMar><w:top w:w=\"0\" w:type=\"dxa\"/><w:left w:w=\"108\" w:type=\"dxa\"/>")
            .append("<w:bottom w:w=\"0\" w:type=\"dxa\"/><w:right w:w=\"108\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr></w:style>");

        // Títulos 1 a 4 (APA 7 adaptado): 1 centrado en negrita, 2 a la izquierda en negrita, 3 negrita cursiva, 4 con sangría.
        s.append(parrafo("Heading1", "heading 1", "Normal", "Normal",
            "<w:keepNext/><w:keepLines/><w:spacing w:before=\"360\" w:after=\"160\"/><w:jc w:val=\"center\"/><w:outlineLvl w:val=\"0\"/>",
            "<w:b/><w:color w:val=\"0F2A43\"/><w:sz w:val=\"32\"/><w:szCs w:val=\"32\"/>"));
        s.append(parrafo("Heading2", "heading 2", "Normal", "Normal",
            "<w:keepNext/><w:keepLines/><w:spacing w:before=\"280\" w:after=\"120\"/><w:outlineLvl w:val=\"1\"/>",
            "<w:b/><w:color w:val=\"0F2A43\"/><w:sz w:val=\"26\"/><w:szCs w:val=\"26\"/>"));
        s.append(parrafo("Heading3", "heading 3", "Normal", "Normal",
            "<w:keepNext/><w:keepLines/><w:spacing w:before=\"200\" w:after=\"100\"/><w:outlineLvl w:val=\"2\"/>",
            "<w:b/><w:i/><w:sz w:val=\"23\"/><w:szCs w:val=\"23\"/>"));
        s.append(parrafo("Heading4", "heading 4", "Normal", "Normal",
            "<w:keepNext/><w:keepLines/><w:spacing w:before=\"160\" w:after=\"80\"/><w:ind w:left=\"720\"/><w:outlineLvl w:val=\"3\"/>",
            "<w:b/><w:sz w:val=\"21\"/><w:szCs w:val=\"21\"/>"));

        // Portada.
        s.append(parrafo("PortadaTipo", "Portada tipo", "Normal", "Normal",
            "<w:spacing w:before=\"1200\" w:after=\"240\"/><w:jc w:val=\"center\"/>",
            "<w:b/><w:caps/><w:color w:val=\"0F2A43\"/><w:sz w:val=\"28\"/><w:szCs w:val=\"28\"/>"));
        s.append(parrafo("PortadaProyecto", "Portada proyecto", "Normal", "Normal",
            "<w:spacing w:after=\"160\"/><w:jc w:val=\"center\"/>", "<w:b/><w:sz w:val=\"48\"/><w:szCs w:val=\"48\"/>"));
        s.append(parrafo("PortadaOrganizacion", "Portada organización", "Normal", "Normal",
            "<w:spacing w:after=\"480\"/><w:jc w:val=\"center\"/>", "<w:sz w:val=\"28\"/><w:szCs w:val=\"28\"/>"));
        s.append(parrafo("Confidencial", "Confidencial", "Normal", "Normal",
            "<w:spacing w:before=\"480\" w:after=\"240\"/><w:jc w:val=\"center\"/>",
            "<w:b/><w:spacing w:val=\"60\"/><w:color w:val=\"7A0019\"/><w:sz w:val=\"26\"/><w:szCs w:val=\"26\"/>"));

        // Texto auxiliar.
        s.append(parrafo("Nota", "Nota", "Normal", "Normal", "<w:spacing w:before=\"60\" w:after=\"200\"/>",
            "<w:color w:val=\"444444\"/><w:sz w:val=\"18\"/><w:szCs w:val=\"18\"/>"));
        s.append(parrafo("Leyenda", "Leyenda de tabla", "Normal", "Normal",
            "<w:keepNext/><w:spacing w:before=\"200\" w:after=\"0\"/>", "<w:b/><w:sz w:val=\"20\"/><w:szCs w:val=\"20\"/>"));
        s.append(parrafo("LeyendaTitulo", "Título de tabla", "Normal", "Normal",
            "<w:keepNext/><w:spacing w:after=\"80\"/>", "<w:i/><w:sz w:val=\"20\"/><w:szCs w:val=\"20\"/>"));
        s.append(parrafo("Codigo", "Código", "Normal", "Normal",
            "<w:spacing w:after=\"0\" w:line=\"240\" w:lineRule=\"auto\"/><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F2F2F2\"/>",
            fuente(MONO) + "<w:sz w:val=\"18\"/><w:szCs w:val=\"18\"/>"));
        s.append(parrafo("Cita", "Cita", "Normal", "Normal",
            "<w:pBdr><w:left w:val=\"single\" w:sz=\"12\" w:space=\"8\" w:color=\"9AA5B1\"/></w:pBdr><w:ind w:left=\"567\"/>",
            "<w:i/><w:color w:val=\"444444\"/>"));
        s.append(parrafo("Referencia", "Referencia APA", "Normal", "Normal",
            "<w:spacing w:after=\"100\"/><w:ind w:left=\"720\" w:hanging=\"720\"/>", ""));
        s.append(parrafo("Aviso", "Aviso", "Normal", "Normal",
            "<w:pBdr><w:top w:val=\"single\" w:sz=\"4\" w:space=\"4\" w:color=\"9AA5B1\"/><w:left w:val=\"single\" w:sz=\"4\" w:space=\"4\" w:color=\"9AA5B1\"/>"
                + "<w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"4\" w:color=\"9AA5B1\"/><w:right w:val=\"single\" w:sz=\"4\" w:space=\"4\" w:color=\"9AA5B1\"/></w:pBdr>"
                + "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F2F4F7\"/><w:spacing w:before=\"120\" w:after=\"120\"/>",
            "<w:sz w:val=\"19\"/><w:szCs w:val=\"19\"/>"));
        s.append(parrafo("AvisoNP", "Aviso no publicado", "Aviso", "Normal",
            "<w:pBdr><w:top w:val=\"single\" w:sz=\"12\" w:space=\"4\" w:color=\"C62828\"/><w:left w:val=\"single\" w:sz=\"12\" w:space=\"4\" w:color=\"C62828\"/>"
                + "<w:bottom w:val=\"single\" w:sz=\"12\" w:space=\"4\" w:color=\"C62828\"/><w:right w:val=\"single\" w:sz=\"12\" w:space=\"4\" w:color=\"C62828\"/></w:pBdr>"
                + "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"FDECEA\"/>",
            "<w:b/><w:color w:val=\"C62828\"/>"));
        s.append(parrafo("Ficha", "Ficha de hallazgo", "Normal", "Normal",
            "<w:keepNext/><w:spacing w:before=\"240\" w:after=\"60\"/><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"E8EDF2\"/>",
            "<w:b/><w:sz w:val=\"21\"/><w:szCs w:val=\"21\"/>"));
        s.append(parrafo("Encabezado", "header", "Normal", "Normal", "<w:spacing w:after=\"0\"/><w:jc w:val=\"right\"/>",
            "<w:color w:val=\"555555\"/><w:sz w:val=\"16\"/><w:szCs w:val=\"16\"/>"));
        s.append(parrafo("Pie", "footer", "Normal", "Normal", "<w:spacing w:after=\"0\"/><w:jc w:val=\"center\"/>",
            "<w:color w:val=\"555555\"/><w:sz w:val=\"16\"/><w:szCs w:val=\"16\"/>"));

        // Tabla: bordes finos grises, texto de 9 pt y sin espacio entre párrafos de la celda.
        s.append("<w:style w:type=\"table\" w:styleId=\"Tabla\"><w:name w:val=\"Tabla\"/><w:basedOn w:val=\"TablaNormal\"/><w:qFormat/>")
            .append("<w:pPr><w:spacing w:before=\"20\" w:after=\"20\" w:line=\"240\" w:lineRule=\"auto\"/></w:pPr>")
            .append("<w:rPr><w:sz w:val=\"18\"/><w:szCs w:val=\"18\"/></w:rPr>")
            .append("<w:tblPr><w:tblBorders>");
        for (String borde : new String[] {"top", "left", "bottom", "right", "insideH", "insideV"}) {
            s.append("<w:").append(borde).append(" w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"BFC5CC\"/>");
        }
        s.append("</w:tblBorders><w:tblCellMar><w:top w:w=\"40\" w:type=\"dxa\"/><w:left w:w=\"80\" w:type=\"dxa\"/>")
            .append("<w:bottom w:w=\"40\" w:type=\"dxa\"/><w:right w:w=\"80\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr>")
            .append("<w:tblStylePr w:type=\"firstRow\"><w:rPr><w:b/></w:rPr><w:tcPr><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"E8EDF2\"/></w:tcPr></w:tblStylePr>")
            .append("</w:style>");
        s.append("</w:styles>");
        return s.toString();
    }
}

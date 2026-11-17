package com.hexora.hexora.informes.render.pdf;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.cos.COSName;

/** Lectura de PDF generados para las pruebas: texto, fuentes, páginas y tamaño de página. */
final class PdfPrueba {

    private PdfPrueba() {
    }

    static String texto(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            PDFTextStripper extractor = new PDFTextStripper();
            return extractor.getText(documento);
        }
    }

    /** Texto de una sola página (empieza en 1). */
    static String textoPagina(byte[] pdf, int pagina) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            PDFTextStripper extractor = new PDFTextStripper();
            extractor.setStartPage(pagina);
            extractor.setEndPage(pagina);
            return extractor.getText(documento);
        }
    }

    static int paginas(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return documento.getNumberOfPages();
        }
    }

    /** Ancho y alto en puntos de la página dada (empieza en 0). */
    static float[] tamano(byte[] pdf, int pagina) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            var caja = documento.getPage(pagina).getMediaBox();
            return new float[] {caja.getWidth(), caja.getHeight()};
        }
    }

    /** Nombres de las fuentes usadas en todas las páginas (los subconjuntos llevan un prefijo ABCDEF+). */
    static Set<String> fuentes(byte[] pdf) throws IOException {
        Set<String> nombres = new LinkedHashSet<>();
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            for (PDPage pagina : documento.getPages()) {
                PDResources recursos = pagina.getResources();
                for (COSName nombre : recursos.getFontNames()) {
                    PDFont fuente = recursos.getFont(nombre);
                    nombres.add(fuente.getName());
                }
            }
        }
        return nombres;
    }

    /** Número de marcadores (bookmarks) de primer nivel del documento. */
    static int marcadores(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            var esquema = documento.getDocumentCatalog().getDocumentOutline();
            if (esquema == null) {
                return 0;
            }
            int total = 0;
            for (var hijo = esquema.getFirstChild(); hijo != null; hijo = hijo.getNextSibling()) {
                total++;
            }
            return total;
        }
    }

    /** Título guardado en la información del documento. */
    static String titulo(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return documento.getDocumentInformation().getTitle();
        }
    }
}

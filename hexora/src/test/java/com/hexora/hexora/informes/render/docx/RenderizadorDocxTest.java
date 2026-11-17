package com.hexora.hexora.informes.render.docx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexora.hexora.informes.FormatoInforme;
import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.markdown.MarkdownSaneado;
import com.hexora.hexora.informes.render.DatosDemo;
import com.hexora.hexora.informes.render.OfimaticaPrueba;
import com.hexora.hexora.informes.render.OpcionesRender;
import com.hexora.hexora.informes.render.TextosInforme;
import com.hexora.hexora.informes.render.pdf.ConstructorVistaInforme;
import com.hexora.hexora.informes.snapshot.InformeSnapshot;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RenderizadorDocxTest {

    static final List<TipoInforme> TIPOS_DOCX =
        List.of(TipoInforme.TEC, TipoInforme.EJE, TipoInforme.MIT, TipoInforme.FIN);

    static RenderizadorDocx renderizador() {
        MarkdownSaneado markdown = new MarkdownSaneado(20000);
        TextosInforme textos = new TextosInforme();
        return new RenderizadorDocx(new ConstructorVistaInforme(markdown, textos), markdown, textos);
    }

    static byte[] renderizar(InformeSnapshot snapshot, boolean noPublicado) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        renderizador().render(snapshot, new OpcionesRender(noPublicado), salida);
        return salida.toByteArray();
    }

    static String texto(byte[] docx) throws IOException {
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(docx));
             XWPFWordExtractor extractor = new XWPFWordExtractor(d)) {
            return extractor.getText();
        }
    }

    // ---------------------------------------------------------------- contenido

    @ParameterizedTest
    @EnumSource(value = TipoInforme.class, names = {"TEC", "EJE", "MIT", "FIN"})
    void generaUnDocxValidoConLosDatosDelSnapshot(TipoInforme tipo) throws Exception {
        for (boolean np : new boolean[] {false, true}) {
            InformeSnapshot snapshot = DatosDemo.snapshot(tipo, np);
            byte[] docx = renderizar(snapshot, np);
            String texto = texto(docx);
            assertThat(texto).contains(snapshot.informe().id()).contains("Evaluación de seguridad de la plataforma de ventas")
                .contains("Comercializadora Andina").contains("Firma Auditora Demo").contains("CONFIDENCIAL")
                .contains("Control del documento").contains("Historial de versiones")
                .contains("La versión de referencia de este informe es el PDF publicado");
            assertThat(texto).contains(np ? "DOCUMENTO NO PUBLICADO" : "Publicado");
            if (!np) {
                assertThat(texto).doesNotContain("DOCUMENTO NO PUBLICADO");
            }
        }
    }

    @Test
    void elTecnicoTraeFichasAnexosYReferencias() throws Exception {
        String texto = texto(renderizar(DatosDemo.snapshot(TipoInforme.TEC, false), false));
        assertThat(texto).contains("Alcance y metodología").contains("Escala de calificación").contains("Resumen de hallazgos")
            .contains("Hallazgos por área").contains("Gestión de identidades").contains("Inyección SQL en el buscador")
            .contains("CVE-2099-0001").contains("CVSS 9.8").contains("Anexo A. Riesgos aceptados")
            .contains("Anexo B. Glosario").contains("Referencias").contains("NIST SP 800-30")
            .contains("Tabla 1").contains("ñoquis");
        assertThat(texto).as("sin índice automático (§7.2)").doesNotContain("Índice");
    }

    @Test
    void elEjecutivoNoTraeCveNiCvss() throws Exception {
        String texto = texto(renderizar(DatosDemo.snapshot(TipoInforme.EJE, false), false));
        assertThat(texto).contains("Los riesgos principales").contains("Hallazgos por severidad")
            .contains("Hallazgos por área y severidad").contains("Recomendaciones");
        assertThat(texto).doesNotContain("CVE-").doesNotContain("CVSS");
    }

    @Test
    void laMitigacionEsHorizontalYElRestoVertical() throws Exception {
        for (TipoInforme tipo : TIPOS_DOCX) {
            try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(
                renderizar(DatosDemo.snapshot(tipo, false), false)))) {
                var tamano = d.getDocument().getBody().getSectPr().getPgSz();
                boolean horizontal = tipo == TipoInforme.MIT;
                assertThat(Integer.parseInt(String.valueOf(tamano.getW()))).as(tipo.name()).isEqualTo(horizontal ? 15840 : 12240);
                assertThat(Integer.parseInt(String.valueOf(tamano.getH()))).isEqualTo(horizontal ? 12240 : 15840);
            }
        }
    }

    @Test
    void elFinalTraeLasDosPartesLaTrazabilidadYLaConstancia() throws Exception {
        String texto = texto(renderizar(DatosDemo.snapshot(TipoInforme.FIN, false), false));
        assertThat(texto).contains("Parte I. Informe ejecutivo").contains("Parte II. Informe técnico")
            .contains("Trazabilidad").contains("Paquete de entregables").contains("Constancia de entrega")
            .contains("Firma del auditor").contains("Firma del representante del cliente")
            .contains("Lista de verificación de cierre").contains("Mapa de calor");
    }

    @Test
    void laMitigacionYElOrdenSonLosDelPdf() throws Exception {
        InformeSnapshot snapshot = DatosDemo.snapshot(TipoInforme.MIT, false);
        String texto = texto(renderizar(snapshot, false));
        int sql = texto.indexOf("Inyección SQL en el buscador");
        int certificado = texto.indexOf("Certificado TLS caducado");
        assertThat(sql).isPositive();
        assertThat(certificado).as("la crítica va antes que la de severidad menor").isGreaterThan(sql);
        assertThat(texto).contains("Las fases de remediación son una sugerencia derivada de la severidad");
    }

    // ---------------------------------------------------------------- estructura de Word

    @Test
    void encabezadoYPieTienenLosCamposYElHash() throws Exception {
        InformeSnapshot snapshot = DatosDemo.snapshot(TipoInforme.TEC, false);
        byte[] docx = renderizar(snapshot, false);
        Map<String, String> partes = OfimaticaPrueba.textos(docx);
        String encabezado = partes.entrySet().stream().filter(e -> e.getKey().contains("header")).map(Map.Entry::getValue)
            .findFirst().orElseThrow();
        String pie = partes.entrySet().stream().filter(e -> e.getKey().contains("footer")).map(Map.Entry::getValue)
            .findFirst().orElseThrow();
        assertThat(encabezado).contains("w:instr=\" PAGE \"").contains("w:instr=\" NUMPAGES \"");
        assertThat(pie).contains(snapshot.informe().id()).contains("CONFIDENCIAL").contains("SHA-256: ");
        // Solo hay campos simples de numeración de página en todo el documento.
        for (Map.Entry<String, String> e : partes.entrySet()) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("w:instr=\"([^\"]*)\"").matcher(e.getValue());
            while (m.find()) {
                assertThat(m.group(1).trim()).isIn("PAGE", "NUMPAGES");
            }
            assertThat(e.getValue()).doesNotContain("w:fldChar").doesNotContain("w:instrText");
        }
    }

    @Test
    void laCopiaNoPublicadaMarcaElEncabezado() throws Exception {
        String encabezado = OfimaticaPrueba.textos(renderizar(DatosDemo.snapshot(TipoInforme.EJE, true), true)).entrySet()
            .stream().filter(e -> e.getKey().contains("header")).map(Map.Entry::getValue).findFirst().orElseThrow();
        assertThat(encabezado).contains("DOCUMENTO NO PUBLICADO");
        String publicado = OfimaticaPrueba.textos(renderizar(DatosDemo.snapshot(TipoInforme.EJE, false), false)).entrySet()
            .stream().filter(e -> e.getKey().contains("header")).map(Map.Entry::getValue).findFirst().orElseThrow();
        assertThat(publicado).doesNotContain("DOCUMENTO NO PUBLICADO");
    }

    @Test
    void lasCeldasDeSeveridadVanSombreadasConLaPaletaYLlevanSuEtiqueta() throws Exception {
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(
            renderizar(DatosDemo.snapshot(TipoInforme.TEC, false), false)))) {
            Map<String, String> esperado = Map.of("Crítica", "7A0019", "Alta", "C62828", "Media", "F9A825",
                "Baja", "2E7D32", "Informativa", "1565C0");
            java.util.Set<String> vistas = new java.util.HashSet<>();
            for (XWPFTable tabla : d.getTables()) {
                for (XWPFTableRow fila : tabla.getRows()) {
                    for (XWPFTableCell celda : fila.getTableCells()) {
                        String etiqueta = celda.getText();
                        if (esperado.containsKey(etiqueta) && celda.getColor() != null) {
                            assertThat(celda.getColor()).as(etiqueta).isEqualToIgnoringCase(esperado.get(etiqueta));
                            vistas.add(etiqueta);
                        }
                    }
                }
            }
            assertThat(vistas).contains("Crítica", "Alta", "Media");
        }
    }

    @Test
    void usaLosEstilosDeLaBaseYNoFormatoSuelto() throws Exception {
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(
            renderizar(DatosDemo.snapshot(TipoInforme.TEC, false), false)))) {
            java.util.Set<String> estilos = new java.util.HashSet<>();
            d.getParagraphs().forEach(p -> estilos.add(String.valueOf(p.getStyleID())));
            assertThat(estilos).contains("Heading1", "Heading2", "Leyenda", "LeyendaTitulo", "Nota", "Ficha", "Referencia");
            assertThat(d.getTables()).allSatisfy(t -> assertThat(t.getStyleID()).isEqualTo("Tabla"));
        }
    }

    // ---------------------------------------------------------------- seguridad del contenedor

    @ParameterizedTest
    @EnumSource(value = TipoInforme.class, names = {"TEC", "EJE", "MIT", "FIN"})
    void elDocxNoTraeRelacionesExternasMacrosObjetosNiEnlaces(TipoInforme tipo) throws Exception {
        for (InformeSnapshot snapshot : List.of(DatosDemo.snapshot(tipo, false), OfimaticaPrueba.snapshotConFormulas(tipo, true))) {
            byte[] docx = renderizar(snapshot, snapshot.informe().id().endsWith("-NP"));
            OfimaticaPrueba.verificarContenedorSinRiesgos(docx);
            assertThat(OfimaticaPrueba.entradas(docx)).noneMatch(n -> n.endsWith(".docm") || n.contains("macros"));
            Map<String, String> partes = OfimaticaPrueba.textos(docx);
            assertThat(partes.get("[Content_Types].xml")).contains("wordprocessingml.document.main+xml")
                .doesNotContain("macroEnabled");
        }
    }

    @Test
    void laUnicaRelacionAMediosEsElLogoInterno() throws Exception {
        byte[] docx = renderizar(DatosDemo.snapshot(TipoInforme.TEC, false), false);
        assertThat(OfimaticaPrueba.entradas(docx)).filteredOn(n -> n.startsWith("word/media/")).hasSize(1);
        for (Map.Entry<String, String> e : OfimaticaPrueba.textos(docx).entrySet()) {
            if (e.getKey().endsWith(".rels")) {
                assertThat(e.getValue()).doesNotContain("TargetMode");
            }
        }
    }

    // ---------------------------------------------------------------- datos hostiles

    @ParameterizedTest
    @EnumSource(value = TipoInforme.class, names = {"TEC", "EJE", "MIT", "FIN"})
    void elTextoDeUsuarioEsTextoYNoSeEvaluaNiRompeElDocumento(TipoInforme tipo) throws Exception {
        byte[] docx = renderizar(OfimaticaPrueba.snapshotConFormulas(tipo, false), false);
        String texto = texto(docx);
        assertThat(texto).contains("=HYPERLINK(\"http://x\")").contains("+cmd").contains("-1+1").contains("@SUM(A1)")
            .contains("=code").contains("<script>alert(1)</script>");
        assertThat(texto).doesNotContain("\u0000").doesNotContain("\u0007").doesNotContain("\u001b");
        if (tipo != TipoInforme.EJE) { // el ejecutivo solo lista los cinco riesgos principales
            assertThat(texto).contains("Control").contains("fin").contains("suelto");
        }
        // Se vuelve a abrir sin error: el XML es válido.
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertThat(d.getParagraphs()).isNotEmpty();
        }
    }

    @Test
    void laNarrativaConEnlacesEImagenesSeMuestraSoloComoTexto() throws Exception {
        InformeSnapshot base = DatosDemo.snapshot(TipoInforme.EJE, false);
        var narrativa = new java.util.LinkedHashMap<>(base.narrativa());
        narrativa.put("resumenEjecutivo", "[clic](javascript:alert(1)) ![x](http://attacker/x.png) "
            + "<img src=\"file:///etc/passwd\"> [ok](https://proveedor.ejemplo/a)");
        byte[] docx = renderizar(new InformeSnapshot(base.schemaVersion(), base.plantillaVersion(), base.generadoEn(),
            base.informe(), base.organizacion(), base.proyecto(), base.auditor(), base.metricas(), base.areas(),
            base.anexoA(), base.trazabilidad(), base.historial(), narrativa, base.referenciasApa()), false);
        OfimaticaPrueba.verificarContenedorSinRiesgos(docx);
        String texto = texto(docx);
        assertThat(texto).doesNotContain("javascript:").doesNotContain("attacker");
        assertThat(texto).contains("clic");
        assertThat(OfimaticaPrueba.entradas(docx)).filteredOn(n -> n.startsWith("word/media/")).hasSize(1);
    }

    @Test
    void unaCopiaNoPublicadaSinNarrativaMarcaLosFaltantes() throws Exception {
        String texto = texto(renderizar(DatosDemo.snapshot(TipoInforme.EJE, true, false), true));
        assertThat(texto).contains("[PENDIENTE DE COMPLETAR POR EL AUDITOR]");
    }

    @Test
    void unProyectoSinHallazgosSeRenderizaEnTodosLosTipos() throws Exception {
        for (TipoInforme tipo : TIPOS_DOCX) {
            String texto = texto(renderizar(DatosDemo.snapshotSinHallazgos(tipo), false));
            assertThat(texto).as(tipo.name()).contains("CONFIDENCIAL");
        }
    }

    // ---------------------------------------------------------------- registro y compatibilidad

    @Test
    void soloSoportaLosTiposConDocxYRechazaElResto() {
        RenderizadorDocx r = renderizador();
        assertThat(r.formato()).isEqualTo(FormatoInforme.DOCX);
        for (TipoInforme tipo : TipoInforme.values()) {
            assertThat(r.soporta(tipo)).as(tipo.name()).isEqualTo(tipo != TipoInforme.MR);
        }
        assertThatThrownBy(() -> r.render(DatosDemo.snapshot(TipoInforme.MR, false), new OpcionesRender(false),
            new ByteArrayOutputStream())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mismoSnapshotMismoContenido() throws Exception {
        InformeSnapshot snapshot = DatosDemo.snapshot(TipoInforme.TEC, false);
        assertThat(texto(renderizar(snapshot, false))).isEqualTo(texto(renderizar(snapshot, false)));
    }
}

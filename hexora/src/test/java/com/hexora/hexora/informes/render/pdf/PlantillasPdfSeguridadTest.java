package com.hexora.hexora.informes.render.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.markdown.MarkdownSaneado;
import com.hexora.hexora.informes.render.DatosDemo;
import com.hexora.hexora.informes.render.OpcionesRender;
import com.hexora.hexora.informes.render.TextosInforme;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.FuenteArea;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.FuenteCve;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.FuenteVulnerabilidad;
import com.hexora.hexora.informes.snapshot.InformeSnapshot;
import com.hexora.hexora.informes.snapshot.ReferenciasApa;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Seguridad del PDF: el texto de usuario nunca se interpreta como marcado, las plantillas no pueden cargar nada
 * externo y lo único que se inserta sin escapar es la narrativa ya saneada, en un solo punto.
 */
class PlantillasPdfSeguridadTest {

    private static final Path PLANTILLAS = Path.of("src/main/resources/informes/pdf/plantillas");
    private static final Path RECURSOS_PDF = Path.of("src/main/resources/informes/pdf");

    private static final String HOSTIL = "<script>alert(1)</script> & \"comillas\" 'simples' ${7*7} [[${1+1}]] __${7*7}__ "
        + "<img src=\"http://169.254.169.254/x\"/> </td></tr></table><h1>inyectado</h1> th:text=\"x\" javascript:alert(1)";

    private final MotorPlantillasPdf motor = new MotorPlantillasPdf();
    private final ConstructorVistaInforme vistas = new ConstructorVistaInforme(new MarkdownSaneado(20000), new TextosInforme());

    private static String normalizar(String texto) {
        return texto.replaceAll("\\s+", " ");
    }

    private static List<Path> plantillas() throws IOException {
        try (Stream<Path> archivos = Files.walk(PLANTILLAS)) {
            return archivos.filter(p -> p.toString().endsWith(".html")).toList();
        }
    }

    // ---------------------------------------------------------------- el código de las plantillas

    @Test
    void soloHayUnaInsercionSinEscaparYEstaEnElFragmentoDeNarrativa() throws IOException {
        int total = 0;
        for (Path plantilla : plantillas()) {
            String contenido = Files.readString(plantilla);
            int usos = contenido.split("th:utext=", -1).length - 1;
            total += usos;
            if (plantilla.getFileName().toString().equals("narrativa.html")) {
                assertThat(usos).as("narrativa.html").isEqualTo(1);
                assertThat(contenido).contains("ÚNICO punto del documento donde se inserta marcado sin escapar");
            } else {
                assertThat(usos).as(plantilla.toString()).isZero();
            }
        }
        assertThat(total).isEqualTo(1);
    }

    @Test
    void lasPlantillasNoUsanEstilosEnLineaScriptsNiExpresionesEnLinea() throws IOException {
        for (Path plantilla : plantillas()) {
            String contenido = Files.readString(plantilla);
            // Se ignoran los comentarios de plantilla: lo que importa es el marcado y los atributos.
            String sinComentarios = contenido.replaceAll("(?s)<!--.*?-->", "");
            String nombre = plantilla.toString();
            assertThat(sinComentarios).as(nombre).doesNotContain(" style=").doesNotContain("<script")
                .doesNotContain("th:inline").doesNotContain("[[").doesNotContain("[(")
                .doesNotContain("javascript:").doesNotContain(" onclick=").doesNotContain("th:utext=\"${#")
                .doesNotContain("<iframe").doesNotContain("<object").doesNotContain("<embed");
        }
    }

    @Test
    void lasPlantillasYLosEstilosNoDeclaranRecursosExternos() throws IOException {
        Pattern externo = Pattern.compile("(?i)(https?:|file:|ftp:|data:|//[a-z0-9])");
        for (Path plantilla : plantillas()) {
            String contenido = Files.readString(plantilla).replaceAll("(?s)<!--.*?-->", "")
                .replaceAll("xmlns(:\\w+)?=\"[^\"]*\"", "");
            Matcher m = externo.matcher(contenido);
            assertThat(m.find()).as("recurso externo en " + plantilla + (m.hitEnd() ? "" : ": " + m.group())).isFalse();
        }
        for (String hoja : List.of("informe.css", "informe-horizontal.css")) {
            String css = Files.readString(RECURSOS_PDF.resolve(hoja));
            assertThat(css).as(hoja).doesNotContainIgnoringCase("@import").doesNotContainIgnoringCase("http:")
                .doesNotContainIgnoringCase("https:").doesNotContainIgnoringCase("file:")
                .doesNotContainIgnoringCase("data:");
            assertThat(css).as(hoja + " solo carga recursos por url() si son de classpath")
                .doesNotContainPattern("(?i)url\\((?!\\s*['\"]?classpath:informes/)");
        }
    }

    @Test
    void elUnicoRecursoQueCarganLasPlantillasEsElLogoYLasHojasDeEstilo() throws IOException {
        for (Path plantilla : plantillas()) {
            Matcher m = Pattern.compile("(?:src|href)=\"(classpath:[^\"]+)\"").matcher(Files.readString(plantilla));
            while (m.find()) {
                assertThat(m.group(1)).matches("classpath:informes/pdf/(img/logo-tinyhat\\.png|informe(-horizontal)?\\.css)");
                assertThat(ResolvedorRecursosSeguro.rutaValida(m.group(1).substring("classpath:".length()))).isTrue();
            }
        }
    }

    // ---------------------------------------------------------------- datos de usuario hostiles

    private InformeSnapshot snapshotHostil(TipoInforme tipo) {
        FuenteArea area = new FuenteArea("a-1", "Área " + HOSTIL, "Descripción " + HOSTIL);
        FuenteCve cve = new FuenteCve(new BigDecimal("9.8"), LocalDateTime.of(2099, 1, 1, 0, 0),
            "https://proveedor.ejemplo/" + "x\nhttps://proveedor.ejemplo/\"onmouseover=\"alert(1)");
        FuenteVulnerabilidad v = new FuenteVulnerabilidad("abcdef00-0000-4000-8000-000000000001", area.clave(),
            "Título " + HOSTIL, 9, 9, 9, 9, "Abierta", "Mitigación " + HOSTIL, "CVE-2099-0001", cve);
        Map<String, String> narrativa = new java.util.LinkedHashMap<>();
        for (TipoInforme.SeccionNarrativa seccion : tipo.secciones()) {
            narrativa.put(seccion.clave(), "# Título\n" + HOSTIL + "\n\n[x](javascript:alert(1)) ![i](http://evil/x.png)\n\n"
                + "<div onclick=\"x()\">bloque</div>\n\n| a | b |\n|---|---|\n| <script>1</script> | 2 |");
        }
        return com.hexora.hexora.informes.snapshot.ConstructorSnapshot.construir(
            new com.hexora.hexora.informes.snapshot.ConstructorSnapshot.Entrada(tipo, 1, "PUBLICADO", false, 2026,
                DatosDemo.ORG_UUID_PUBLICO, DatosDemo.PRY_UUID_PUBLICO, "Organización " + HOSTIL,
                "Proyecto " + HOSTIL, java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 2),
                "Auditor " + HOSTIL, List.of(area), List.of(v), narrativa, List.of(), DatosDemo.EMISION, "2026.1",
                1000, ReferenciasApa.desdeClasspath()));
    }

    @ParameterizedTest
    @EnumSource(TipoInforme.class)
    void elTextoHostilSaleEscapadoYNoSeInterpretaNiComoMarcadoNiComoExpresion(TipoInforme tipo) throws Exception {
        InformeSnapshot snapshot = snapshotHostil(tipo);
        String html = motor.procesar(MotorPlantillasPdf.plantillaDe(tipo), vistas.construir(snapshot, OpcionesRender.PUBLICADO));

        assertThat(html.toLowerCase()).doesNotContain("<script").doesNotContain("<img src=\"http")
            .doesNotContain("<iframe").doesNotContain("onclick=\"x()\"").doesNotContain("<h1>inyectado");
        // Las expresiones del usuario se imprimen tal cual: no se evalúan.
        assertThat(html).contains("${7*7}").contains("[[${1+1}]]").contains("__${7*7}__");
        assertThat(html).doesNotContain(">49<").doesNotContain(" 49 ");
        // Lo escapado conserva el contenido como texto.
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");

        byte[] pdf = MuestrasPdfTest.renderizar(snapshot, false);
        String texto = normalizar(PdfPrueba.texto(pdf));
        assertThat(texto).contains("<script>alert(1)</script>").contains("${7*7}").contains("[[${1+1}]]");
        assertThat(texto).doesNotContain("inyectado ").doesNotContain("evil/x.png");
    }

    @ParameterizedTest
    @EnumSource(TipoInforme.class)
    void elDocumentoHostilNoIntentaCargarNingunRecurso(TipoInforme tipo) throws Exception {
        InformeSnapshot snapshot = snapshotHostil(tipo);
        String html = MarcadoresPdf.agregar(RenderizadorPdf.sinCaracteresInvalidosEnXml(
            motor.procesar(MotorPlantillasPdf.plantillaDe(tipo), vistas.construir(snapshot, OpcionesRender.PUBLICADO))));
        VerificadorRecursosHtml.verificar(html);

        ResolvedorRecursosSeguro resolvedor = new ResolvedorRecursosSeguro();
        GeneradorPdf.generar(html, resolvedor, new ByteArrayOutputStream());
        assertThat(resolvedor.rechazados()).as("el documento no debe pedir ningún recurso ajeno").isEmpty();
        assertThat(resolvedor.resueltos()).isNotEmpty().allMatch(ruta -> ruta.startsWith("informes/"));
    }

    @Test
    void loscaracteresDeControlLasPalabrasInterminablesYLosEmojisNoRompenElDocumento() throws Exception {
        FuenteArea area = new FuenteArea("a-1", "Área\u0000\u0001 con control \uFFFE", null);
        FuenteVulnerabilidad v = new FuenteVulnerabilidad("abcdef00-0000-4000-8000-000000000001", area.clave(),
            "Título " + "A".repeat(3000) + " 😀 \u202Eoculto\u202C \uD800 suelto", 5, 5, 5, 5, "Abierta",
            "Mitigación\u0008 con " + "palabra".repeat(500), null, null);
        for (TipoInforme tipo : TipoInforme.values()) {
            InformeSnapshot s = com.hexora.hexora.informes.snapshot.ConstructorSnapshot.construir(
                new com.hexora.hexora.informes.snapshot.ConstructorSnapshot.Entrada(tipo, 1, "PUBLICADO", false, 2026,
                    DatosDemo.ORG_UUID_PUBLICO, DatosDemo.PRY_UUID_PUBLICO, "Org\u0000", "Proyecto\u0007",
                    java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 2), "Aud", List.of(area),
                    List.of(v), DatosDemo.narrativa(tipo), List.of(), DatosDemo.EMISION, "2026.1", 1000,
                    ReferenciasApa.desdeClasspath()));
            assertThatCode(() -> MuestrasPdfTest.renderizar(s, false)).as(tipo.name()).doesNotThrowAnyException();
        }
    }

    // ---------------------------------------------------------------- verificador de recursos del XHTML

    @ParameterizedTest
    @ValueSource(strings = {
        "<img src=\"http://example.org/x.png\"/>", "<img src='https://example.org/x.png'/>",
        "<link rel=\"stylesheet\" href=\"file:///C:/Windows/win.ini\"/>", "<img src=\"data:image/png;base64,AAAA\"/>",
        "<a href=\"javascript:alert(1)\">x</a>", "<img src=\"../secreto.png\"/>", "<img src=\"//example.org/x.png\"/>",
        "<img src=\"classpath:application.properties\"/>", "<img src=\"classpath:informes/../application.properties\"/>",
        "<p>x</p><style>@import url(http://example.org/x.css);</style>",
        "<style>body { background: url(http://example.org/x.png); }</style>",
        "<style>body { background: url('file:///etc/passwd'); }</style>",
        "<object data=\"http://example.org/x.swf\"></object>", "<video poster=\"http://example.org/x.png\"></video>",
        "<form action=\"http://example.org/\"></form>", "<img SRC=\"HTTP://EXAMPLE.ORG/X\"/>"
    })
    void elVerificadorRechazaCualquierRecursoQueNoSeaDeClasspathInformes(String fragmento) {
        assertThatThrownBy(() -> VerificadorRecursosHtml.verificar("<html><body>" + fragmento + "</body></html>"))
            .isInstanceOf(IllegalStateException.class).hasMessage("La plantilla declara un recurso no permitido");
    }

    @Test
    void elVerificadorAceptaAnclasInternasYLosRecursosDeInformes() {
        assertThatCode(() -> VerificadorRecursosHtml.verificar("""
            <html xmlns="http://www.w3.org/1999/xhtml"><head>
            <link rel="stylesheet" href="classpath:informes/pdf/informe.css"/></head><body>
            <a href="#sec-1">ir</a><img src="classpath:informes/pdf/img/logo-tinyhat.png"/>
            <p>texto con src=&quot;http://x&quot; escapado y url(http://x) en prosa</p>
            </body></html>""")).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------- marcadores

    @Test
    void losMarcadoresAnidanElNivelDosEnElNivelUnoYAgreganIds() {
        String xhtml = "<html><head><title>t</title></head><body><h1>Uno</h1><h2 id=\"existente\">Dos A</h2>"
            + "<h2>Dos &amp; B</h2><h3>Tres</h3><h1>Otro</h1></body></html>";
        String salida = MarcadoresPdf.agregar(xhtml);
        assertThat(salida).contains("<h1 id=\"marcador-1\">Uno</h1>").contains("<h2 id=\"existente\">Dos A</h2>")
            .contains("<h2 id=\"marcador-2\">Dos &amp; B</h2>").contains("<h3>Tres</h3>")
            .contains("<bookmarks><bookmark name=\"Uno\" href=\"#marcador-1\">"
                + "<bookmark name=\"Dos A\" href=\"#existente\"/><bookmark name=\"Dos &amp; B\" href=\"#marcador-2\"/>"
                + "</bookmark><bookmark name=\"Otro\" href=\"#marcador-3\"></bookmark></bookmarks></head>");
        assertThat(MarcadoresPdf.agregar("<html><head></head><body><p>sin títulos</p></body></html>"))
            .doesNotContain("<bookmarks>");
    }

    @Test
    void sinCaracteresInvalidosEnXmlConservaTildesEmojisYSaltos() {
        assertThat(RenderizadorPdf.sinCaracteresInvalidosEnXml("a\u0000b\u0008c\td\ne\rñ😀\uFFFE\uD800"))
            .isEqualTo("abc\td\ne\rñ😀");
    }
}

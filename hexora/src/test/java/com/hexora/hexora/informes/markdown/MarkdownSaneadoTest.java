package com.hexora.hexora.informes.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexora.hexora.informes.markdown.BloqueNarrativa.BloqueCodigo;
import com.hexora.hexora.informes.markdown.BloqueNarrativa.Cita;
import com.hexora.hexora.informes.markdown.BloqueNarrativa.Encabezado;
import com.hexora.hexora.informes.markdown.BloqueNarrativa.Fragmento;
import com.hexora.hexora.informes.markdown.BloqueNarrativa.Lista;
import com.hexora.hexora.informes.markdown.BloqueNarrativa.Parrafo;
import com.hexora.hexora.informes.markdown.BloqueNarrativa.Separador;
import com.hexora.hexora.informes.markdown.BloqueNarrativa.Tabla;
import com.hexora.hexora.informes.validacion.InformeValidacionException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Markdown de la narrativa: lista blanca de nodos y contenido hostil (§5 y §11). */
class MarkdownSaneadoTest {

    /** Las únicas etiquetas que puede contener el HTML resultante. */
    private static final Set<String> ETIQUETAS_PERMITIDAS = Set.of(
        "p", "h3", "h4", "strong", "em", "ul", "ol", "li", "code", "pre", "blockquote", "table", "thead", "tbody",
        "tr", "th", "td", "br", "hr");

    private final MarkdownSaneado markdown = new MarkdownSaneado(20000);

    /** Todo lo que el HTML contiene como etiqueta real (lo escapado como {@code &lt;} no cuenta). */
    private static Set<String> etiquetas(String html) {
        Set<String> nombres = new HashSet<>();
        Matcher m = Pattern.compile("<\\s*/?\\s*([a-zA-Z][a-zA-Z0-9]*)").matcher(html);
        while (m.find()) {
            nombres.add(m.group(1).toLowerCase());
        }
        return nombres;
    }

    /** Ninguna etiqueta del resultado lleva atributos (ni href, ni src, ni style, ni on*). */
    private static void sinAtributos(String html) {
        assertThat(Pattern.compile("<\\s*[a-zA-Z][a-zA-Z0-9]*\\s+[^>]*[=\"']").matcher(html).find())
            .as("hay atributos en: " + html).isFalse();
    }

    private void assertSeguro(String entrada) {
        NarrativaSaneada n = markdown.analizar(entrada);
        assertThat(ETIQUETAS_PERMITIDAS).containsAll(etiquetas(n.html()));
        sinAtributos(n.html());
        String minusculas = n.html().toLowerCase();
        assertThat(minusculas).doesNotContain("<script").doesNotContain("<img").doesNotContain("<a ")
            .doesNotContain("<iframe").doesNotContain("<object").doesNotContain("<embed").doesNotContain("<link")
            .doesNotContain("<style").doesNotContain("<svg").doesNotContain("<meta").doesNotContain("<form");
        // Nota: palabras como "src=" o "javascript:" pueden aparecer como TEXTO escapado (por ejemplo &lt;img src=...);
        // lo que importa es que no haya etiquetas fuera de la lista blanca ni atributos en ninguna etiqueta real.
    }

    // ---------------------------------------------------------------- los siete casos de §11

    @Test
    void scriptQuedaEscapadoComoTexto() {
        NarrativaSaneada n = markdown.analizar("<script>alert(1)</script>");
        assertSeguro("<script>alert(1)</script>");
        assertThat(n.html()).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(n.textoPlano()).isEqualTo("<script>alert(1)</script>");
    }

    @Test
    void imagenConFileQuedaEscapada() {
        String entrada = "<img src=\"file:///etc/passwd\">";
        assertSeguro(entrada);
        assertThat(markdown.analizar(entrada).html()).contains("&lt;img").contains("&gt;");
    }

    @Test
    void imagenConMetadatosDeNubeQuedaEscapada() {
        String entrada = "<img src=\"http://169.254.169.254/latest/meta-data/\">";
        assertSeguro(entrada);
        assertThat(etiquetas(markdown.analizar(entrada).html())).doesNotContain("img");
    }

    @Test
    void enlaceJavascriptSoloDejaLaEtiqueta() {
        NarrativaSaneada n = markdown.analizar("[x](javascript:alert(1))");
        assertSeguro("[x](javascript:alert(1))");
        assertThat(n.textoPlano()).isEqualTo("x");
        assertThat(n.html()).isEqualTo("<p>x</p>");
    }

    @Test
    void imagenMarkdownSeElimina() {
        NarrativaSaneada n = markdown.analizar("antes ![x](http://attacker/x.png) después");
        assertSeguro("antes ![x](http://attacker/x.png) después");
        assertThat(n.html()).doesNotContain("attacker").doesNotContain("x.png");
        assertThat(n.textoPlano()).isEqualTo("antes  después");
        assertThat(markdown.analizar("![x](http://attacker/x.png)").textoPlano()).isEmpty();
    }

    @Test
    void htmlEnBloqueQuedaEscapado() {
        String entrada = "<div onclick=\"robar()\">\n<p style=\"color:red\">hola</p>\n</div>\n\nTexto normal";
        assertSeguro(entrada);
        NarrativaSaneada n = markdown.analizar(entrada);
        assertThat(n.html()).contains("&lt;div onclick=&quot;robar()&quot;&gt;").contains("<p>Texto normal</p>");
        assertThat(n.textoPlano()).contains("<div onclick=\"robar()\">").contains("Texto normal");
    }

    @Test
    void elEncabezadoDeNivelUnoSeBajaANivelTres() {
        NarrativaSaneada n = markdown.analizar("# Título");
        assertThat(n.html()).isEqualTo("<h3>Título</h3>");
        assertThat(n.bloques()).containsExactly(new Encabezado(3, List.of(Fragmento.plano("Título"))));
    }

    // ---------------------------------------------------------------- más contenido hostil

    @ParameterizedTest
    @ValueSource(strings = {
        "<iframe src=\"http://evil.example/\"></iframe>",
        "<object data=\"http://evil.example/x.swf\"></object>",
        "<embed src=\"http://evil.example/x\">",
        "<svg onload=alert(1)>",
        "<style>@import url(http://evil.example/x.css);</style>",
        "<link rel=\"stylesheet\" href=\"http://evil.example/x.css\">",
        "<meta http-equiv=\"refresh\" content=\"0;url=http://evil.example\">",
        "<a href=\"javascript:alert(1)\">clic</a>",
        "texto <b onmouseover=\"x()\">en línea</b> texto",
        "<!-- comentario <script>alert(1)</script> -->",
        "<![CDATA[ <script>alert(1)</script> ]]>",
        "[a](data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==)",
        "[a](vbscript:msgbox(1))",
        "[a](file:///etc/passwd)",
        "![a](javascript:alert(1))",
        "![a](data:image/svg+xml;base64,PHN2Zy8+)",
        "[![alt](http://evil.example/i.png)](http://evil.example/)",
        "<http://evil.example/track?x=1>",
        "[ref]: javascript:alert(1)\n\n[usar][ref]",
        "```\"><script>alert(1)</script>\ncódigo\n```",
        "```html\n<script>alert(1)</script>\n```",
        "| a | b |\n|---|---|\n| <script>alert(1)</script> | <img src=x onerror=alert(1)> |",
        "- <script>alert(1)</script>\n- 2. <img src=x>",
        "> <img src=x onerror=alert(1)>",
        "&lt;script&gt;alert(1)&lt;/script&gt;",
        "<scr<script>ipt>alert(1)</scr</script>ipt>"
    })
    void ningunaEntradaHostilGeneraEtiquetasNiAtributosPeligrosos(String entrada) {
        assertSeguro(entrada);
    }

    @Test
    void elEnlaceNormalSeMuestraComoTextoConSuUrlYSinHipervinculo() {
        NarrativaSaneada n = markdown.analizar("Vea [la guía](https://example.org/guia) y <https://example.org/otra>.");
        assertThat(n.textoPlano()).isEqualTo("Vea la guía (https://example.org/guia) y https://example.org/otra.");
        assertThat(n.html()).isEqualTo("<p>Vea la guía (https://example.org/guia) y https://example.org/otra.</p>");
        assertSeguro("Vea [la guía](https://example.org/guia)");
    }

    @Test
    void mailtoSeMuestraYOtrosEsquemasSoloDejanLaEtiqueta() {
        assertThat(markdown.analizar("[escribir](mailto:a@example.org)").textoPlano())
            .isEqualTo("escribir (mailto:a@example.org)");
        assertThat(markdown.analizar("[bajar](ftp://example.org/x)").textoPlano()).isEqualTo("bajar");
        assertThat(markdown.analizar("[sin](//example.org/x)").textoPlano()).isEqualTo("sin");
    }

    @Test
    void laEtiquetaDelEnlaceConserva_elTextoPeroNoElFormatoPeligroso() {
        assertThat(markdown.analizar("[**negrita** y `código`](https://example.org)").textoPlano())
            .isEqualTo("negrita y código (https://example.org)");
    }

    @Test
    void textoConCaracteresEspecialesSeEscapa() {
        NarrativaSaneada n = markdown.analizar("a < b && c > d \"comillas\" 'simples'");
        assertThat(n.html()).isEqualTo("<p>a &lt; b &amp;&amp; c &gt; d &quot;comillas&quot; 'simples'</p>");
        assertThat(n.textoPlano()).isEqualTo("a < b && c > d \"comillas\" 'simples'");
    }

    @Test
    void laAnidacionExtremaSeRechazaSinDesbordarLaPila() {
        String citas = ">".repeat(5000) + " hola";
        assertThatThrownBy(() -> markdown.analizar(citas)).isInstanceOf(InformeValidacionException.class)
            .hasMessageContaining("anidación");
        // Una anidación razonable sí pasa.
        assertThat(markdown.analizar("> > > texto").textoPlano()).isEqualTo("texto");
        assertThatCode(() -> markdown.analizar("- a\n  - b\n    - c\n      - d")).doesNotThrowAnyException();
    }

    @Test
    void unTextoLargoEnElLimiteSeProcesaRapido() {
        String parrafo = "Texto con **negrita**, *cursiva* y `código`. ".repeat(440); // ~19.8 mil caracteres
        long inicio = System.nanoTime();
        NarrativaSaneada n = markdown.analizar(parrafo);
        long milisegundos = (System.nanoTime() - inicio) / 1_000_000;
        assertThat(parrafo.length()).isLessThanOrEqualTo(20000);
        assertThat(n.estaVacia()).isFalse();
        assertThat(milisegundos).as("ms").isLessThan(2000);
    }

    // ---------------------------------------------------------------- niveles de encabezado

    @ParameterizedTest
    @ValueSource(strings = {"# t", "## t", "### t"})
    void losNivelesUnoADosSeBajanATresYElTresSeConserva(String entrada) {
        assertThat(markdown.analizar(entrada).html()).isEqualTo("<h3>t</h3>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"#### t", "##### t", "###### t"})
    void losNivelesCuatroASeisQuedanEnCuatro(String entrada) {
        assertThat(markdown.analizar(entrada).html()).isEqualTo("<h4>t</h4>");
    }

    // ---------------------------------------------------------------- nodos permitidos

    @Test
    void formatoBasicoSeConservaEnLasTresSalidas() {
        NarrativaSaneada n = markdown.analizar("Hay **negrita**, *cursiva* y `código` aquí.");
        assertThat(n.html()).isEqualTo("<p>Hay <strong>negrita</strong>, <em>cursiva</em> y <code>código</code> aquí.</p>");
        assertThat(n.textoPlano()).isEqualTo("Hay negrita, cursiva y código aquí.");
        assertThat(n.bloques()).containsExactly(new Parrafo(List.of(
            new Fragmento("Hay ", false, false, false), new Fragmento("negrita", true, false, false),
            new Fragmento(", ", false, false, false), new Fragmento("cursiva", false, true, false),
            new Fragmento(" y ", false, false, false), new Fragmento("código", false, false, true),
            new Fragmento(" aquí.", false, false, false))));
    }

    @Test
    void listasConViñetasYNumeradas() {
        NarrativaSaneada n = markdown.analizar("- uno\n- dos\n\n1. primero\n2. segundo");
        assertThat(n.html()).contains("<ul>").contains("<li>uno</li>").contains("<ol>").contains("<li>segundo</li>");
        assertThat(n.textoPlano()).isEqualTo("- uno\n- dos\n\n1. primero\n2. segundo");
        assertThat(n.bloques()).hasSize(2);
        Lista viñetas = (Lista) n.bloques().get(0);
        assertThat(viñetas.ordenada()).isFalse();
        assertThat(viñetas.elementos()).hasSize(2);
        assertThat(((Lista) n.bloques().get(1)).ordenada()).isTrue();
    }

    @Test
    void codigoEnBloqueSeConservaComoTexto() {
        NarrativaSaneada n = markdown.analizar("```\nselect * from t where a < 1;\n```");
        assertThat(n.html()).isEqualTo("<pre><code>select * from t where a &lt; 1;\n</code></pre>");
        assertThat(n.textoPlano()).isEqualTo("select * from t where a < 1;");
        assertThat(n.bloques()).containsExactly(new BloqueCodigo("select * from t where a < 1;"));
        assertThat(markdown.analizar("    codigo indentado").bloques()).containsExactly(new BloqueCodigo("codigo indentado"));
    }

    @Test
    void citasYSeparadores() {
        NarrativaSaneada n = markdown.analizar("> cita\n\n---\n\ntexto");
        assertThat(n.html()).contains("<blockquote>").contains("<hr />");
        assertThat(n.bloques().get(0)).isInstanceOf(Cita.class);
        assertThat(n.bloques().get(1)).isEqualTo(new Separador());
    }

    @Test
    void tablasGfmSeConservanSinAtributosDeAlineacion() {
        NarrativaSaneada n = markdown.analizar("| Hallazgo | Nivel |\n|:--|--:|\n| SQL | **Alto** |\n| XSS | Medio |");
        assertThat(n.html()).contains("<table>").contains("<th>Hallazgo</th>").contains("<td>SQL</td>")
            .contains("<strong>Alto</strong>");
        sinAtributos(n.html());
        assertThat(n.html()).doesNotContain("style").doesNotContain("align");
        assertThat(n.textoPlano()).isEqualTo("Hallazgo | Nivel\nSQL | Alto\nXSS | Medio");
        Tabla tabla = (Tabla) n.bloques().get(0);
        assertThat(tabla.conEncabezado()).isTrue();
        assertThat(tabla.filas()).hasSize(3);
        assertThat(tabla.filas().get(1).get(1)).containsExactly(new Fragmento("Alto", true, false, false));
    }

    @Test
    void saltosDeLineaBlandosYDuros() {
        NarrativaSaneada n = markdown.analizar("línea uno\nlínea dos  \nlínea tres");
        assertThat(n.textoPlano()).isEqualTo("línea uno línea dos\nlínea tres");
        Parrafo p = (Parrafo) n.bloques().get(0);
        assertThat(p.fragmentos()).anyMatch(Fragmento::esSaltoDeLinea);
        assertThat(n.html()).contains("<br />");
    }

    @Test
    void unaListaDentroDeUnaCitaYTextoMezclado() {
        NarrativaSaneada n = markdown.analizar("> - a\n> - b\n\nfin");
        assertSeguro("> - a\n> - b\n\nfin");
        assertThat(n.bloques()).hasSize(2);
        assertThat(((Cita) n.bloques().get(0)).bloques().get(0)).isInstanceOf(Lista.class);
    }

    @Test
    void tildesEnYeSeConservanEnLasTresSalidas() {
        NarrativaSaneada n = markdown.analizar("Contraseña débil: ñandú ✓ áéíóú");
        assertThat(n.html()).isEqualTo("<p>Contraseña débil: ñandú ✓ áéíóú</p>");
        assertThat(n.textoPlano()).isEqualTo("Contraseña débil: ñandú ✓ áéíóú");
    }

    @Test
    void textoVacioONuloDaUnaNarrativaVacia() {
        assertThat(markdown.analizar(null)).isSameAs(NarrativaSaneada.VACIA);
        assertThat(markdown.analizar("")).isSameAs(NarrativaSaneada.VACIA);
        assertThat(markdown.analizar("   \n  ")).isSameAs(NarrativaSaneada.VACIA);
        assertThat(NarrativaSaneada.VACIA.estaVacia()).isTrue();
        assertThat(markdown.analizar("hola").estaVacia()).isFalse();
    }

    @Test
    void elHtmlNoTieneNingunaEtiquetaFueraDeLaListaBlancaEnUnDocumentoCompleto() {
        String documento = """
            # Resumen
            Texto con **negrita**, *cursiva*, `código` y [un enlace](https://example.org).

            ## Lista
            - uno
            - dos con <b>html</b>

            1. a
            2. b

            > cita

            | h1 | h2 |
            |----|----|
            | x  | y  |

            ---

            ```
            bloque
            ```

            ![img](http://evil.example/x.png)
            """;
        assertSeguro(documento);
        assertThat(etiquetas(markdown.analizar(documento).html())).isSubsetOf(ETIQUETAS_PERMITIDAS);
    }

    // ---------------------------------------------------------------- longitud

    @Test
    void laLongitudPorSeccionEstaLimitada() {
        MarkdownSaneado corto = new MarkdownSaneado(10);
        corto.validarLongitud("resumenEjecutivo", "x".repeat(10));
        corto.validarLongitud("resumenEjecutivo", null);
        assertThatThrownBy(() -> corto.validarLongitud("resumenEjecutivo", "x".repeat(11)))
            .isInstanceOf(InformeValidacionException.class)
            .hasMessageContaining("resumenEjecutivo").hasMessageContaining("10");
    }
}

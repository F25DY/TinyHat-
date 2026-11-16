package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Las plantillas deben cumplir la política de seguridad de contenido (CSP) y los contratos de seguridad de la
 * interfaz. Lee los archivos reales (sin arrancar Spring ni base de datos), así que un cambio de frontend que los
 * rompa falla en la compilación y no en el navegador de quien lo use.
 *
 * <p>La CSP de producción es {@code default-src 'self'; script-src 'self'; style-src 'self'; font-src 'self'}: un
 * {@code <script>} en línea, un atributo {@code style}, un manejador {@code onclick} o un recurso de un CDN no se
 * ejecuta ni se carga, y la pantalla queda sin estilos o sin comportamiento.
 */
class FrontendTemplatesComplianceTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path TEMPLATES = RESOURCES.resolve("templates");
    private static final Path STATIC = RESOURCES.resolve("static");

    private static final Pattern INLINE_SCRIPT = Pattern.compile("<script(?![^>]*\\bsrc\\s*=)(?![^>]*\\bth:src\\s*=)[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLE_TAG = Pattern.compile("<style[\\s>]", Pattern.CASE_INSENSITIVE);
    private static final Pattern STYLE_ATTRIBUTE = Pattern.compile("\\sstyle\\s*=\\s*[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_HANDLER = Pattern.compile("\\son[a-z]+\\s*=\\s*[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVASCRIPT_URL = Pattern.compile("(?:href|src|action)\\s*=\\s*[\"']\\s*javascript:", Pattern.CASE_INSENSITIVE);
    /** Recursos que el navegador carga solos (no los enlaces de navegación {@code <a href>}). */
    private static final Pattern EXTERNAL_RESOURCE = Pattern.compile(
        "<(?:link|script|img|iframe|source|embed|object)\\b[^>]*\\b(?:href|src)\\s*=\\s*[\"']\\s*(?:https?:)?//", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATIC_REFERENCE = Pattern.compile(
        "@\\{\\s*(/(?:css|js|imagen|img|vendor)/[^}(\\s]+)");
    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*[\"']?([^)\"'#?]+)");
    /**
     * Identificadores numéricos internos de las entidades: las rutas y los datos de la interfaz deben usar publicId.
     * Ojo: {@code proyecto.id} y {@code dep.id} NO se prohíben porque en detalleproyecto.html son campos de
     * {@code ProyectoView}, cuyo {@code id} ya es el identificador público (el controlador lo construye con
     * {@code getPublicId()}); en las entidades, {@code getId()} sí es el número interno.
     */
    private static final Pattern NUMERIC_ID = Pattern.compile(
        "\\b(?:departamento|aud|auditoria|area|vuln|vulnerabilidad|empresa)\\.id\\b|\\bidAuditoria\\b|\\bidArea\\b|\\bidVulnerabilidad\\b");

    @Test
    void templatesRespectTheContentSecurityPolicy() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path template : templates()) {
            String html = read(template);
            check(violations, template, html, INLINE_SCRIPT, "<script> en línea (script-src 'self')");
            check(violations, template, html, STYLE_TAG, "<style> en línea (style-src 'self')");
            check(violations, template, html, STYLE_ATTRIBUTE, "atributo style=\"...\" (style-src 'self'); usa una clase CSS");
            check(violations, template, html, EVENT_HANDLER, "manejador on*=\"...\" en línea; usa un archivo de /js y atributos data-*");
            check(violations, template, html, JAVASCRIPT_URL, "URL javascript:");
            check(violations, template, html, EXTERNAL_RESOURCE, "recurso cargado desde un servidor externo; aloja el archivo en /vendor");
        }
        assertThat(violations).as("plantillas que la CSP bloquearía").isEmpty();
    }

    @Test
    void ownStylesheetsDoNotLoadAnythingFromAnotherServer() throws IOException {
        // Un @import o url() a otro servidor lo bloquea la CSP (style-src/font-src 'self') y avisa a un tercero de cada visita.
        Pattern external = Pattern.compile("@import\\s+(?:url\\(\\s*)?[\"']?\\s*(?:https?:)?//|url\\(\\s*[\"']?\\s*(?:https?:)?//", Pattern.CASE_INSENSITIVE);
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(STATIC.resolve("css"))) {
            for (Path css : files.filter(p -> p.toString().endsWith(".css")).toList()) {
                Matcher matcher = external.matcher(read(css));
                if (matcher.find()) {
                    violations.add(STATIC.relativize(css).toString().replace('\\', '/') + ": carga un recurso externo");
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void everyStaticFileReferencedByATemplateExists() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Path template : templates()) {
            Matcher matcher = STATIC_REFERENCE.matcher(read(template));
            while (matcher.find()) {
                String reference = matcher.group(1);
                if (!Files.isRegularFile(STATIC.resolve(reference.substring(1)))) {
                    missing.add(name(template) + " -> " + reference);
                }
            }
        }
        assertThat(missing).as("archivos estáticos referenciados que no existen").isEmpty();
    }

    @Test
    void vendoredStylesheetsOnlyPointToFilesThatExist() throws IOException {
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(STATIC.resolve("vendor"))) {
            for (Path css : files.filter(p -> p.toString().endsWith(".css")).toList()) {
                Matcher matcher = CSS_URL.matcher(read(css));
                while (matcher.find()) {
                    String target = matcher.group(1).trim();
                    if (target.startsWith("data:") || target.startsWith("http")) {
                        continue;
                    }
                    Path resolved = css.getParent().resolve(target).normalize();
                    // Font Awesome lista también .ttf como alternativa; solo se aloja woff2, que es lo que usan los navegadores actuales.
                    if (!Files.isRegularFile(resolved) && !target.endsWith(".ttf")) {
                        missing.add(css.getFileName() + " -> " + target);
                    }
                }
            }
        }
        assertThat(missing).as("recursos de las hojas de estilo alojadas que faltan").isEmpty();
    }

    @Test
    void templatesNeverExposeNumericIdentifiers() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path template : templates()) {
            check(violations, template, read(template), NUMERIC_ID,
                "identificador numérico interno; usa publicId (la ruta y el panel de informes exigen el identificador público)");
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void everyPostFormUsesThAction() throws IOException {
        // th:action hace que Thymeleaf añada el token CSRF automáticamente; un action="..." a mano lo omite.
        Pattern postForm = Pattern.compile("<form\\b(?=[^>]*\\bmethod\\s*=\\s*[\"']post[\"'])(?![^>]*\\bth:action\\s*=)[^>]*>", Pattern.CASE_INSENSITIVE);
        List<String> violations = new ArrayList<>();
        for (Path template : templates()) {
            check(violations, template, read(template), postForm, "<form method=\"post\"> sin th:action (no llevaría el token CSRF)");
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void logoutIsAPostFormWithCsrfNotALink() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path template : templates()) {
            String html = read(template);
            if (!html.contains("boton-cerrar-sesion")) {
                continue;
            }
            if (!html.contains("th:action=\"@{/logout}\"")) {
                violations.add(name(template) + ": 'Cerrar sesión' debe ser un <form th:action=\"@{/logout}\" method=\"post\">");
            }
            if (Pattern.compile("<a\\b[^>]*boton-cerrar-sesion", Pattern.CASE_INSENSITIVE).matcher(html).find()) {
                violations.add(name(template) + ": 'Cerrar sesión' es un enlace; un enlace GET no cierra la sesión");
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void everyPageIncludesTheSecurityHeadFragment() throws IOException {
        // Aporta el meta CSRF y security.js, que los scripts de la página usan para llamar al servidor. Una página que
        // carga scripts propios lo necesita; las que solo tienen formularios con th:action reciben el token CSRF solas.
        List<String> missing = new ArrayList<>();
        for (Path template : templates()) {
            String html = read(template);
            boolean fullPage = html.contains("<html");
            boolean fragment = template.getParent().getFileName().toString().equals("fragments");
            boolean loadsOwnScripts = html.contains("th:src=\"@{/js/");
            if (fullPage && !fragment && loadsOwnScripts && !html.contains("fragments/security-head")) {
                missing.add(name(template));
            }
        }
        assertThat(missing).as("páginas con scripts propios que no incluyen security-head").isEmpty();
    }

    @Test
    void theSidebarExistsOnlyAsASharedFragment() throws IOException {
        // Copiado en cada plantilla, el menú se desincronizó: una página se quedó sin "Cerrar sesión" y todas mostraban
        // un usuario fijo. Un solo fragmento (fragments/sidebar.html) lo evita.
        List<String> copies = new ArrayList<>();
        for (Path template : templates()) {
            if (!name(template).equals("fragments/sidebar.html") && read(template).contains("<aside")) {
                copies.add(name(template));
            }
        }
        assertThat(copies).as("plantillas con su propio menú lateral; usa el fragmento sidebar").isEmpty();
        assertThat(read(TEMPLATES.resolve("fragments").resolve("sidebar.html")))
            .contains("action=\"@{/logout}\"")
            .contains("${usuarioActual}")
            .doesNotContain("@gmail.com");
    }

    // --- utilidades ---

    private static void check(List<String> violations, Path template, String html, Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(html);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        if (count > 0) {
            violations.add(name(template) + " (" + count + "): " + message);
        }
    }

    private static List<Path> templates() throws IOException {
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            return files.filter(p -> p.toString().endsWith(".html")).sorted().toList();
        }
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String name(Path template) {
        return TEMPLATES.relativize(template).toString().replace('\\', '/');
    }
}

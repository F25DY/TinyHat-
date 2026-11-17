package com.hexora.hexora.informes.render.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** El resolvedor de recursos del PDF solo acepta classpath:informes/... (§7.1) y nada sale a la red. */
class ResolvedorRecursosSeguroTest {

    private static final String BASE = "classpath:informes/pdf/";

    @ParameterizedTest
    @ValueSource(strings = {
        "http://example.org/x.png", "https://example.org/x.png", "HTTP://EXAMPLE.ORG/X", "file:///etc/passwd",
        "file:///C:/Windows/win.ini", "ftp://example.org/x", "data:image/png;base64,iVBORw0KGgo=",
        "jar:file:/tmp/x.jar!/a.png", "javascript:alert(1)", "mailto:a@example.org", "C:\\Windows\\win.ini",
        "//example.org/x.png", "/etc/passwd", "\\\\servidor\\recurso", "../secreto.txt", "img/../../secreto.txt",
        "classpath:informes/../application.properties", "classpath:application.properties",
        "classpath:/informes/pdf/informe.css", "classpath:informes//pdf/informe.css",
        "classpath:informes/pdf/%2e%2e/application.properties", "classpath:informes/pdf/informe.css%00.png",
        "classpath:informes\\pdf\\informe.css", "classpath:informes/pdf/", "classpath:informes",
        "classpath:META-INF/MANIFEST.MF", "ClassPath:informes/pdf/informe.css", " ", ""
    })
    void rechazaTodoLoQueNoSeaClasspathInformes(String uri) {
        ResolvedorRecursosSeguro resolvedor = new ResolvedorRecursosSeguro();
        assertThat(resolvedor.resolveURI(BASE, uri)).as(uri).isNull();
        assertThat(resolvedor.rechazados()).hasSize(1);
        assertThat(resolvedor.resueltos()).isEmpty();
    }

    @Test
    void aceptaLosRecursosDeInformes() {
        ResolvedorRecursosSeguro resolvedor = new ResolvedorRecursosSeguro();
        assertThat(resolvedor.resolveURI(BASE, "classpath:informes/pdf/informe.css"))
            .isEqualTo("classpath:informes/pdf/informe.css");
        assertThat(resolvedor.resolveURI(BASE, "classpath:informes/pdf/img/logo-tinyhat.png"))
            .isEqualTo("classpath:informes/pdf/img/logo-tinyhat.png");
        assertThat(resolvedor.resolveURI(BASE, "img/logo-tinyhat.png"))
            .as("ruta relativa dentro de informes/").isEqualTo("classpath:informes/pdf/img/logo-tinyhat.png");
        assertThat(resolvedor.resolveURI(BASE, "#sec-1")).as("ancla interna").isEqualTo("#sec-1");
        assertThat(resolvedor.rechazados()).isEmpty();
    }

    @Test
    void unaRutaRelativaConUnaBaseQueNoEsDeClasspathSeRechaza() {
        ResolvedorRecursosSeguro resolvedor = new ResolvedorRecursosSeguro();
        assertThat(resolvedor.resolveURI("http://example.org/", "x.png")).isNull();
        assertThat(resolvedor.resolveURI(null, "x.png")).isNull();
        assertThat(resolvedor.resolveURI("file:///tmp/", "x.png")).isNull();
    }

    @Test
    void elRegistroAbreviaYNeutralizaCaracteresDeControl() {
        ResolvedorRecursosSeguro resolvedor = new ResolvedorRecursosSeguro();
        resolvedor.resolveURI(BASE, "http://example.org/\n\rinyectado" + "x".repeat(500));
        assertThat(resolvedor.rechazados()).singleElement().satisfies(registro -> {
            assertThat(registro).doesNotContain("\n").doesNotContain("\r");
            assertThat(registro.length()).isLessThan(100);
        });
    }

    @Test
    void laFabricaDeFlujosVuelveAValidarLaRutaYNoAbreNadaAjeno() throws IOException {
        ResolvedorRecursosSeguro resolvedor = new ResolvedorRecursosSeguro();
        assertThat(resolvedor.getUrl("classpath:informes/pdf/img/logo-tinyhat.png").getStream()).isNotNull();
        assertThat(resolvedor.getUrl("classpath:application.properties").getStream()).isNull();
        assertThat(resolvedor.getUrl("classpath:informes/../application.properties").getStream()).isNull();
        assertThat(resolvedor.getUrl("file:///etc/passwd").getStream()).isNull();
        assertThat(resolvedor.getUrl(null).getStream()).isNull();
        assertThat(resolvedor.rechazados()).hasSize(4);
    }

    // ---------------------------------------------------------------- generación con contenido hostil

    /** Servidor local que cuenta las conexiones: el renderizado no debe hacer ninguna. */
    private static final class ServidorEspia implements AutoCloseable {
        final ServerSocket socket;
        final AtomicInteger conexiones = new AtomicInteger();
        final Thread hilo;

        ServidorEspia() throws IOException {
            socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            hilo = new Thread(() -> {
                while (!socket.isClosed()) {
                    try (Socket cliente = socket.accept()) {
                        conexiones.incrementAndGet();
                    } catch (IOException cerrado) {
                        return;
                    }
                }
            });
            hilo.setDaemon(true);
            hilo.start();
        }

        int puerto() {
            return socket.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @Test
    void unDocumentoConRecursosHostilesSeGeneraSinCargarNadaNiTocarLaRed() throws Exception {
        try (ServidorEspia espia = new ServidorEspia()) {
            int p = espia.puerto();
            String html = """
                <html xmlns="http://www.w3.org/1999/xhtml"><head>
                <style>
                @import url(http://127.0.0.1:%1$d/importado.css);
                body { font-family: 'Plus Jakarta Sans'; background-image: url(http://127.0.0.1:%1$d/fondo.png); }
                .a { background: url(file:///C:/Windows/win.ini); }
                .b { background: url(../../application.properties); }
                .c { background: url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==); }
                </style>
                <link rel="stylesheet" type="text/css" href="http://127.0.0.1:%1$d/hoja.css"/>
                <link rel="stylesheet" type="text/css" href="file:///C:/Windows/win.ini"/>
                </head><body>
                <p class="a b c">Contraseña débil: ñandú áéíóú</p>
                <img src="http://127.0.0.1:%1$d/pixel.png" alt="a"/>
                <img src="http://169.254.169.254/latest/meta-data/" alt="b"/>
                <img src="file:///C:/Windows/win.ini" alt="c"/>
                <img src="../../application.properties" alt="d"/>
                <img src="classpath:application.properties" alt="e"/>
                <img src="//127.0.0.1:%1$d/x.png" alt="f"/>
                <img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==" alt="g"/>
                <img src="classpath:informes/pdf/img/logo-tinyhat.png" alt="logo permitido" width="40" height="40"/>
                </body></html>
                """.formatted(p);

            ResolvedorRecursosSeguro resolvedor = new ResolvedorRecursosSeguro();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            GeneradorPdf.generar(html, resolvedor, salida);
            Thread.sleep(300); // margen para que una conexión indebida llegara a registrarse

            byte[] pdf = salida.toByteArray();
            assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
            assertThat(espia.conexiones.get()).as("el servidor local no debe recibir ninguna petición").isZero();
            assertThat(resolvedor.rechazados()).as("rechazados: " + resolvedor.rechazados())
                .anyMatch(r -> r.startsWith("http://127.0.0.1:" + p + "/pixel.png"))
                .anyMatch(r -> r.startsWith("http://169.254.169.254"))
                .anyMatch(r -> r.startsWith("file:///C:/Windows/win.ini"))
                .anyMatch(r -> r.contains("application.properties"))
                .anyMatch(r -> r.startsWith("//127.0.0.1"));
            assertThat(resolvedor.resueltos()).contains("informes/pdf/img/logo-tinyhat.png");
            assertThat(resolvedor.resueltos()).noneMatch(r -> !r.startsWith("informes/"));
        }
    }

    @Test
    void elTextoConTildesYEnyeSeConservaEnElPdf() throws Exception {
        String html = """
            <html xmlns="http://www.w3.org/1999/xhtml"><head><style>body { font-family: 'Plus Jakarta Sans'; }</style></head>
            <body><p>Contraseña débil: ñandú, árbol, ÁÉÍÓÚ Ñ ü — «comillas»</p></body></html>
            """;
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        GeneradorPdf.generar(html, new ResolvedorRecursosSeguro(), salida);
        byte[] pdf = salida.toByteArray();
        assertThat(pdf.length).isGreaterThan(2000);
        // Texto real extraído del PDF: las tildes y la ñ llegan intactas.
        assertThat(PdfPrueba.texto(pdf).replaceAll("\s+", " "))
            .contains("Contraseña débil: ñandú, árbol, ÁÉÍÓÚ Ñ ü — «comillas»");
        // Las fuentes van incrustadas (subconjunto) y no se usa una fuente base del sistema.
        assertThat(PdfPrueba.fuentes(pdf)).isNotEmpty().allMatch(nombre -> nombre.contains("PlusJakartaSans"));
    }

    @Test
    void unHtmlMalFormadoDaUnErrorGenericoSinDetalles() {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> GeneradorPdf.generar("<html><body><p>sin cerrar</body>", new ResolvedorRecursosSeguro(), salida))
            .isInstanceOf(IOException.class).hasMessage("No se pudo generar el PDF");
    }
}

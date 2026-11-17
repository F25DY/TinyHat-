package com.hexora.hexora.informes.render.pdf;

import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.extend.FSStreamFactory;
import com.openhtmltopdf.extend.FSUriResolver;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolvedor de recursos del PDF (§7.1, obligatorio). Solo resuelve {@code classpath:informes/...}: hojas de estilo,
 * fuentes e imágenes que viajan dentro de la aplicación. Cualquier otro esquema ({@code http}, {@code https},
 * {@code file}, {@code ftp}, {@code data}, {@code jar} externo, {@code javascript}...), las rutas absolutas o
 * protocolo-relativas y las que contengan {@code ..}, barras invertidas, codificación porcentual o caracteres de
 * control se <b>rechazan</b>, se registran como advertencia y no cargan nada. Así el renderizado nunca sale a la red
 * ni lee archivos del servidor, aunque algún texto llegara a colarse en una plantilla.
 *
 * <p>Se crea una instancia por documento: {@link #rechazados()} dice qué se intentó cargar.
 */
public final class ResolvedorRecursosSeguro implements FSUriResolver, FSStreamFactory {

    public static final String ESQUEMA = "classpath:";
    public static final String RAIZ = "informes/";

    private static final Logger LOGGER = LoggerFactory.getLogger(ResolvedorRecursosSeguro.class);
    private static final Pattern TIENE_ESQUEMA = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.\\-]*:.*", Pattern.DOTALL);
    private static final Pattern RUTA_PERMITIDA = Pattern.compile("^[A-Za-z0-9._/\\-]+$");
    private static final int MAX_LONGITUD_REGISTRO = 80;

    private final List<String> rechazados = new CopyOnWriteArrayList<>();
    private final List<String> resueltos = new CopyOnWriteArrayList<>();

    @Override
    public String resolveURI(String baseUri, String uri) {
        if (uri == null || uri.isBlank()) {
            return rechazar(uri);
        }
        String limpio = uri.strip();
        if (limpio.startsWith("#")) {
            return limpio; // ancla interna del mismo documento (índice)
        }
        String ruta;
        if (limpio.startsWith(ESQUEMA)) {
            ruta = limpio.substring(ESQUEMA.length());
        } else if (TIENE_ESQUEMA.matcher(limpio).matches()) {
            return rechazar(limpio); // http, https, file, ftp, data, jar, javascript, letra de unidad...
        } else if (limpio.startsWith("/") || limpio.startsWith("\\")) {
            return rechazar(limpio);
        } else {
            String base = baseUri == null ? "" : baseUri.strip();
            if (!base.startsWith(ESQUEMA)) {
                return rechazar(limpio);
            }
            ruta = directorioDe(base.substring(ESQUEMA.length())) + limpio;
        }
        if (!rutaValida(ruta)) {
            return rechazar(limpio);
        }
        resueltos.add(ruta);
        return ESQUEMA + ruta;
    }

    /** Fábrica de flujos del protocolo {@code classpath}: vuelve a validar la ruta antes de abrir nada. */
    @Override
    public FSStream getUrl(String url) {
        if (url == null || !url.startsWith(ESQUEMA) || !rutaValida(url.substring(ESQUEMA.length()))) {
            rechazar(url);
            return new FlujoClasspath(null);
        }
        return new FlujoClasspath(url.substring(ESQUEMA.length()));
    }

    /** Lo que se intentó cargar y se rechazó (abreviado y sin caracteres de control), en orden. */
    public List<String> rechazados() {
        return List.copyOf(rechazados);
    }

    /** Rutas de classpath que sí se resolvieron. */
    public List<String> resueltos() {
        return List.copyOf(resueltos);
    }

    /** Política común: dentro de {@code informes/}, solo caracteres simples, sin subir de directorio. */
    static boolean rutaValida(String ruta) {
        return ruta != null
            && ruta.startsWith(RAIZ)
            && !ruta.contains("..")
            && !ruta.contains("//")
            && !ruta.endsWith("/")
            && RUTA_PERMITIDA.matcher(ruta).matches();
    }

    private static String directorioDe(String ruta) {
        int barra = ruta.lastIndexOf('/');
        return barra < 0 ? "" : ruta.substring(0, barra + 1);
    }

    private String rechazar(String original) {
        String registrado = abreviar(original);
        rechazados.add(registrado);
        LOGGER.warn("Recurso rechazado al generar un informe: {}", registrado);
        return null;
    }

    private static String abreviar(String texto) {
        if (texto == null) {
            return "(nulo)";
        }
        String sinControl = texto.replaceAll("[\\p{Cntrl}]", "?");
        return sinControl.length() > MAX_LONGITUD_REGISTRO
            ? sinControl.substring(0, MAX_LONGITUD_REGISTRO) + "..."
            : sinControl;
    }

    /** Flujo de un recurso del classpath; sin ruta (o inexistente) no entrega nada. */
    private static final class FlujoClasspath implements FSStream {

        private final String ruta;

        FlujoClasspath(String ruta) {
            this.ruta = ruta;
        }

        @Override
        public InputStream getStream() {
            if (ruta == null) {
                return null;
            }
            return ResolvedorRecursosSeguro.class.getClassLoader().getResourceAsStream(ruta);
        }

        @Override
        public Reader getReader() {
            InputStream flujo = getStream();
            return flujo == null ? null : new InputStreamReader(flujo, StandardCharsets.UTF_8);
        }
    }

    /** Fábrica que no entrega nada, para los protocolos que nunca deben cargarse (red, archivos). */
    static final class FabricaRechazada implements FSStreamFactory {

        private final ResolvedorRecursosSeguro resolvedor;

        FabricaRechazada(ResolvedorRecursosSeguro resolvedor) {
            this.resolvedor = resolvedor;
        }

        @Override
        public FSStream getUrl(String url) {
            resolvedor.rechazar(url);
            return new FlujoClasspath(null);
        }
    }

    /** Para pruebas: ¿falló la apertura de un flujo? */
    static boolean abre(String ruta) throws IOException {
        try (InputStream flujo = ResolvedorRecursosSeguro.class.getClassLoader().getResourceAsStream(ruta)) {
            return flujo != null;
        }
    }
}

package com.hexora.hexora.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.hexora.hexora.model.Cve;
import com.hexora.hexora.security.SecureRestClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Traduce texto de inglés a español usando la API gratuita de MyMemory.
 * Es el ÚNICO lugar que conoce al traductor: si algún día cambiamos a DeepL,
 * solo hay que modificar este archivo.
 *
 * MyMemory solo acepta ~500 bytes por consulta, por eso el texto se parte en trozos
 * (cortando por frases) y luego se vuelve a unir.
 */
@Service
public class TraductorService {

    private static final Logger log = LoggerFactory.getLogger(TraductorService.class);

    private static final String URL_MYMEMORY = "https://api.mymemory.translated.net/get";
    private static final int MAX_BYTES = 450; // el límite real es 500; dejamos margen
    private static final int MAX_TRANSLATED_CHARS = 20_000;
    private static final Pattern CVE_ID_PATTERN =
        Pattern.compile("^CVE-\\d{4}-\\d{4,19}$", Pattern.CASE_INSENSITIVE);

    private final RestClient restClient;
    private final String email;

    // MYMEMORY_EMAIL sale del .env. Es opcional: con correo el límite diario sube de 5.000 a 50.000 caracteres.
    public TraductorService(@Value("${MYMEMORY_EMAIL:}") String email) {
        this.email = email == null ? "" : email.trim();
        this.restClient = SecureRestClientFactory.create(
            URL_MYMEMORY, "api.mymemory.translated.net", 64 * 1024);
    }

    /** Traduce solo la descripción de un CVE validado procedente del catálogo NVD. */
    public Optional<String> aEspanol(Cve cve) {
        if (cve == null
            || cve.getCodigoCve() == null
            || !CVE_ID_PATTERN.matcher(cve.getCodigoCve()).matches()) {
            return Optional.empty();
        }
        String texto = cve.getDescripcion();
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        if (texto.length() > MAX_TRANSLATED_CHARS) {
            log.warn("Se omitió una descripción CVE que excede el límite de traducción");
            return Optional.empty();
        }

        StringBuilder resultado = new StringBuilder();
        for (String trozo : partir(texto)) {
            Optional<String> traducido = traducirTrozo(trozo);
            if (traducido.isEmpty()) {
                // Si falla un pedazo, no guardamos una traducción incompleta.
                return Optional.empty();
            }
            if (resultado.length() > 0) {
                resultado.append(' ');
            }
            resultado.append(traducido.get());
            if (resultado.length() > MAX_TRANSLATED_CHARS) {
                log.warn("Se omitió una respuesta de traducción que excede el límite configurado");
                return Optional.empty();
            }
        }
        return Optional.of(resultado.toString());
    }

    // ---------------------------------------------------------------
    // Una consulta a MyMemory
    // ---------------------------------------------------------------

    private Optional<String> traducirTrozo(String trozo) {
        try {
            Map<String, Object> respuesta = restClient.get()
                    .uri(uri -> {
                        uri.queryParam("q", "{q}").queryParam("langpair", "{par}");
                        if (!email.isBlank()) {
                            uri.queryParam("de", "{email}");
                        }
                        return uri.build(Map.of("q", trozo, "par", "en|es", "email", email));
                    })
                    .retrieve()
                    .onStatus(status -> status.is3xxRedirection(), (request, response) -> {
                        throw new IllegalStateException("MyMemory redirects are not accepted");
                    })
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (respuesta == null) {
                return Optional.empty();
            }

            String estado = String.valueOf(respuesta.get("responseStatus"));
            Object datos = respuesta.get("responseData");
            if (!"200".equals(estado) || !(datos instanceof Map<?, ?> mapa)) {
                log.warn("MyMemory respondió con estado {}", estado);
                return Optional.empty();
            }

            Object traducido = mapa.get("translatedText");
            if (!(traducido instanceof String t) || t.isBlank() || t.startsWith("MYMEMORY WARNING")) {
                // Pasa, por ejemplo, cuando se acaba la cuota diaria.
                log.warn("MyMemory no devolvió una traducción válida (¿cuota diaria agotada?)");
                return Optional.empty();
            }
            return Optional.of(limpiar(t));
        } catch (RestClientException | IllegalStateException e) {
            log.warn("No se pudo consultar MyMemory ({})", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    // A veces MyMemory devuelve símbolos en formato HTML (&quot; en vez de ").
    private static String limpiar(String texto) {
        return texto.replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }

    // ---------------------------------------------------------------
    // Partir el texto en trozos que quepan en una consulta
    // ---------------------------------------------------------------

    private List<String> partir(String texto) {
        List<String> trozos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();

        for (String frase : texto.trim().split("(?<=[.!?])\\s+")) {
            for (String parte : cortarFraseLarga(frase)) {
                if (actual.length() > 0 && bytes(actual) + bytes(parte) + 1 > MAX_BYTES) {
                    trozos.add(actual.toString());
                    actual.setLength(0);
                }
                if (actual.length() > 0) {
                    actual.append(' ');
                }
                actual.append(parte);
            }
        }
        if (actual.length() > 0) {
            trozos.add(actual.toString());
        }
        return trozos;
    }

    // Si una sola frase es demasiado larga, la corta por palabras.
    private List<String> cortarFraseLarga(String frase) {
        List<String> partes = new ArrayList<>();
        if (bytes(frase) <= MAX_BYTES) {
            partes.add(frase);
            return partes;
        }

        StringBuilder pedazo = new StringBuilder();
        for (String palabra : frase.split("\\s+")) {
            if (pedazo.length() > 0 && bytes(pedazo) + bytes(palabra) + 1 > MAX_BYTES) {
                partes.add(pedazo.toString());
                pedazo.setLength(0);
            }
            if (pedazo.length() > 0) {
                pedazo.append(' ');
            }
            pedazo.append(palabra);
        }
        if (pedazo.length() > 0) {
            partes.add(pedazo.toString());
        }
        return partes;
    }

    private static int bytes(CharSequence texto) {
        return texto.toString().getBytes(StandardCharsets.UTF_8).length;
    }
}

package com.hexora.hexora.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.hexora.hexora.model.Cve;
import com.hexora.hexora.repository.CveRepository;
import com.hexora.hexora.security.NvdRateLimitService;
import com.hexora.hexora.security.RateLimitExceededException;
import com.hexora.hexora.security.TinyHatNvdProperties;

/**
 * Busca CVEs. Primero mira en la base de datos local (rápido). Si no encuentra
 * nada, le pregunta a la API del NVD, guarda lo que llegue y vuelve a buscar.
 * Así la tabla "cve" se va llenando sola con lo que la gente consulta.
 */
@Service
public class CveService {

    private static final Logger log = LoggerFactory.getLogger(CveService.class);

    private static final Pattern PATRON_CVE = Pattern.compile("^CVE-\\d{4}-\\d{4,}$", Pattern.CASE_INSENSITIVE);
    private static final int MAX_ENLACES_PARCHE = 5;

    private final CveRepository cveRepository;
    private final TraductorService traductorService;
    private final RestClient restClient;
    private final String apiKey;
    private final NvdRateLimitService nvdRateLimitService;

    public CveService(CveRepository cveRepository, TraductorService traductorService,
            TinyHatNvdProperties properties, NvdRateLimitService nvdRateLimitService) {
        this.cveRepository = cveRepository;
        this.traductorService = traductorService;
        this.apiKey = properties.getApiKey();
        this.nvdRateLimitService = nvdRateLimitService;
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).build();
    }

    public List<Cve> buscar(String texto) {
        String q = texto == null ? "" : texto.trim();
        if (q.length() < 3) {
            return List.of();
        }

        List<Cve> locales = cveRepository
                .findTop10ByCodigoCveContainingIgnoreCaseOrDescripcionContainingIgnoreCase(q, q);
        if (!locales.isEmpty()) {
            return locales;
        }

        traerDesdeNvd(q);
        return cveRepository.findTop10ByCodigoCveContainingIgnoreCaseOrDescripcionContainingIgnoreCase(q, q);
    }

    /**
     * Devuelve el CVE completo, listo para rellenar el formulario:
     *  1) Si se guardó antes de la V5, completa los datos de riesgo y los enlaces de parche pidiéndolos al NVD.
     *  2) Si todavía no tiene la descripción en español, la traduce.
     * Todo se guarda, así que cada paso se hace una sola vez por CVE.
     * Si algo falla, devuelve el CVE igual con lo que tenga.
     */
    public Optional<Cve> obtenerDetalle(String codigo) {
        Optional<Cve> encontrado = cveRepository.findByCodigoCve(codigo);
        if (encontrado.isEmpty()) {
            return encontrado;
        }

        Cve cve = encontrado.get();
        boolean huboCambios = false;

        if (cve.getVersionCvss() == null) {
            for (Map<String, Object> datos : consultarNvd("cveId", cve.getCodigoCve())) {
                if (cve.getCodigoCve().equalsIgnoreCase(texto(datos.get("id")))) {
                    leerMetricas(datos, cve);
                    cve.setReferenciasParche(leerEnlacesParche(datos));
                    huboCambios = true;
                    break;
                }
            }
        }

        if (cve.getDescripcionEs() == null && cve.getDescripcion() != null) {
            Optional<String> traduccion = traductorService.aEspanol(cve.getDescripcion());
            if (traduccion.isPresent()) {
                cve.setDescripcionEs(traduccion.get());
                huboCambios = true;
            }
        }

        if (huboCambios) {
            cveRepository.save(cve);
        }
        return Optional.of(cve);
    }

    // ---------------------------------------------------------------
    // Conexión con el NVD
    // ---------------------------------------------------------------

    private void traerDesdeNvd(String q) {
        boolean esCodigo = PATRON_CVE.matcher(q).matches();
        String parametro = esCodigo ? "cveId" : "keywordSearch";
        String valor = esCodigo ? q.toUpperCase() : q;

        for (Map<String, Object> datos : consultarNvd(parametro, valor)) {
            Cve cve = convertir(datos);
            if (cve != null && cveRepository.findByCodigoCve(cve.getCodigoCve()).isEmpty()) {
                cveRepository.save(cve);
            }
        }
    }

    // Hace la consulta al NVD y devuelve la lista de CVEs (cada uno como un mapa del JSON).
    private List<Map<String, Object>> consultarNvd(String parametro, String valor) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        try {
            long retryAfter = nvdRateLimitService.tryConsume();
            if (retryAfter > 0) {
                throw new RateLimitExceededException(retryAfter);
            }
            Map<String, Object> respuesta = restClient.get()
                    .uri(uri -> uri.queryParam(parametro, "{valor}")
                            .queryParam("resultsPerPage", 10)
                            .build(valor))
                    .headers(h -> {
                        if (!apiKey.isBlank()) {
                            h.set("apiKey", apiKey);
                        }
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (respuesta != null) {
                for (Map<String, Object> item : lista(respuesta.get("vulnerabilities"))) {
                    resultado.add(mapa(item.get("cve")));
                }
            }
        } catch (RateLimitExceededException e) {
            log.warn("Consulta NVD omitida por límite local; reintento disponible en {} s", e.getRetryAfterSeconds());
        } catch (RestClientException e) {
            // Si el NVD no responde, la app conserva los resultados locales y lo registra.
            log.warn("No se pudo consultar el NVD ({}={}): {}", parametro, valor, e.getMessage());
        }
        return resultado;
    }

    // ---------------------------------------------------------------
    // Convertir la respuesta del NVD en nuestro objeto Cve
    // ---------------------------------------------------------------

    private Cve convertir(Map<String, Object> datos) {
        String codigo = texto(datos.get("id"));
        if (codigo == null) {
            return null;
        }

        Cve cve = new Cve();
        cve.setCodigoCve(codigo);
        cve.setDescripcion(leerDescripcion(datos));
        cve.setFechaPublicacion(leerFecha(datos.get("published")));
        cve.setFechaModificacion(leerFecha(datos.get("lastModified")));
        cve.setCwe(leerCwe(datos));
        leerMetricas(datos, cve);
        cve.setReferenciasParche(leerEnlacesParche(datos));
        return cve;
    }

    // Toma la descripción en inglés; si no hay, la primera que venga.
    private String leerDescripcion(Map<String, Object> datos) {
        String primera = null;
        for (Map<String, Object> d : lista(datos.get("descriptions"))) {
            String valor = texto(d.get("value"));
            if (valor == null) {
                continue;
            }
            if ("en".equals(d.get("lang"))) {
                return valor;
            }
            if (primera == null) {
                primera = valor;
            }
        }
        return primera;
    }

    // Busca el primer código que empiece con "CWE-" (ignora "NVD-CWE-Other", etc.).
    private String leerCwe(Map<String, Object> datos) {
        for (Map<String, Object> debilidad : lista(datos.get("weaknesses"))) {
            for (Map<String, Object> d : lista(debilidad.get("description"))) {
                String valor = texto(d.get("value"));
                if (valor != null && valor.startsWith("CWE-")) {
                    return valor;
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Métricas CVSS: puntaje, severidad, impactos y explotabilidad
    // ---------------------------------------------------------------

    void leerMetricas(Map<String, Object> datos, Cve cve) {
        Map<String, Object> metricas = mapa(datos.get("metrics"));
        leerPuntaje(metricas, cve);
        leerImpactos(metricas, cve);
    }

    // Prioriza la misma versión que los impactos para no mezclar evaluaciones.
    private void leerPuntaje(Map<String, Object> metricas, Cve cve) {
        String[] versiones = { "cvssMetricV31", "cvssMetricV30", "cvssMetricV2", "cvssMetricV40" };

        for (String version : versiones) {
            Map<String, Object> elegida = elegirEntrada(lista(metricas.get(version)));
            if (elegida == null) {
                continue;
            }

            Map<String, Object> cvssData = mapa(elegida.get("cvssData"));
            if (cvssData.get("baseScore") instanceof Number n) {
                cve.setPuntajeCvss(BigDecimal.valueOf(n.doubleValue()));
            }

            // En CVSS 2.0 la severidad viene afuera de cvssData.
            String severidad = texto(cvssData.get("baseSeverity"));
            if (severidad == null) {
                severidad = texto(elegida.get("baseSeverity"));
            }
            if (severidad != null) {
                cve.setSeveridad(severidad);
            }
            return;
        }
    }

    // Impacto en confidencialidad, integridad y disponibilidad, más la "explotabilidad".
    // Se prefiere la 3.1 (luego 3.0 y 2.0) porque trae la explotabilidad; la 4.0 solo trae los impactos.
    private void leerImpactos(Map<String, Object> metricas, Cve cve) {
        String[][] versiones = { { "cvssMetricV31", "3.1" }, { "cvssMetricV30", "3.0" },
                { "cvssMetricV2", "2.0" }, { "cvssMetricV40", "4.0" } };

        for (String[] version : versiones) {
            Map<String, Object> elegida = elegirEntrada(lista(metricas.get(version[0])));
            if (elegida == null) {
                continue;
            }

            Map<String, Object> cvssData = mapa(elegida.get("cvssData"));
            boolean esV4 = "4.0".equals(version[1]);

            cve.setImpactoConfidencialidad(
                    texto(cvssData.get(esV4 ? "vulnConfidentialityImpact" : "confidentialityImpact")));
            cve.setImpactoIntegridad(texto(cvssData.get(esV4 ? "vulnIntegrityImpact" : "integrityImpact")));
            cve.setImpactoDisponibilidad(
                    texto(cvssData.get(esV4 ? "vulnAvailabilityImpact" : "availabilityImpact")));

            if (elegida.get("exploitabilityScore") instanceof Number n) {
                cve.setPuntajeExplotabilidad(BigDecimal.valueOf(n.doubleValue()));
            } else {
                cve.setPuntajeExplotabilidad(null);
            }
            cve.setVersionCvss(version[1]);
            return;
        }

        // El NVD todavía no califica este CVE. Se marca para no volver a preguntarle cada vez.
        cve.setVersionCvss("NINGUNA");
    }

    // Entre varias calificaciones, prefiere la marcada como "Primary"; si no hay, la primera.
    private Map<String, Object> elegirEntrada(List<Map<String, Object>> entradas) {
        if (entradas.isEmpty()) {
            return null;
        }
        for (Map<String, Object> entrada : entradas) {
            if ("Primary".equals(entrada.get("type"))) {
                return entrada;
            }
        }
        return entradas.get(0);
    }

    // ---------------------------------------------------------------
    // Enlaces de parche o mitigación (para sugerir el plan de mitigación)
    // ---------------------------------------------------------------

    private String leerEnlacesParche(Map<String, Object> datos) {
        List<String> enlaces = new ArrayList<>();

        for (Map<String, Object> referencia : lista(datos.get("references"))) {
            String url = texto(referencia.get("url"));
            if (url == null || !url.startsWith("http") || enlaces.contains(url)) {
                continue;
            }
            if (referencia.get("tags") instanceof List<?> etiquetas
                    && (etiquetas.contains("Patch") || etiquetas.contains("Mitigation"))) {
                enlaces.add(url);
                if (enlaces.size() >= MAX_ENLACES_PARCHE) {
                    break;
                }
            }
        }
        return enlaces.isEmpty() ? null : String.join("\n", enlaces);
    }

    // ---------------------------------------------------------------
    // Ayudantes para leer el JSON sin errores si falta algún dato
    // ---------------------------------------------------------------

    private LocalDateTime leerFecha(Object valor) {
        if (valor instanceof String texto) {
            try {
                return LocalDateTime.parse(texto);
            } catch (java.time.format.DateTimeParseException e) {
                return null;
            }
        }
        return null;
    }

    // Devuelve el texto si es un texto con contenido; si no, null.
    private static String texto(Object o) {
        return o instanceof String s && !s.isBlank() ? s : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(Object o) {
        return o instanceof List ? (List<Map<String, Object>>) o : List.of();
    }
}
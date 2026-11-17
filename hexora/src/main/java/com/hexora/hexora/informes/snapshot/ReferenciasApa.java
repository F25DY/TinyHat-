package com.hexora.hexora.informes.snapshot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;

/**
 * Referencias APA 7 deterministas (§6.6): un bloque fijo ({@code informes/textos/referencias-base.md}) más una
 * entrada por cada CVE citado en el informe, en orden alfabético. La sangría francesa la aplica cada renderizador.
 *
 * <p>Formato de un CVE: {@code National Institute of Standards and Technology. ({año publicado}). *{CVE-ID} detail*.
 * National Vulnerability Database. {url}}; sin año, {@code (s.f.)}. Los asteriscos marcan cursiva.
 */
public final class ReferenciasApa {

    private static final String RECURSO_BASE = "informes/textos/referencias-base.md";
    private static final String SIN_FECHA = "s.f.";

    private final List<String> base;

    public ReferenciasApa(List<String> base) {
        this.base = List.copyOf(base);
    }

    /** Usa el bloque fijo versionado con las plantillas. */
    public static ReferenciasApa desdeClasspath() {
        try (InputStream entrada = new ClassPathResource(RECURSO_BASE).getInputStream()) {
            String texto = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
            List<String> lineas = new ArrayList<>();
            for (String linea : texto.split("\\R")) {
                String limpia = linea.strip();
                if (!limpia.isEmpty() && !limpia.startsWith("#")) {
                    lineas.add(limpia);
                }
            }
            return new ReferenciasApa(lineas);
        } catch (IOException excepcion) {
            throw new IllegalStateException("No se pudo leer " + RECURSO_BASE, excepcion);
        }
    }

    /** Referencia de un CVE; {@code publicadoIso} puede ser nulo (entonces se usa "s.f."). */
    public static String referenciaDeCve(String cveId, String publicadoIso) {
        String anio = publicadoIso != null && publicadoIso.length() >= 4 && publicadoIso.substring(0, 4).matches("\\d{4}")
            ? publicadoIso.substring(0, 4)
            : SIN_FECHA;
        return "National Institute of Standards and Technology. (" + anio + "). *" + cveId
            + " detail*. National Vulnerability Database. https://nvd.nist.gov/vuln/detail/" + cveId;
    }

    /**
     * Lista final: el bloque fijo más un CVE por identificador distinto, sin repetidos y en orden alfabético
     * (sin distinguir mayúsculas, tildes ni asteriscos).
     */
    public List<String> construir(Collection<InformeSnapshot.Cve> cves) {
        Map<String, String> porId = new LinkedHashMap<>();
        for (InformeSnapshot.Cve cve : cves) {
            porId.putIfAbsent(cve.id(), referenciaDeCve(cve.id(), cve.publicado()));
        }
        List<String> todas = new ArrayList<>(base);
        todas.addAll(porId.values());
        return todas.stream()
            .distinct()
            .sorted(Comparator.comparing(ReferenciasApa::claveAlfabetica).thenComparing(Comparator.naturalOrder()))
            .toList();
    }

    private static String claveAlfabetica(String referencia) {
        return Normalizer.normalize(referencia.replace("*", ""), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    }
}

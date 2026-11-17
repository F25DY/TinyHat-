package com.hexora.hexora.informes.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexora.hexora.informes.snapshot.InformeSnapshot.Cve;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenciasApaTest {

    private static Cve cve(String id, String publicado) {
        return new Cve(id, new BigDecimal("9.8"), publicado, "https://nvd.nist.gov/vuln/detail/" + id);
    }

    @Test
    void elBloqueFijoTraeLasReferenciasDelPlanMasLasDeLaEscala() {
        List<String> referencias = ReferenciasApa.desdeClasspath().construir(List.of());
        assertThat(referencias).hasSize(5);
        assertThat(referencias).anyMatch(r -> r.startsWith("OWASP Foundation. (s.f.)."))
            .anyMatch(r -> r.contains("NIST Special Publication 800-115"))
            .anyMatch(r -> r.startsWith("Forum of Incident Response and Security Teams."))
            .anyMatch(r -> r.contains("NIST Special Publication 800-30, Rev. 1"))
            .anyMatch(r -> r.contains("FIPS PUB 199"));
        assertThat(referencias).noneMatch(r -> r.startsWith("#") || r.isBlank());
    }

    @Test
    void cadaReferenciaDeCveSigueElFormatoExactoDelPlan() {
        assertThat(ReferenciasApa.referenciaDeCve("CVE-2024-0001", "2024-01-10")).isEqualTo(
            "National Institute of Standards and Technology. (2024). *CVE-2024-0001 detail*. "
                + "National Vulnerability Database. https://nvd.nist.gov/vuln/detail/CVE-2024-0001");
    }

    @Test
    void sinAnioDePublicacionSeUsaSf() {
        assertThat(ReferenciasApa.referenciaDeCve("CVE-2023-9999", null))
            .startsWith("National Institute of Standards and Technology. (s.f.). *CVE-2023-9999 detail*.");
        assertThat(ReferenciasApa.referenciaDeCve("CVE-2023-9999", "")).contains("(s.f.)");
        assertThat(ReferenciasApa.referenciaDeCve("CVE-2023-9999", "abc")).contains("(s.f.)");
    }

    @Test
    void agregaUnaEntradaPorCveDistintoSinRepetidos() {
        List<String> referencias = ReferenciasApa.desdeClasspath().construir(List.of(
            cve("CVE-2024-0001", "2024-01-10"), cve("CVE-2024-0001", "2024-01-10"), cve("CVE-2022-0002", null)));
        assertThat(referencias).hasSize(7);
        assertThat(referencias.stream().filter(r -> r.contains("CVE-2024-0001"))).hasSize(1);
        assertThat(referencias.stream().filter(r -> r.contains("CVE-2022-0002 detail*") && r.contains("(s.f.)")))
            .hasSize(1);
    }

    @Test
    void quedanEnOrdenAlfabeticoYElResultadoEsDeterminista() {
        List<Cve> cves = new ArrayList<>(List.of(cve("CVE-2024-0001", "2024-01-10"), cve("CVE-2021-44228", "2021-12-10"),
            cve("CVE-2023-4863", "2023-09-12")));
        ReferenciasApa apa = ReferenciasApa.desdeClasspath();
        List<String> a = apa.construir(cves);
        Collections.reverse(cves);
        List<String> b = apa.construir(cves);

        assertThat(a).isEqualTo(b);
        List<String> claves = a.stream().map(r -> r.replace("*", "").toLowerCase()).toList();
        assertThat(claves).isSorted();
        // Los autores empiezan por: Forum, Joint, National (FIPS y CVE), OWASP, Scarfone.
        assertThat(a.get(0)).startsWith("Forum of Incident Response");
        assertThat(a.get(a.size() - 1)).startsWith("Scarfone, K.");
    }

    @Test
    void conTildesYAsteriscosElOrdenIgnoraAmbos() {
        ReferenciasApa apa = new ReferenciasApa(List.of("Álvarez, J. (2020). *Zeta*. X.", "Alonso, M. (2019). Beta. Y.",
            "Ángel, P. (2018). Gamma. Z."));
        assertThat(apa.construir(List.of())).containsExactly("Alonso, M. (2019). Beta. Y.",
            "Álvarez, J. (2020). *Zeta*. X.", "Ángel, P. (2018). Gamma. Z.");
    }
}

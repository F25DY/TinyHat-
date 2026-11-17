package com.hexora.hexora.informes.snapshot;

import static com.hexora.hexora.informes.snapshot.DatosDePrueba.APLICACIONES;
import static com.hexora.hexora.informes.snapshot.DatosDePrueba.RED;
import static com.hexora.hexora.informes.snapshot.DatosDePrueba.VACIA;
import static com.hexora.hexora.informes.snapshot.DatosDePrueba.entrada;
import static com.hexora.hexora.informes.snapshot.DatosDePrueba.entradaVariada;
import static com.hexora.hexora.informes.snapshot.DatosDePrueba.vulnerabilidad;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.Entrada;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.FuenteArea;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.FuenteVulnerabilidad;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.Hallazgo;
import com.hexora.hexora.informes.validacion.InformeValidacionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** El ensamblado: Anexo A, métricas, órdenes, severidad, riesgo, CVE y referencias. */
class ConstructorSnapshotTest {

    private static Hallazgo hallazgo(InformeSnapshot s, String area, String codigo) {
        return s.areas().stream().filter(a -> a.nombre().equals(area)).findFirst().orElseThrow()
            .hallazgos().stream().filter(h -> h.codigo().equals(codigo)).findFirst().orElseThrow();
    }

    @Test
    void identificaElDocumentoYSusDatosBasicos() {
        InformeSnapshot s = ConstructorSnapshot.construir(entradaVariada(TipoInforme.TEC));

        assertThat(s.schemaVersion()).isEqualTo(1);
        assertThat(s.plantillaVersion()).isEqualTo("2026.1");
        assertThat(s.generadoEn()).isEqualTo("2026-11-20T10:15:00-05:00");
        assertThat(s.informe().tipo()).isEqualTo("TEC");
        assertThat(s.informe().version()).isEqualTo(2);
        assertThat(s.informe().id()).isEqualTo("TH-2026-7F3A-0C91-TEC-v2");
        assertThat(s.informe().estado()).isEqualTo("PUBLICADO");
        assertThat(s.organizacion().nombre()).isEqualTo("Organización Ficticia S.A.S.");
        assertThat(s.proyecto().nombre()).isEqualTo("Proyecto Piloto");
        assertThat(s.proyecto().fechaInicio()).isEqualTo("2026-11-02");
        assertThat(s.proyecto().fechaFin()).isEqualTo("2026-11-16");
        assertThat(s.auditor().nombre()).isEqualTo("Auditores Demo");
        assertThat(s.narrativa()).containsEntry("resumenEjecutivo", "Resumen **ficticio**.");
    }

    @Test
    void unaCopiaDeTrabajoLlevaElSufijoNp() {
        Entrada base = entradaVariada(TipoInforme.EJE);
        Entrada np = new Entrada(base.tipo(), base.numeroVersion(), "BORRADOR", true, base.anio(),
            base.organizacionUuid(), base.proyectoUuid(), base.organizacionNombre(), base.proyectoNombre(),
            base.fechaInicio(), base.fechaFin(), base.auditorNombre(), base.areas(), base.vulnerabilidades(),
            base.narrativa(), base.trazabilidad(), base.generadoEn(), base.plantillaVersion(), base.maxHallazgos(),
            base.referencias());
        InformeSnapshot s = ConstructorSnapshot.construir(np);
        assertThat(s.informe().id()).isEqualTo("TH-2026-7F3A-0C91-EJE-v2-NP");
        assertThat(s.informe().estado()).isEqualTo("BORRADOR");
    }

    @Test
    void losAceptadosVanAlAnexoAYNoCuentanEnLasMetricasPrincipales() {
        InformeSnapshot s = ConstructorSnapshot.construir(entradaVariada(TipoInforme.TEC));

        assertThat(s.anexoA()).hasSize(1);
        var entrada = s.anexoA().get(0);
        assertThat(entrada.codigo()).isEqualTo("H-EEEEEE");
        assertThat(entrada.area()).isEqualTo("Aplicaciones");
        assertThat(entrada.tipo()).isEqualTo("ACEPTADO");
        assertThat(entrada.severidad()).as("8 en C/I/D es impacto 4").isEqualTo("ALTA");

        assertThat(s.metricas().aceptados()).isEqualTo(1);
        assertThat(s.metricas().falsosPositivos()).isZero();
        assertThat(s.metricas().totalHallazgos()).as("solo el cuerpo").isEqualTo(4);
        assertThat(s.areas().stream().flatMap(a -> a.hallazgos().stream()).map(Hallazgo::codigo))
            .doesNotContain("H-EEEEEE");
        assertThat(s.metricas().porEstado()).containsOnlyKeys("Abierta", "En Progreso", "Mitigada");
        assertThat(s.metricas().porEstado()).containsEntry("Abierta", 2).containsEntry("En Progreso", 1)
            .containsEntry("Mitigada", 1);
    }

    @Test
    void elEstadoAceptadaSeReconoceSinImportarMayusculasNiEspacios() {
        Entrada e = entrada(TipoInforme.TEC, List.of(RED), List.of(
            vulnerabilidad("111111", RED, "Uno", 5, 5, 5, 5, " aceptada ")));
        InformeSnapshot s = ConstructorSnapshot.construir(e);
        assertThat(s.anexoA()).hasSize(1);
        assertThat(s.metricas().totalHallazgos()).isZero();
    }

    @Test
    void laSeveridadSeDerivaDelImpactoYElRiesgoDeLaTablaDeNist() {
        InformeSnapshot s = ConstructorSnapshot.construir(entradaVariada(TipoInforme.TEC));

        Hallazgo sql = hallazgo(s, "Aplicaciones", "H-CCCCCC");
        assertThat(sql.cia()).isEqualTo(new InformeSnapshot.Cia(10, 10, 10));
        assertThat(sql.impacto15()).isEqualTo(5);
        assertThat(sql.probabilidad()).isEqualTo(5);
        assertThat(sql.severidad()).isEqualTo("CRITICA");
        assertThat(sql.riesgo().nivel()).isEqualTo("CRITICO");
        assertThat(sql.riesgo().puntaje()).isEqualTo(25);
        assertThat(sql.faseSugerida()).isEqualTo("F1");

        // Una sola dimensión muy alta (9) con probabilidad 8: impacto 4, probabilidad 4.
        Hallazgo puerto = hallazgo(s, "Red", "H-AAAAAA");
        assertThat(puerto.impacto15()).isEqualTo(4);
        assertThat(puerto.probabilidad()).isEqualTo(4);
        assertThat(puerto.severidad()).isEqualTo("ALTA");
        assertThat(puerto.riesgo().nivel()).isEqualTo("ALTO");
        assertThat(puerto.riesgo().puntaje()).isEqualTo(16);
        assertThat(puerto.faseSugerida()).isEqualTo("F2");

        // 5/5/5 con probabilidad 5: nivel 3 por 3, Medio.
        Hallazgo cifrado = hallazgo(s, "Red", "H-BBBBBB");
        assertThat(cifrado.severidad()).isEqualTo("MEDIA");
        assertThat(cifrado.riesgo().nivel()).isEqualTo("MEDIO");
        assertThat(cifrado.faseSugerida()).isEqualTo("F3");

        Hallazgo menor = hallazgo(s, "Aplicaciones", "H-DDDDDD");
        assertThat(menor.impacto15()).isEqualTo(2);
        assertThat(menor.severidad()).isEqualTo("BAJA");
    }

    @Test
    void elCodigoDelHallazgoSaleDelUuidYEsEstable() {
        InformeSnapshot a = ConstructorSnapshot.construir(entradaVariada(TipoInforme.TEC));
        InformeSnapshot b = ConstructorSnapshot.construir(entradaVariada(TipoInforme.MIT));
        assertThat(codigos(a)).isEqualTo(codigos(b)).contains("H-AAAAAA", "H-CCCCCC");
    }

    private static List<String> codigos(InformeSnapshot s) {
        return s.areas().stream().flatMap(area -> area.hallazgos().stream()).map(Hallazgo::codigo).sorted().toList();
    }

    @Test
    void lasMetricasSeCalculanAqui() {
        InformeSnapshot s = ConstructorSnapshot.construir(entradaVariada(TipoInforme.TEC));
        var m = s.metricas();

        assertThat(m.totalAreas()).isEqualTo(3);
        assertThat(m.sinProbabilidad()).isZero();
        assertThat(m.porSeveridad()).containsOnlyKeys("CRITICA", "ALTA", "MEDIA", "BAJA", "INFORMATIVA")
            .containsEntry("CRITICA", 1).containsEntry("ALTA", 1).containsEntry("MEDIA", 1)
            .containsEntry("BAJA", 1).containsEntry("INFORMATIVA", 0);

        assertThat(m.porAreaSeveridad()).extracting(InformeSnapshot.FilaAreaSeveridad::area)
            .containsExactly("Aplicaciones", "Área sin hallazgos", "Red");
        var aplicaciones = m.porAreaSeveridad().get(0).porSeveridad();
        assertThat(aplicaciones).containsEntry("CRITICA", 1).containsEntry("BAJA", 1).containsEntry("ALTA", 0);
        assertThat(m.porAreaSeveridad().get(1).porSeveridad().values()).containsOnly(0);

        // mapaCalor[p-1][i-1]: SQL (5,5), puerto (4,4), cifrado (3,3), cabecera (impacto 2, prob 2).
        assertThat(m.mapaCalor()).hasSize(5).allSatisfy(fila -> assertThat(fila).hasSize(5));
        assertThat(m.mapaCalor().get(4).get(4)).isEqualTo(1);
        assertThat(m.mapaCalor().get(3).get(3)).isEqualTo(1);
        assertThat(m.mapaCalor().get(2).get(2)).isEqualTo(1);
        assertThat(m.mapaCalor().get(1).get(1)).isEqualTo(1);
        int total = m.mapaCalor().stream().flatMap(List::stream).mapToInt(Integer::intValue).sum();
        assertThat(total).isEqualTo(m.totalHallazgos());
    }

    @Test
    void sinProbabilidadNoTieneRiesgoYSeCuenta() {
        Entrada e = entrada(TipoInforme.MR, List.of(RED), List.of(
            vulnerabilidad("111111", RED, "Sin dato", 5, 5, 5, null, "Abierta")));
        InformeSnapshot s = ConstructorSnapshot.construir(e);
        Hallazgo h = hallazgo(s, "Red", "H-111111");
        assertThat(h.probabilidad()).isNull();
        assertThat(h.riesgo()).isNull();
        assertThat(s.metricas().sinProbabilidad()).isEqualTo(1);
        assertThat(s.metricas().mapaCalor().stream().flatMap(List::stream).mapToInt(Integer::intValue).sum()).isZero();
    }

    @Test
    void losOrdenesDelSnapshotSonLosDelPlan() {
        InformeSnapshot s = ConstructorSnapshot.construir(entradaVariada(TipoInforme.TEC));
        assertThat(s.areas()).extracting(InformeSnapshot.Area::nombre)
            .as("áreas por nombre ascendente, sin distinguir tildes")
            .containsExactly("Aplicaciones", "Área sin hallazgos", "Red");
        assertThat(s.areas().get(0).hallazgos()).extracting(Hallazgo::codigo)
            .as("fichas: severidad desc").containsExactly("H-CCCCCC", "H-DDDDDD");
        assertThat(s.areas().get(2).hallazgos()).extracting(Hallazgo::codigo).containsExactly("H-AAAAAA", "H-BBBBBB");
    }

    @Test
    void lasAreasSinHallazgosTambienAparecen() {
        InformeSnapshot s = ConstructorSnapshot.construir(entradaVariada(TipoInforme.TEC));
        assertThat(s.areas()).extracting(InformeSnapshot.Area::nombre).contains("Área sin hallazgos");
        assertThat(s.areas().stream().filter(a -> a.nombre().startsWith("Área")).findFirst().orElseThrow()
            .hallazgos()).isEmpty();
    }

    @Test
    void elCveSeTomaDeLaCacheLocalYSinCacheSoloLleva() {
        FuenteVulnerabilidad conCache = DatosDePrueba.conCve(
            vulnerabilidad("aaaaaa", RED, "Con CVE", 9, 9, 9, 9, "Abierta"), "cve-2024-0001", DatosDePrueba.cveEnCache());
        FuenteVulnerabilidad sinCache = DatosDePrueba.conCve(
            vulnerabilidad("bbbbbb", RED, "Sin caché", 9, 9, 9, 9, "Abierta"), "CVE-2023-12345", null);
        FuenteVulnerabilidad invalido = DatosDePrueba.conCve(
            vulnerabilidad("cccccc", RED, "CVE mal escrito", 9, 9, 9, 9, "Abierta"), "no-es-un-cve", null);
        InformeSnapshot s = ConstructorSnapshot.construir(entrada(TipoInforme.TEC, List.of(RED),
            List.of(conCache, sinCache, invalido)));

        Hallazgo a = hallazgo(s, "Red", "H-AAAAAA");
        assertThat(a.cves()).hasSize(1);
        assertThat(a.cves().get(0).id()).isEqualTo("CVE-2024-0001");
        assertThat(a.cves().get(0).cvss()).isEqualByComparingTo("9.8");
        assertThat(a.cves().get(0).publicado()).isEqualTo("2024-01-10");
        assertThat(a.cves().get(0).url()).isEqualTo("https://nvd.nist.gov/vuln/detail/CVE-2024-0001");
        assertThat(a.referencias()).as("solo http/https, sin repetidos")
            .containsExactly("https://vendor.example/parche-1", "https://vendor.example/parche-2");

        Hallazgo b = hallazgo(s, "Red", "H-BBBBBB");
        assertThat(b.cves()).hasSize(1);
        assertThat(b.cves().get(0).cvss()).isNull();
        assertThat(b.cves().get(0).publicado()).isNull();
        assertThat(b.referencias()).isEmpty();

        assertThat(hallazgo(s, "Red", "H-CCCCCC").cves()).isEmpty();
    }

    @Test
    void lasReferenciasApaLlevanElBloqueFijoYUnaEntradaPorCve() {
        FuenteVulnerabilidad cve = DatosDePrueba.conCve(
            vulnerabilidad("aaaaaa", RED, "Con CVE", 9, 9, 9, 9, "Abierta"), "CVE-2024-0001", DatosDePrueba.cveEnCache());
        InformeSnapshot s = ConstructorSnapshot.construir(entrada(TipoInforme.TEC, List.of(RED), List.of(cve)));
        assertThat(s.referenciasApa()).anyMatch(r -> r.contains("NIST Special Publication 800-115"))
            .anyMatch(r -> r.contains("CVE-2024-0001 detail*") && r.startsWith(
                "National Institute of Standards and Technology. (2024)."));
    }

    @Test
    void elCveDeUnAceptadoNoEntraEnLasReferencias() {
        FuenteVulnerabilidad aceptada = DatosDePrueba.conCve(
            vulnerabilidad("aaaaaa", RED, "Asumido", 9, 9, 9, 9, "Aceptada"), "CVE-2024-0001", DatosDePrueba.cveEnCache());
        InformeSnapshot s = ConstructorSnapshot.construir(entrada(TipoInforme.TEC, List.of(RED), List.of(aceptada)));
        assertThat(s.referenciasApa()).noneMatch(r -> r.contains("CVE-2024-0001"));
    }

    @Test
    void laTrazabilidadSoloVaEnElInformeFinal() {
        Entrada fin = entrada(TipoInforme.FIN, List.of(RED), List.of());
        Entrada conTrazas = new Entrada(fin.tipo(), 1, "PUBLICADO", false, 2026, fin.organizacionUuid(),
            fin.proyectoUuid(), fin.organizacionNombre(), fin.proyectoNombre(), fin.fechaInicio(), fin.fechaFin(),
            fin.auditorNombre(), fin.areas(), fin.vulnerabilidades(), fin.narrativa(), DatosDePrueba.trazabilidad(),
            fin.generadoEn(), fin.plantillaVersion(), fin.maxHallazgos(), fin.referencias());
        assertThat(ConstructorSnapshot.construir(conTrazas).trazabilidad()).hasSize(1);

        Entrada tec = new Entrada(TipoInforme.TEC, 1, "PUBLICADO", false, 2026, fin.organizacionUuid(),
            fin.proyectoUuid(), fin.organizacionNombre(), fin.proyectoNombre(), fin.fechaInicio(), fin.fechaFin(),
            fin.auditorNombre(), fin.areas(), fin.vulnerabilidades(), fin.narrativa(), DatosDePrueba.trazabilidad(),
            fin.generadoEn(), fin.plantillaVersion(), fin.maxHallazgos(), fin.referencias());
        assertThat(ConstructorSnapshot.construir(tec).trazabilidad()).isEmpty();
    }

    @Test
    void masDeMaxHallazgosEsUnErrorDeValidacion() {
        List<FuenteVulnerabilidad> muchas = new ArrayList<>();
        for (int n = 0; n < 4; n++) {
            muchas.add(vulnerabilidad(String.format("%06x", 0xabc000 + n), RED, "V" + n, 5, 5, 5, 5, "Abierta"));
        }
        Entrada base = entrada(TipoInforme.TEC, List.of(RED), muchas);
        Entrada limite3 = new Entrada(base.tipo(), 1, "PUBLICADO", false, 2026, base.organizacionUuid(),
            base.proyectoUuid(), base.organizacionNombre(), base.proyectoNombre(), base.fechaInicio(), base.fechaFin(),
            base.auditorNombre(), base.areas(), base.vulnerabilidades(), base.narrativa(), List.of(), base.generadoEn(),
            base.plantillaVersion(), 3, base.referencias());
        assertThatThrownBy(() -> ConstructorSnapshot.construir(limite3))
            .isInstanceOf(InformeValidacionException.class)
            .hasMessageContaining("4 vulnerabilidades").hasMessageContaining("3");
        // En el límite exacto se acepta.
        Entrada limite4 = new Entrada(base.tipo(), 1, "PUBLICADO", false, 2026, base.organizacionUuid(),
            base.proyectoUuid(), base.organizacionNombre(), base.proyectoNombre(), base.fechaInicio(), base.fechaFin(),
            base.auditorNombre(), base.areas(), base.vulnerabilidades(), base.narrativa(), List.of(), base.generadoEn(),
            base.plantillaVersion(), 4, base.referencias());
        assertThat(ConstructorSnapshot.construir(limite4).metricas().totalHallazgos()).isEqualTo(4);
    }

    @Test
    void unaVulnerabilidadDeOtraAreaNoEntra() {
        Entrada e = entrada(TipoInforme.TEC, List.of(RED), List.of(
            vulnerabilidad("111111", new FuenteArea("area-ajena", "Ajena", null), "Ajena", 5, 5, 5, 5, "Abierta")));
        assertThatThrownBy(() -> ConstructorSnapshot.construir(e)).isInstanceOf(InformeValidacionException.class);
    }

    @Test
    void dosAreasConElMismoNombreSeMantienenSeparadas() {
        FuenteArea otraRed = new FuenteArea("area-red-2", "Red", "Otra");
        Entrada e = entrada(TipoInforme.TEC, List.of(RED, otraRed), List.of(
            vulnerabilidad("111111", RED, "Uno", 5, 5, 5, 5, "Abierta"),
            vulnerabilidad("222222", otraRed, "Dos", 5, 5, 5, 5, "Abierta")));
        InformeSnapshot s = ConstructorSnapshot.construir(e);
        assertThat(s.areas()).hasSize(2);
        assertThat(s.areas().get(0).hallazgos()).hasSize(1);
        assertThat(s.areas().get(1).hallazgos()).hasSize(1);
    }

    @Test
    void lasColeccionesDelSnapshotSonInmutables() {
        InformeSnapshot s = ConstructorSnapshot.construir(entradaVariada(TipoInforme.TEC));
        assertThatThrownBy(() -> s.areas().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> s.metricas().porSeveridad().put("X", 1))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> s.narrativa().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(Map.copyOf(s.narrativa())).isEqualTo(s.narrativa());
    }
}

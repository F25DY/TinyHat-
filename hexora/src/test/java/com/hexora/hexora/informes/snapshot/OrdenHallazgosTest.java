package com.hexora.hexora.informes.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexora.hexora.informes.snapshot.InformeSnapshot.Area;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.Cia;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.Hallazgo;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.Riesgo;
import com.hexora.hexora.informes.snapshot.OrdenHallazgos.HallazgoDeArea;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Órdenes de §4.3. */
class OrdenHallazgosTest {

    private static Hallazgo h(String codigo, String severidad, int c, int i, int a, Integer prob, Riesgo riesgo) {
        return new Hallazgo(codigo, "T " + codigo, "m", "Abierta", severidad, new Cia(c, i, a), prob, 3, riesgo,
            List.of(), List.of(), "F3");
    }

    private static Riesgo r(int puntaje, String nivel) {
        return new Riesgo(puntaje, nivel);
    }

    private static List<String> codigos(List<HallazgoDeArea> lista) {
        return lista.stream().map(par -> par.hallazgo().codigo()).toList();
    }

    @Test
    void fichasTecSeveridadLuegoSumaCiaLuegoCodigo() {
        List<Hallazgo> lista = new ArrayList<>(List.of(
            h("H-3", "ALTA", 5, 5, 5, 3, null),
            h("H-2", "CRITICA", 1, 1, 1, 3, null),
            h("H-1", "ALTA", 9, 9, 9, 3, null),
            h("H-4", "ALTA", 5, 5, 5, 3, null),
            h("H-5", "INFORMATIVA", 9, 9, 9, 3, null)));
        Collections.shuffle(lista, new java.util.Random(7));
        lista.sort(OrdenHallazgos.FICHAS_TEC);
        assertThat(lista).extracting(Hallazgo::codigo).containsExactly("H-2", "H-1", "H-3", "H-4", "H-5");
    }

    @Test
    void mitigacionSeveridadSumaCiaAreaYCodigo() {
        List<HallazgoDeArea> lista = new ArrayList<>(List.of(
            new HallazgoDeArea("Red", h("H-1", "ALTA", 5, 5, 5, 3, null)),
            new HallazgoDeArea("Aplicaciones", h("H-2", "ALTA", 5, 5, 5, 3, null)),
            new HallazgoDeArea("Red", h("H-3", "CRITICA", 1, 1, 1, 3, null)),
            new HallazgoDeArea("Aplicaciones", h("H-0", "ALTA", 5, 5, 5, 3, null)),
            new HallazgoDeArea("Red", h("H-4", "ALTA", 9, 9, 9, 3, null))));
        lista.sort(OrdenHallazgos.MITIGACION);
        // Crítica primero; luego Alta por suma CIA (27 antes que 15), y a igual suma por área y código.
        assertThat(codigos(lista)).containsExactly("H-3", "H-4", "H-0", "H-2", "H-1");
    }

    @Test
    void matrizDeRiesgoPorNivelLuegoPuntajeLuegoSeveridadLuegoCodigo() {
        List<HallazgoDeArea> lista = new ArrayList<>(List.of(
            new HallazgoDeArea("A", h("H-1", "CRITICA", 1, 1, 1, 1, r(5, "MEDIO"))),
            new HallazgoDeArea("A", h("H-2", "ALTA", 1, 1, 1, 5, r(20, "ALTO"))),
            new HallazgoDeArea("A", h("H-3", "ALTA", 1, 1, 1, 4, r(16, "ALTO"))),
            new HallazgoDeArea("A", h("H-4", "MEDIA", 1, 1, 1, 5, r(25, "CRITICO"))),
            new HallazgoDeArea("A", h("H-5", "BAJA", 1, 1, 1, 5, r(20, "ALTO"))),
            new HallazgoDeArea("A", h("H-6", "ALTA", 1, 1, 1, 5, r(20, "ALTO"))),
            new HallazgoDeArea("A", h("H-7", "ALTA", 1, 1, 1, null, null))));
        Collections.shuffle(lista, new java.util.Random(3));
        lista.sort(OrdenHallazgos.MATRIZ_RIESGO);
        // Crítico primero aunque su severidad sea Media; Alto por puntaje (20, 20, 20, 16) y a igual puntaje por
        // severidad (Alta antes que Baja) y código; Medio después; sin riesgo al final.
        assertThat(codigos(lista)).containsExactly("H-4", "H-2", "H-6", "H-5", "H-3", "H-1", "H-7");
    }

    @Test
    void laCriticaConBajaProbabilidadVaDespuesDeUnaAltaCasiSegura() {
        // Es la advertencia del plan: el registro se ordena por riesgo, no solo por severidad técnica.
        List<HallazgoDeArea> lista = new ArrayList<>(List.of(
            new HallazgoDeArea("A", h("H-CRIT", "CRITICA", 1, 1, 1, 1, r(5, "BAJO"))),
            new HallazgoDeArea("A", h("H-ALTA", "ALTA", 1, 1, 1, 5, r(20, "ALTO")))));
        lista.sort(OrdenHallazgos.MATRIZ_RIESGO);
        assertThat(codigos(lista)).containsExactly("H-ALTA", "H-CRIT");
    }

    @Test
    void topEjecutivoSeveridadLuegoPuntajeNulosAlFinalLuegoSumaCia() {
        List<HallazgoDeArea> lista = new ArrayList<>(List.of(
            new HallazgoDeArea("A", h("H-1", "ALTA", 9, 9, 9, null, null)),
            new HallazgoDeArea("A", h("H-2", "ALTA", 1, 1, 1, 4, r(16, "ALTO"))),
            new HallazgoDeArea("A", h("H-3", "ALTA", 1, 1, 1, 5, r(20, "ALTO"))),
            new HallazgoDeArea("A", h("H-4", "CRITICA", 1, 1, 1, 1, r(5, "BAJO"))),
            new HallazgoDeArea("A", h("H-5", "ALTA", 3, 3, 3, 4, r(16, "ALTO")))));
        lista.sort(OrdenHallazgos.TOP_EJECUTIVO);
        // Crítica; luego Alta por puntaje (20, 16, 16, nulo); a igual puntaje (16) la mayor suma CIA (H-5).
        assertThat(codigos(lista)).containsExactly("H-4", "H-3", "H-5", "H-2", "H-1");
    }

    @Test
    void topEjecutivoDevuelveComoMaximoCincoYMenosSiHayMenos() {
        List<Hallazgo> siete = Stream.of("H-1", "H-2", "H-3", "H-4", "H-5", "H-6", "H-7")
            .map(c -> h(c, "ALTA", 1, 1, 1, 3, r(9, "MEDIO"))).toList();
        InformeSnapshot s = snapshotCon(List.of(new Area("A", null, siete)));
        assertThat(OrdenHallazgos.topEjecutivo(s)).hasSize(5);
        assertThat(OrdenHallazgos.topEjecutivo(snapshotCon(List.of(new Area("A", null, siete.subList(0, 2))))))
            .hasSize(2);
        assertThat(OrdenHallazgos.topEjecutivo(snapshotCon(List.of()))).isEmpty();
    }

    @Test
    void laOrdenacionEsDeterministaSinImportarElOrdenDeEntrada() {
        List<HallazgoDeArea> a = new ArrayList<>();
        for (int n = 1; n <= 12; n++) {
            a.add(new HallazgoDeArea(n % 2 == 0 ? "Red" : "Aplicaciones",
                h("H-" + n, n % 3 == 0 ? "ALTA" : "MEDIA", n, n % 5, 2, 3, r(9, "MEDIO"))));
        }
        List<HallazgoDeArea> b = new ArrayList<>(a);
        Collections.reverse(b);
        assertThat(codigos(a.stream().sorted(OrdenHallazgos.MITIGACION).toList()))
            .isEqualTo(codigos(b.stream().sorted(OrdenHallazgos.MITIGACION).toList()));
        assertThat(codigos(a.stream().sorted(OrdenHallazgos.MATRIZ_RIESGO).toList()))
            .isEqualTo(codigos(b.stream().sorted(OrdenHallazgos.MATRIZ_RIESGO).toList()));
    }

    @Test
    void lasAreasSeOrdenanSinDistinguirTildesNiMayusculas() {
        List<String> nombres = new ArrayList<>(List.of("zona", "Árbol", "alfa", "Beta", "Álamo"));
        nombres.sort(OrdenHallazgos.AREAS);
        assertThat(nombres).containsExactly("Álamo", "alfa", "Árbol", "Beta", "zona");
    }

    private static InformeSnapshot snapshotCon(List<Area> areas) {
        return new InformeSnapshot(1, "2026.1", "2026-11-20T10:00:00-05:00",
            new InformeSnapshot.DatosInforme("EJE", 1, "TH-2026-AAAA-BBBB-EJE-v1", "PUBLICADO"),
            new InformeSnapshot.DatosOrganizacion("Org"), new InformeSnapshot.DatosProyecto("P", "2026-11-01", "2026-11-02"),
            new InformeSnapshot.DatosAuditor("Aud"),
            new InformeSnapshot.Metricas(Map.of(), List.of(), Map.of(), List.of(), 0, 0, areas.size(), 0, 0),
            areas, List.of(), List.of(), Map.of(), List.of());
    }
}

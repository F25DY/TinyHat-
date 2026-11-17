package com.hexora.hexora.informes.render.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.render.DatosDemo;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.FuenteVulnerabilidad;
import com.hexora.hexora.informes.snapshot.InformeSnapshot;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** El máximo de hallazgos por informe (1000) se renderiza en un tiempo razonable. */
class RendimientoPdfTest {

    private static List<FuenteVulnerabilidad> mil() {
        List<FuenteVulnerabilidad> lista = new ArrayList<>();
        var areas = List.of(DatosDemo.RED, DatosDemo.WEB, DatosDemo.IDENTIDAD);
        for (int n = 0; n < 1000; n++) {
            lista.add(DatosDemo.v(String.format("%06x", 0x100000 + n), areas.get(n % 3),
                "Hallazgo de prueba número " + n + " con un título algo largo para que ocupe espacio", n % 11, (n / 3) % 11,
                (n / 7) % 11, (n / 5) % 11, n % 4 == 0 ? "En Progreso" : "Abierta",
                "Acción de mitigación de ejemplo para el hallazgo " + n + ".", null, null));
        }
        return lista;
    }

    @Test
    void milHallazgosSeRenderizanEnElTecnicoYEnLaMatrizDentroDeUnTiempoRazonable() throws Exception {
        for (TipoInforme tipo : List.of(TipoInforme.TEC, TipoInforme.MR, TipoInforme.MIT)) {
            InformeSnapshot snapshot = DatosDemo.snapshotConVulnerabilidades(tipo, mil());
            assertThat(snapshot.metricas().totalHallazgos()).isEqualTo(1000);
            long inicio = System.nanoTime();
            byte[] pdf = MuestrasPdfTest.renderizar(snapshot, false);
            long segundos = (System.nanoTime() - inicio) / 1_000_000_000L;
            System.out.println("RENDIMIENTO " + tipo + ": " + PdfPrueba.paginas(pdf) + " páginas, " + segundos + " s, "
                + pdf.length / 1024 + " KiB");
            assertThat(segundos).as(tipo + " con 1000 hallazgos").isLessThan(120);
            assertThat(PdfPrueba.paginas(pdf)).isGreaterThan(20);
            assertThat(pdf.length).as("tamaño (máximo configurable: 25 MiB)").isLessThan(25 * 1024 * 1024);
        }
    }
}

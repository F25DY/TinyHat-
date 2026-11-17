package com.hexora.hexora.informes.render.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.markdown.MarkdownSaneado;
import com.hexora.hexora.informes.render.DatosDemo;
import com.hexora.hexora.informes.render.OpcionesRender;
import com.hexora.hexora.informes.render.TextosInforme;
import com.hexora.hexora.informes.render.pdf.VistaInforme.FilaArea;
import com.hexora.hexora.informes.snapshot.InformeSnapshot;
import com.hexora.hexora.informes.snapshot.SerializadorSnapshot;
import com.hexora.hexora.riesgo.EscalaRiesgo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** El modelo que reciben las plantillas: todo calculado en Java, nada en el HTML. */
class ConstructorVistaInformeTest {

    private final ConstructorVistaInforme constructor =
        new ConstructorVistaInforme(new MarkdownSaneado(20000), new TextosInforme());

    private VistaInforme vista(TipoInforme tipo, boolean np) {
        return constructor.construir(DatosDemo.snapshot(tipo, np), new OpcionesRender(np));
    }

    @Test
    void elDocumentoTraeLosDatosDeControlYElPie() {
        InformeSnapshot s = DatosDemo.snapshot(TipoInforme.TEC, false);
        VistaInforme v = constructor.construir(s, OpcionesRender.PUBLICADO);
        String huella = SerializadorSnapshot.huella(s);
        assertThat(v.doc().hashCompleto()).isEqualTo(huella).hasSize(64);
        assertThat(v.doc().hashCorto()).isEqualTo(huella.substring(0, 12));
        assertThat(v.doc().pie()).isEqualTo("TH-2026-7F3A-0C91-TEC-v2 · CONFIDENCIAL · SHA-256: " + huella.substring(0, 12));
        assertThat(v.doc().fechaEmision()).isEqualTo("20 de noviembre de 2026");
        assertThat(v.doc().fechaInicio()).isEqualTo("2 de noviembre de 2026");
        assertThat(v.doc().estado()).isEqualTo("Publicado");
        assertThat(v.doc().noPublicado()).isFalse();
        assertThat(v.doc().tipoNombre()).isEqualTo("Técnico");
        assertThat(v.doc().plantillaVersion()).isEqualTo("2026.1");
        assertThat(v.avisoNoPublicado()).isNull();
    }

    @Test
    void unaCopiaNpTraeElAvisoYElMarcadorDePendiente() {
        VistaInforme v = vista(TipoInforme.EJE, true);
        assertThat(v.doc().noPublicado()).isTrue();
        assertThat(v.doc().estado()).isEqualTo("No publicado");
        assertThat(v.doc().marcaAgua()).isEqualTo("DOCUMENTO NO PUBLICADO");
        assertThat(v.doc().pendiente()).isEqualTo("[PENDIENTE DE COMPLETAR POR EL AUDITOR]");
        assertThat(v.avisoNoPublicado().html()).contains("DOCUMENTO NO PUBLICADO — copia de trabajo sin validez como entregable.");
        assertThat(v.doc().id()).endsWith("-NP");
    }

    @Test
    void unaSeccionObligatoriaVaciaSoloEsPendienteEnUnaCopiaNp() {
        InformeSnapshot vacio = DatosDemo.snapshot(TipoInforme.EJE, true, false);
        assertThat(constructor.construir(vacio, OpcionesRender.NO_PUBLICADO).secciones().get("resumenEjecutivo").pendiente())
            .isTrue();
        // La recomendación es opcional: vacía no es pendiente.
        assertThat(constructor.construir(vacio, OpcionesRender.NO_PUBLICADO).secciones().get("recomendaciones").pendiente())
            .isFalse();
        assertThat(constructor.construir(vacio, OpcionesRender.PUBLICADO).secciones().get("resumenEjecutivo").pendiente())
            .isFalse();
    }

    @Test
    void laNarrativaSaleSaneadaConSuTitulo() {
        var seccion = vista(TipoInforme.EJE, false).secciones().get("resumenEjecutivo");
        assertThat(seccion.titulo()).isEqualTo("Resumen ejecutivo");
        assertThat(seccion.presente()).isTrue();
        assertThat(seccion.html()).contains("<strong>2 y el 16 de noviembre de 2026</strong>").contains("<ul>")
            .doesNotContain("<script").doesNotContain("href=");
    }

    @Test
    void elConteoPorSeveridadVaDeLaMasGraveALaMenosGraveYSumaElTotal() {
        VistaInforme v = vista(TipoInforme.EJE, false);
        assertThat(v.conteoSeveridad()).extracting(VistaInforme.FilaConteo::etiqueta)
            .containsExactly("Crítica", "Alta", "Media", "Baja", "Informativa");
        assertThat(v.conteoSeveridad()).extracting(VistaInforme.FilaConteo::clase)
            .containsExactly("sev-critica", "sev-alta", "sev-media", "sev-baja", "sev-informativa");
        assertThat(v.conteoSeveridad().stream().mapToInt(VistaInforme.FilaConteo::cantidad).sum())
            .isEqualTo(v.totalHallazgos()).isEqualTo(11);
        assertThat(v.conteoSeveridad()).allSatisfy(f -> assertThat(f.ancho()).isBetween(0, 100));
        assertThat(v.conteoSeveridad().get(0).porcentaje()).isEqualTo("9 %");
        assertThat(v.etiquetasSeveridad()).containsExactly("Crítica", "Alta", "Media", "Baja", "Informativa");
    }

    @Test
    void lasBarrasPorAreaNuncaSuperanElAnchoDelContenedor() {
        VistaInforme v = vista(TipoInforme.EJE, false);
        assertThat(v.areaSeveridad()).isNotEmpty();
        for (FilaArea fila : v.areaSeveridad()) {
            assertThat(fila.segmentos().stream().mapToInt(VistaInforme.Segmento::ancho).sum()).isLessThanOrEqualTo(100);
            assertThat(fila.segmentos()).allSatisfy(g -> assertThat(g.ancho()).isBetween(0, 100));
            assertThat(fila.conteos()).hasSize(5);
            assertThat(fila.conteos().stream().mapToInt(Integer::intValue).sum()).isEqualTo(fila.total());
        }
        // El área con más hallazgos define la escala: su barra ocupa (casi) todo el ancho.
        int mayor = v.areaSeveridad().stream().mapToInt(FilaArea::total).max().orElseThrow();
        FilaArea fila = v.areaSeveridad().stream().filter(a -> a.total() == mayor).findFirst().orElseThrow();
        assertThat(fila.segmentos().stream().mapToInt(VistaInforme.Segmento::ancho).sum()).isBetween(90, 100);
    }

    @Test
    void losRiesgosPrincipalesSonCincoConLaFraseDeLaDimensionMasAlta() {
        VistaInforme v = vista(TipoInforme.EJE, false);
        assertThat(v.riesgosPrincipales()).hasSize(5);
        var primero = v.riesgosPrincipales().get(0);
        assertThat(primero.posicion()).isEqualTo(1);
        assertThat(primero.titulo()).startsWith("Inyección SQL");
        assertThat(primero.severidad().texto()).isEqualTo("Crítica");
        assertThat(primero.severidad().clase()).isEqualTo("sev-critica");
        assertThat(primero.riesgo().texto()).isEqualTo("Crítico");
        assertThat(primero.riesgo().clase()).isEqualTo("niv-critico");
        // C=10, I=10, A=7: las dimensiones más altas son C e I.
        assertThat(primero.dimension()).contains("Confidencialidad: Podría quedar expuesta información")
            .contains("Integridad: Podría alterarse información o sistemas sin autorización.")
            .doesNotContain("Disponibilidad");
    }

    @Test
    void elIndiceDelTecnicoTieneUnaEntradaPorAreaYSoloLoQueExiste() {
        VistaInforme v = vista(TipoInforme.TEC, false);
        assertThat(v.indice()).extracting(VistaInforme.EntradaIndice::texto).contains("Alcance y metodología",
            "Escala de calificación", "Resumen de hallazgos", "Hallazgos por área", "Aplicaciones web",
            "Gestión de identidades", "Infraestructura de red", "Conclusiones", "Anexo A. Riesgos aceptados",
            "Anexo B. Glosario", "Referencias");
        assertThat(v.indice().stream().filter(e -> e.nivel() == 2)).hasSize(3);
        assertThat(v.indice()).extracting(VistaInforme.EntradaIndice::ancla).doesNotHaveDuplicates();
        // Los demás tipos no llevan índice.
        assertThat(vista(TipoInforme.EJE, false).indice()).isEmpty();
        assertThat(vista(TipoInforme.MIT, false).indice()).isEmpty();
    }

    @Test
    void elIndiceOmiteConclusionesYAnexoSiNoHay() {
        var sinAceptadas = DatosDemo.vulnerabilidades().stream().filter(x -> !"Aceptada".equals(x.estado())).toList();
        var base = DatosDemo.entrada(TipoInforme.TEC, false, java.util.Map.of("alcanceMetodologia", "Texto"),
            sinAceptadas, List.of(DatosDemo.RED, DatosDemo.WEB, DatosDemo.IDENTIDAD));
        InformeSnapshot s = com.hexora.hexora.informes.snapshot.ConstructorSnapshot.construir(base);
        VistaInforme v = constructor.construir(s, OpcionesRender.PUBLICADO);
        assertThat(v.indice()).extracting(VistaInforme.EntradaIndice::texto)
            .doesNotContain("Conclusiones", "Anexo A. Riesgos aceptados");
    }

    @Test
    void elMapaDeCalorVaDeProbabilidadMuyAltaAMuyBajaYLlevaElColorDeLaMatrizDeNist() {
        VistaInforme v = vista(TipoInforme.MR, false);
        assertThat(v.mapaCalor()).hasSize(5);
        assertThat(v.mapaCalor().get(0).probabilidad()).isEqualTo("5 · Muy alta");
        assertThat(v.mapaCalor().get(4).probabilidad()).isEqualTo("1 · Muy baja");
        for (int fila = 0; fila < 5; fila++) {
            int p = 5 - fila;
            for (int i = 1; i <= 5; i++) {
                assertThat(v.mapaCalor().get(fila).celdas().get(i - 1).clase())
                    .isEqualTo("niv-" + EscalaRiesgo.nivel(p, i).clave());
            }
        }
        // El total de las celdas es el total de hallazgos del cuerpo.
        assertThat(v.mapaCalor().stream().flatMap(f -> f.celdas().stream()).mapToInt(VistaInforme.CeldaCalor::valor).sum())
            .isEqualTo(v.totalHallazgos());
        assertThat(v.etiquetasImpacto()).containsExactly("Muy bajo", "Bajo", "Medio", "Alto", "Muy alto");
    }

    @Test
    void laMatrizDeNivelesEsLaTablaDeNist() {
        VistaInforme v = vista(TipoInforme.TEC, false);
        assertThat(v.matrizNiveles()).hasSize(5);
        assertThat(v.matrizNiveles().get(0).celdas()).extracting(VistaInforme.Celda::texto)
            .containsExactly("Muy bajo", "Bajo", "Medio", "Alto", "Crítico");
        assertThat(v.matrizNiveles().get(4).celdas()).extracting(VistaInforme.Celda::texto)
            .containsExactly("Muy bajo", "Muy bajo", "Muy bajo", "Bajo", "Bajo");
        assertThat(v.matrizNiveles().get(0).celdas().get(0).clase()).isEqualTo("niv-muy-bajo");
    }

    @Test
    void elRegistroDeLaMatrizSigueElOrdenPorNivelDeRiesgoYColoreaLaFila() {
        VistaInforme v = vista(TipoInforme.MR, false);
        assertThat(v.registroMatriz().get(0).codigo()).isEqualTo("H-D4E5F6");
        assertThat(v.registroMatriz().get(0).clase()).isEqualTo("niv-critico");
        assertThat(v.registroMatriz().get(0).riesgo().texto()).isEqualTo("Crítico");
        assertThat(v.registroMatriz().get(0).cve()).isEqualTo("CVE-2099-0002");
        assertThat(v.registroMatriz().get(0).cvss()).isEqualTo("7.5");
        assertThat(v.registroMatriz().get(0).puntaje()).isEqualTo("20");
        var sinCve = v.registroMatriz().stream().filter(r -> r.codigo().equals("H-A7B8C9")).findFirst().orElseThrow();
        assertThat(sinCve.cve()).isEqualTo("—");
        assertThat(sinCve.c()).isEqualTo("8");
    }

    @Test
    void elPlanDeMitigacionVaPorSeveridadConLaFaseSugerida() {
        VistaInforme v = vista(TipoInforme.MIT, false);
        assertThat(v.mitigacion().get(0).prioridad()).isEqualTo(1);
        assertThat(v.mitigacion().get(0).severidad().texto()).isEqualTo("Crítica");
        assertThat(v.mitigacion().get(0).fase()).isEqualTo("F1 — Contención inmediata (0–7 días)");
        assertThat(v.mitigacion().stream().map(VistaInforme.FilaMitigacion::prioridad).toList())
            .isEqualTo(java.util.stream.IntStream.rangeClosed(1, 11).boxed().toList());
        assertThat(v.mitigacion().get(10).severidad().texto()).isEqualTo("Baja");
    }

    @Test
    void elRecuadroDelAnexoAUsaLosConteosYSoloExisteSiHayAceptados() {
        assertThat(vista(TipoInforme.EJE, false).avisoAnexoA().html())
            .contains("<strong>Importante:</strong>").contains("2 hallazgo(s) con riesgo aceptado")
            .contains("0 falso(s) positivo(s)");
        assertThat(vista(TipoInforme.EJE, false).anexoA()).hasSize(2);
        assertThat(vista(TipoInforme.EJE, false).anexoA().get(0).tipo()).isEqualTo("Riesgo aceptado");
    }

    @Test
    void laTrazabilidadYElHistorialSeFormatean() {
        VistaInforme v = vista(TipoInforme.FIN, false);
        assertThat(v.trazabilidad()).hasSize(4);
        assertThat(v.trazabilidad().get(0).tipo()).isEqualTo("Técnico");
        assertThat(v.trazabilidad().get(0).publicado()).isEqualTo("18 de noviembre de 2026");
        assertThat(v.historial()).hasSize(2);
        assertThat(v.historial().get(0).estado()).isEqualTo("Despublicado");
        assertThat(v.historial().get(0).despublicado()).isEqualTo("14 de noviembre de 2026");
        assertThat(v.historial().get(1).estado()).isEqualTo("Publicado");
        assertThat(v.historial().get(1).despublicado()).isEqualTo("—");
        assertThat(vista(TipoInforme.FIN, true).historial().get(1).estado()).isEqualTo("Borrador");
    }

    @Test
    void lasReferenciasMarcanLaCursivaConLosAsteriscos() {
        VistaInforme v = vista(TipoInforme.TEC, false);
        var cursivas = v.referencias().stream().flatMap(r -> r.segmentos().stream()).filter(VistaInforme.SegmentoTexto::cursiva)
            .map(VistaInforme.SegmentoTexto::texto).toList();
        assertThat(cursivas).contains("OWASP Top 10", "CVE-2099-0001 detail");
        assertThat(v.referencias().stream().flatMap(r -> r.segmentos().stream()).map(VistaInforme.SegmentoTexto::texto))
            .noneMatch(t -> t.contains("*"));
    }

    @ParameterizedTest
    @EnumSource(TipoInforme.class)
    void unProyectoSinHallazgosDaUnaVistaCompletaYVacia(TipoInforme tipo) {
        VistaInforme v = constructor.construir(DatosDemo.snapshotSinHallazgos(tipo), OpcionesRender.PUBLICADO);
        assertThat(v.totalHallazgos()).isZero();
        assertThat(v.resumenHallazgos()).isEmpty();
        assertThat(v.mitigacion()).isEmpty();
        assertThat(v.registroMatriz()).isEmpty();
        assertThat(v.riesgosPrincipales()).isEmpty();
        assertThat(v.conteoSeveridad()).allSatisfy(f -> assertThat(f.cantidad()).isZero());
        assertThat(v.conteoSeveridad()).allSatisfy(f -> assertThat(f.porcentaje()).isEqualTo("0 %"));
        assertThat(v.avisoAnexoA()).isNull();
    }
}

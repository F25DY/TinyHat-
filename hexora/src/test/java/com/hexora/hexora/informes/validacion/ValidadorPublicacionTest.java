package com.hexora.hexora.informes.validacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.snapshot.InformeSnapshot;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.Area;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.Cia;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.Hallazgo;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.Riesgo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Condiciones para publicar (§4.5), validadas sobre el snapshot. */
class ValidadorPublicacionTest {

    private final ValidadorPublicacion validador = new ValidadorPublicacion(1000, 20000);

    private static Hallazgo completo(String codigo) {
        return new Hallazgo(codigo, "Título", "Aplicar el parche", "Abierta", "ALTA", new Cia(8, 4, 2), 4, 4,
            new Riesgo(16, "ALTO"), List.of(), List.of(), "F2");
    }

    private static Hallazgo con(Hallazgo base, String estado, String mitigacion, Integer probabilidad) {
        return new Hallazgo(base.codigo(), base.titulo(), mitigacion, estado, base.severidad(), base.cia(), probabilidad,
            base.impacto15(), probabilidad == null ? null : base.riesgo(), base.cves(), base.referencias(),
            base.faseSugerida());
    }

    private static Map<String, String> narrativaCompleta(TipoInforme tipo) {
        Map<String, String> narrativa = new HashMap<>();
        tipo.secciones().stream().filter(TipoInforme.SeccionNarrativa::obligatoria)
            .forEach(seccion -> narrativa.put(seccion.clave(), "Texto de " + seccion.etiqueta()));
        return narrativa;
    }

    private static InformeSnapshot snapshot(TipoInforme tipo, List<Area> areas, Map<String, String> narrativa) {
        return snapshot(tipo, areas, narrativa, "2026-11-02", "2026-11-16", 0);
    }

    private static InformeSnapshot snapshot(
        TipoInforme tipo, List<Area> areas, Map<String, String> narrativa, String inicio, String fin, int aceptados
    ) {
        int total = areas.stream().mapToInt(a -> a.hallazgos().size()).sum();
        return new InformeSnapshot(1, "2026.1", "2026-11-20T10:00:00-05:00",
            new InformeSnapshot.DatosInforme(tipo.name(), 1, "TH-2026-AAAA-BBBB-" + tipo.name() + "-v1", "PUBLICADO"),
            new InformeSnapshot.DatosOrganizacion("Org"), new InformeSnapshot.DatosProyecto("Proyecto", inicio, fin),
            new InformeSnapshot.DatosAuditor("Aud"),
            new InformeSnapshot.Metricas(Map.of(), List.of(), Map.of(), List.of(), 0, total, areas.size(), aceptados, 0),
            areas, List.of(), List.of(), narrativa, List.of());
    }

    private static List<Area> unaArea(Hallazgo... hallazgos) {
        return List.of(new Area("Red", "Descripción", List.of(hallazgos)));
    }

    @ParameterizedTest
    @EnumSource(TipoInforme.class)
    void unInformeCompletoDeCualquierTipoSePuedePublicar(TipoInforme tipo) {
        ResultadoValidacion r = validador.validar(snapshot(tipo, unaArea(completo("H-AAAAAA")), narrativaCompleta(tipo)));
        assertThat(r.faltantes()).isEmpty();
        assertThat(r.esValido()).isTrue();
        r.exigirValido();
    }

    @Test
    void sinAlcanceNoSePublicaSinFechasOSinAreas() {
        assertThat(validador.validar(snapshot(TipoInforme.EJE, List.of(), narrativaCompleta(TipoInforme.EJE))).faltantes())
            .singleElement().asString().contains("alcance definido");
        assertThat(validador.validar(snapshot(TipoInforme.EJE, unaArea(completo("H-1")), narrativaCompleta(TipoInforme.EJE),
            null, "2026-11-16", 0)).faltantes()).singleElement().asString().contains("alcance definido");
        assertThat(validador.validar(snapshot(TipoInforme.EJE, unaArea(completo("H-1")), narrativaCompleta(TipoInforme.EJE),
            "2026-11-02", null, 0)).faltantes()).singleElement().asString().contains("alcance definido");
    }

    @Test
    void unProyectoSinHallazgosPeroConAreasSiSePuedePublicar() {
        ResultadoValidacion r = validador.validar(snapshot(TipoInforme.EJE,
            List.of(new Area("Red", null, List.of())), narrativaCompleta(TipoInforme.EJE)));
        assertThat(r.esValido()).isTrue();
    }

    @Test
    void cadaHallazgoDebeTenerMitigacion() {
        ResultadoValidacion r = validador.validar(snapshot(TipoInforme.TEC,
            unaArea(con(completo("H-AAAAAA"), "Abierta", null, 4), con(completo("H-BBBBBB"), "Abierta", "   ", 4)),
            narrativaCompleta(TipoInforme.TEC)));
        assertThat(r.faltantes()).hasSize(2)
            .anyMatch(m -> m.contains("H-AAAAAA") && m.contains("mitigación") && m.contains("Red"))
            .anyMatch(m -> m.contains("H-BBBBBB") && m.contains("mitigación"));
    }

    @Test
    void unHallazgoConEstadoVacioOSinClasificarBloquea() {
        ResultadoValidacion r = validador.validar(snapshot(TipoInforme.TEC,
            unaArea(con(completo("H-AAAAAA"), "", "m", 4), con(completo("H-BBBBBB"), "Pendiente de revisar", "m", 4),
                con(completo("H-CCCCCC"), "Mitigada", "m", 4), con(completo("H-DDDDDD"), "En Progreso", "m", 4)),
            narrativaCompleta(TipoInforme.TEC)));
        assertThat(r.faltantes()).hasSize(2)
            .anyMatch(m -> m.contains("H-AAAAAA") && m.contains("no tiene estado"))
            .anyMatch(m -> m.contains("H-BBBBBB") && m.contains("sin clasificar"));
    }

    @Test
    void unHallazgoSinSeveridadBloquea() {
        Hallazgo sin = new Hallazgo("H-AAAAAA", "T", "m", "Abierta", null, new Cia(1, 1, 1), 3, 1, null, List.of(),
            List.of(), "F3");
        assertThat(validador.validar(snapshot(TipoInforme.TEC, unaArea(sin), narrativaCompleta(TipoInforme.TEC)))
            .faltantes()).anyMatch(m -> m.contains("H-AAAAAA") && m.contains("severidad"));
    }

    @Test
    void lasSeccionesObligatoriasBloqueanSegunElTipo() {
        // EJE: resumenEjecutivo es obligatoria.
        assertThat(validador.validar(snapshot(TipoInforme.EJE, unaArea(completo("H-1")), Map.of())).faltantes())
            .singleElement().asString().contains("Resumen ejecutivo");
        // TEC: alcanceMetodologia.
        assertThat(validador.validar(snapshot(TipoInforme.TEC, unaArea(completo("H-1")), Map.of("conclusiones", "x")))
            .faltantes()).singleElement().asString().contains("Alcance y metodología");
        // FIN: cuatro obligatorias.
        assertThat(validador.validar(snapshot(TipoInforme.FIN, unaArea(completo("H-1")), Map.of())).faltantes())
            .hasSize(4).anyMatch(m -> m.contains("Carta de entrega")).anyMatch(m -> m.contains("Resumen ejecutivo"))
            .anyMatch(m -> m.contains("Alcance y metodología")).anyMatch(m -> m.contains("Conclusiones"));
        // MIT y MR no tienen obligatorias.
        assertThat(validador.validar(snapshot(TipoInforme.MIT, unaArea(completo("H-1")), Map.of())).faltantes()).isEmpty();
        assertThat(validador.validar(snapshot(TipoInforme.MR, unaArea(completo("H-1")), Map.of())).faltantes()).isEmpty();
    }

    @Test
    void unaSeccionEnBlancoCuentaComoFaltante() {
        assertThat(validador.validar(snapshot(TipoInforme.EJE, unaArea(completo("H-1")),
            Map.of("resumenEjecutivo", "   \n  "))).faltantes()).singleElement().asString().contains("Resumen ejecutivo");
    }

    @ParameterizedTest
    @EnumSource(value = TipoInforme.class, names = {"MR", "FIN"})
    void mrYFinExigenProbabilidadEnTodosLosHallazgos(TipoInforme tipo) {
        ResultadoValidacion r = validador.validar(snapshot(tipo,
            unaArea(con(completo("H-AAAAAA"), "Abierta", "m", null)), narrativaCompleta(tipo)));
        assertThat(r.faltantes()).singleElement().asString().contains("H-AAAAAA").contains("probabilidad");
    }

    @Test
    void losOtrosTiposNoExigenProbabilidad() {
        for (TipoInforme tipo : List.of(TipoInforme.TEC, TipoInforme.EJE, TipoInforme.MIT)) {
            assertThat(validador.validar(snapshot(tipo, unaArea(con(completo("H-1"), "Abierta", "m", null)),
                narrativaCompleta(tipo))).faltantes()).as(tipo.name()).isEmpty();
        }
    }

    @Test
    void unaSeccionQueNoEsDelTipoSeRechaza() {
        Map<String, String> narrativa = narrativaCompleta(TipoInforme.EJE);
        narrativa.put("cartaEntrega", "Esto no es de un ejecutivo");
        assertThat(validador.validar(snapshot(TipoInforme.EJE, unaArea(completo("H-1")), narrativa)).faltantes())
            .singleElement().asString().contains("cartaEntrega").contains("no corresponde");
    }

    @Test
    void respetaElLimiteDeCaracteresPorSeccionYDeHallazgos() {
        ValidadorPublicacion estricto = new ValidadorPublicacion(2, 10);
        Map<String, String> narrativa = new HashMap<>(narrativaCompleta(TipoInforme.EJE));
        narrativa.put("resumenEjecutivo", "x".repeat(11));
        InformeSnapshot s = snapshot(TipoInforme.EJE,
            unaArea(completo("H-1"), completo("H-2"), completo("H-3")), narrativa);
        assertThat(estricto.validar(s).faltantes())
            .anyMatch(m -> m.contains("Resumen ejecutivo") && m.contains("máximo de 10"))
            .anyMatch(m -> m.contains("3 vulnerabilidades") && m.contains("2"));
        // Exactamente en el límite es válido.
        narrativa.put("resumenEjecutivo", "x".repeat(10));
        assertThat(estricto.validar(snapshot(TipoInforme.EJE, unaArea(completo("H-1"), completo("H-2")), narrativa))
            .faltantes()).isEmpty();
    }

    @Test
    void losAceptadosCuentanParaElLimiteDeHallazgos() {
        ValidadorPublicacion estricto = new ValidadorPublicacion(2, 20000);
        InformeSnapshot s = snapshot(TipoInforme.EJE, unaArea(completo("H-1"), completo("H-2")),
            narrativaCompleta(TipoInforme.EJE), "2026-11-02", "2026-11-16", 1);
        assertThat(estricto.validar(s).faltantes()).anyMatch(m -> m.contains("3 vulnerabilidades"));
    }

    @Test
    void conMuchosFaltantesSeDetallanLosPrimerosYSeResumeElResto() {
        Hallazgo[] hallazgos = new Hallazgo[40];
        for (int n = 0; n < hallazgos.length; n++) {
            hallazgos[n] = con(completo(String.format("H-%06d", n)), "Abierta", null, 4);
        }
        List<String> faltantes = validador.validar(snapshot(TipoInforme.TEC, unaArea(hallazgos),
            narrativaCompleta(TipoInforme.TEC))).faltantes();
        assertThat(faltantes).hasSize(26);
        assertThat(faltantes.get(25)).contains("15 faltantes más");
    }

    @Test
    void exigirValidoLanzaConLaListaDeFaltantesYSinDetallesInternos() {
        ResultadoValidacion r = validador.validar(snapshot(TipoInforme.FIN, unaArea(completo("H-1")), Map.of()));
        assertThatThrownBy(r::exigirValido).isInstanceOf(InformeValidacionException.class)
            .satisfies(e -> {
                InformeValidacionException ex = (InformeValidacionException) e;
                assertThat(ex.mensajes()).hasSize(4);
                assertThat(String.join(" ", ex.mensajes())).doesNotContain("Exception").doesNotContain("at com.")
                    .doesNotContain("java.");
            });
    }

    @Test
    void elMarcadorDeLasCopiasNpEsElDelPlan() {
        assertThat(ResultadoValidacion.PENDIENTE).isEqualTo("[PENDIENTE DE COMPLETAR POR EL AUDITOR]");
    }
}

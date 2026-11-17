package com.hexora.hexora.riesgo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexora.hexora.riesgo.Severidad.Fase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SeveridadTest {

    @ParameterizedTest
    @CsvSource({"1,INFORMATIVA", "2,BAJA", "3,MEDIA", "4,ALTA", "5,CRITICA"})
    void laSeveridadSeDerivaDelImpacto(int impacto, Severidad esperada) {
        assertThat(Severidad.desdeImpacto(impacto)).isEqualTo(esperada);
    }

    @Test
    void rechazaImpactosFueraDeEscala() {
        assertThatThrownBy(() -> Severidad.desdeImpacto(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Severidad.desdeImpacto(6)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elOrdenDeGravedadEsCriticaAltaMediaBajaInformativa() {
        assertThat(Severidad.CRITICA.posicionDeGravedad()).isZero();
        assertThat(Severidad.ALTA.posicionDeGravedad()).isEqualTo(1);
        assertThat(Severidad.MEDIA.posicionDeGravedad()).isEqualTo(2);
        assertThat(Severidad.BAJA.posicionDeGravedad()).isEqualTo(3);
        assertThat(Severidad.INFORMATIVA.posicionDeGravedad()).isEqualTo(4);
    }

    /** Tabla de §4.4. */
    @ParameterizedTest
    @CsvSource({"CRITICA,F1", "ALTA,F2", "MEDIA,F3", "BAJA,F3", "INFORMATIVA,F3"})
    void laFaseSugeridaSigueLaTablaDelPlan(Severidad severidad, Fase fase) {
        assertThat(severidad.faseSugerida()).isEqualTo(fase);
    }

    @Test
    void lasFasesTienenElTextoDelPlan() {
        assertThat(Fase.F1.descripcion()).isEqualTo("F1 — Contención inmediata (0–7 días)");
        assertThat(Fase.F2.descripcion()).isEqualTo("F2 — Corto plazo (8–30 días)");
        assertThat(Fase.F3.descripcion()).isEqualTo("F3 — Endurecimiento (31–90 días)");
    }

    @Test
    void lasEtiquetasLlevanTilde() {
        assertThat(Severidad.CRITICA.etiqueta()).isEqualTo("Crítica");
        assertThat(Severidad.INFORMATIVA.etiqueta()).isEqualTo("Informativa");
    }
}

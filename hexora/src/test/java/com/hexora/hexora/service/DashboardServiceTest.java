package com.hexora.hexora.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DashboardServiceTest {

    @Test
    void percentageIsOneToHundredAndZeroOnlyWhenThereIsNothing() {
        assertThat(DashboardService.porcentaje(0, 10)).isZero();
        assertThat(DashboardService.porcentaje(5, 0)).isZero();
        assertThat(DashboardService.porcentaje(1, 100_000)).as("un hallazgo siempre se ve").isEqualTo(1);
        assertThat(DashboardService.porcentaje(5, 10)).isEqualTo(50);
        assertThat(DashboardService.porcentaje(10, 10)).isEqualTo(100);
        assertThat(DashboardService.porcentaje(1, 8)).as("12,5 se redondea hacia arriba").isEqualTo(13);
    }

    @Test
    void levelsAreComparedWithoutAccentsOrCase() {
        assertThat(DashboardService.normalizar("Crítico")).isEqualTo("critico");
        assertThat(DashboardService.normalizar(" CRITICO ")).isEqualTo("critico");
        assertThat(DashboardService.normalizar("En Progreso")).isEqualTo("en progreso");
        assertThat(DashboardService.normalizar(null)).isEmpty();
    }
}

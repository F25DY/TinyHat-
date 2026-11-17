package com.hexora.hexora.informes.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ControlConcurrenciaInformesTest {

    @Test
    void conPermisoEjecutaYLoDevuelve() {
        ControlConcurrenciaInformes control = new ControlConcurrenciaInformes(2, 100);
        assertThat(control.ejecutar(() -> "listo")).isEqualTo("listo");
        assertThat(control.disponibles()).isEqualTo(2);
    }

    @Test
    void sinPermisoTrasLaEsperaResponde503() throws Exception {
        ControlConcurrenciaInformes control = new ControlConcurrenciaInformes(1, 100);
        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        ExecutorService hilos = Executors.newSingleThreadExecutor();
        try {
            Future<String> ocupado = hilos.submit(() -> control.ejecutar(() -> {
                dentro.countDown();
                try {
                    soltar.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "primero";
            }));
            assertThat(dentro.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> control.ejecutar(() -> "segundo")).isInstanceOf(ErroresInforme.Ocupado.class);
            soltar.countDown();
            assertThat(ocupado.get(5, TimeUnit.SECONDS)).isEqualTo("primero");
            assertThat(control.ejecutar(() -> "tercero")).as("el permiso se libera").isEqualTo("tercero");
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test
    void siElTrabajoFallaElPermisoSeLibera() {
        ControlConcurrenciaInformes control = new ControlConcurrenciaInformes(1, 100);
        assertThatThrownBy(() -> control.ejecutar(() -> {
            throw new IllegalStateException("falla");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(control.disponibles()).isEqualTo(1);
    }
}

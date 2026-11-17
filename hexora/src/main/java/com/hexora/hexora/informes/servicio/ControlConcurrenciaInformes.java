package com.hexora.hexora.informes.servicio;

import com.hexora.hexora.informes.InformesProperties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Limita cuántos informes se generan a la vez ({@code tinyhat.informes.concurrencia-max}). Generar es costoso en CPU y
 * memoria; sin permiso tras una espera corta se responde "ocupado" (503) en lugar de encolar sin límite (§8.4).
 */
@Component
public class ControlConcurrenciaInformes {

    private final Semaphore permisos;
    private final long esperaMs;

    @Autowired
    public ControlConcurrenciaInformes(InformesProperties propiedades) {
        this(propiedades.getConcurrenciaMax(), propiedades.getEsperaConcurrenciaMs());
    }

    public ControlConcurrenciaInformes(int maximo, long esperaMs) {
        this.permisos = new Semaphore(maximo, true);
        this.esperaMs = esperaMs;
    }

    public <T> T ejecutar(Supplier<T> trabajo) {
        boolean adquirido;
        try {
            adquirido = permisos.tryAcquire(esperaMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErroresInforme.Ocupado();
        }
        if (!adquirido) {
            throw new ErroresInforme.Ocupado();
        }
        try {
            return trabajo.get();
        } finally {
            permisos.release();
        }
    }

    public int disponibles() {
        return permisos.availablePermits();
    }
}

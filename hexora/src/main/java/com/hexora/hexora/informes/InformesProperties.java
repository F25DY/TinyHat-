package com.hexora.hexora.informes;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configuración del módulo de informes ({@code tinyhat.informes.*}). */
@Validated
@ConfigurationProperties(prefix = "tinyhat.informes")
public class InformesProperties {

    /** Versión de las plantillas y textos fijos. Cualquier cambio en ellos sube este número. */
    @NotBlank
    private String plantillaVersion = "2026.2";

    /** Máximo de vulnerabilidades de un proyecto que puede incluir un informe. */
    @Min(1)
    private int maxHallazgos = 1000;

    /** Máximo de caracteres de cada sección de la narrativa. */
    @Min(1)
    private int maxNarrativaCaracteres = 20000;

    /** Directorio de los archivos publicados (fuera de las rutas servidas; incluirlo en el respaldo, §8.3). */
    @NotBlank
    private String dir = "./var/informes";

    /** Tamaño máximo de un archivo generado; si se supera, se aborta. */
    @Min(1)
    private long maxArchivoBytes = 26_214_400L;

    /** Informes que pueden generarse a la vez; sin permiso se responde 503. */
    @Min(1)
    private int concurrenciaMax = 2;

    /** Espera máxima por un permiso de generación, en milisegundos. */
    @Min(0)
    private long esperaConcurrenciaMs = 500;

    private final Notificacion notificacion = new Notificacion();

    /** Aviso a los CLIENTE_ADMIN al publicar (el notificador por defecto solo escribe en el registro). */
    public static class Notificacion {

        private boolean habilitada = false;

        public boolean isHabilitada() {
            return habilitada;
        }

        public void setHabilitada(boolean habilitada) {
            this.habilitada = habilitada;
        }
    }

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public long getMaxArchivoBytes() {
        return maxArchivoBytes;
    }

    public void setMaxArchivoBytes(long maxArchivoBytes) {
        this.maxArchivoBytes = maxArchivoBytes;
    }

    public int getConcurrenciaMax() {
        return concurrenciaMax;
    }

    public void setConcurrenciaMax(int concurrenciaMax) {
        this.concurrenciaMax = concurrenciaMax;
    }

    public long getEsperaConcurrenciaMs() {
        return esperaConcurrenciaMs;
    }

    public void setEsperaConcurrenciaMs(long esperaConcurrenciaMs) {
        this.esperaConcurrenciaMs = esperaConcurrenciaMs;
    }

    public Notificacion getNotificacion() {
        return notificacion;
    }

    public String getPlantillaVersion() {
        return plantillaVersion;
    }

    public void setPlantillaVersion(String plantillaVersion) {
        this.plantillaVersion = plantillaVersion;
    }

    public int getMaxHallazgos() {
        return maxHallazgos;
    }

    public void setMaxHallazgos(int maxHallazgos) {
        this.maxHallazgos = maxHallazgos;
    }

    public int getMaxNarrativaCaracteres() {
        return maxNarrativaCaracteres;
    }

    public void setMaxNarrativaCaracteres(int maxNarrativaCaracteres) {
        this.maxNarrativaCaracteres = maxNarrativaCaracteres;
    }
}

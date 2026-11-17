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
    private String plantillaVersion = "2026.1";

    /** Máximo de vulnerabilidades de un proyecto que puede incluir un informe. */
    @Min(1)
    private int maxHallazgos = 1000;

    /** Máximo de caracteres de cada sección de la narrativa. */
    @Min(1)
    private int maxNarrativaCaracteres = 20000;

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

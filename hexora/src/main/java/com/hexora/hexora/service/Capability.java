package com.hexora.hexora.service;

public enum Capability {
    MODULO_RED("modulo_red"),
    INFORMES_PDF("informes_pdf"),
    MONITOREO_CONTINUO("monitoreo_continuo");

    private final String databaseValue;

    Capability(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String getDatabaseValue() {
        return databaseValue;
    }
}

package com.hexora.hexora.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "capacidad_cuenta")
public class CapacidadCuenta {

    @EmbeddedId
    private CapacidadCuentaId id;

    @Column(nullable = false)
    private boolean habilitada;

    protected CapacidadCuenta() {
    }

    public CapacidadCuentaId getId() {
        return id;
    }

    public void setId(CapacidadCuentaId id) {
        this.id = id;
    }

    public boolean isHabilitada() {
        return habilitada;
    }

    public void setHabilitada(boolean habilitada) {
        this.habilitada = habilitada;
    }
}

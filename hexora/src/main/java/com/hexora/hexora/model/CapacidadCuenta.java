package com.hexora.hexora.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "capacidad_cuenta")
@Filter(name = "accountFilter", condition = "cuenta_id = :tenantId")
public class CapacidadCuenta {

    @EmbeddedId
    private CapacidadCuentaId id;

    @Column(nullable = false)
    private boolean habilitada;

    public CapacidadCuenta() {
    }

    public CapacidadCuenta(CapacidadCuentaId id, boolean habilitada) {
        this.id = id;
        this.habilitada = habilitada;
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

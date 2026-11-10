package com.hexora.hexora.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class CapacidadCuentaId implements Serializable {

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Column(name = "capacidad", nullable = false, length = 40)
    private String capacidad;

    protected CapacidadCuentaId() {
    }

    public CapacidadCuentaId(Long cuentaId, String capacidad) {
        this.cuentaId = cuentaId;
        this.capacidad = capacidad;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public String getCapacidad() {
        return capacidad;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CapacidadCuentaId that)) {
            return false;
        }
        return Objects.equals(cuentaId, that.cuentaId)
            && Objects.equals(capacidad, that.capacidad);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cuentaId, capacidad);
    }
}

package com.hexora.hexora.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class AsignacionAuditorProyectoId implements Serializable {

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    protected AsignacionAuditorProyectoId() {
    }

    public AsignacionAuditorProyectoId(Long usuarioId, Long proyectoId, Long cuentaId) {
        this.usuarioId = usuarioId;
        this.proyectoId = proyectoId;
        this.cuentaId = cuentaId;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getProyectoId() {
        return proyectoId;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AsignacionAuditorProyectoId that)) {
            return false;
        }
        return Objects.equals(usuarioId, that.usuarioId)
            && Objects.equals(proyectoId, that.proyectoId)
            && Objects.equals(cuentaId, that.cuentaId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(usuarioId, proyectoId, cuentaId);
    }
}

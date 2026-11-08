package com.hexora.hexora.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "asignacion_auditor_proyecto")
public class AsignacionAuditorProyecto {

    @EmbeddedId
    private AsignacionAuditorProyectoId id;

    @jakarta.persistence.Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    protected AsignacionAuditorProyecto() {
    }

    public AsignacionAuditorProyectoId getId() {
        return id;
    }

    public void setId(AsignacionAuditorProyectoId id) {
        this.id = id;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }
}

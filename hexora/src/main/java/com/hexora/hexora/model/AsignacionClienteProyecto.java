package com.hexora.hexora.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Filter;

/**
 * Proyecto al que el auditor dio acceso a un administrador de la MiPyme (CLIENTE_ADMIN). Retirar el acceso marca
 * {@code retiradoEn}; la fila se conserva como historia.
 */
@Entity
@Table(name = "asignacion_cliente_proyecto")
@Filter(name = "accountFilter", condition = "cuenta_id = :tenantId")
public class AsignacionClienteProyecto {

    @EmbeddedId
    private AsignacionAuditorProyectoId id;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "creado_por", updatable = false)
    private Long creadoPor;

    @Column(name = "retirado_en")
    private LocalDateTime retiradoEn;

    protected AsignacionClienteProyecto() {
    }

    public AsignacionClienteProyecto(AsignacionAuditorProyectoId id, Long creadoPor, LocalDateTime creadoEn) {
        this.id = id;
        this.creadoPor = creadoPor;
        this.creadoEn = creadoEn;
    }

    public AsignacionAuditorProyectoId getId() {
        return id;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public Long getCreadoPor() {
        return creadoPor;
    }

    public LocalDateTime getRetiradoEn() {
        return retiradoEn;
    }

    public boolean estaVigente() {
        return retiradoEn == null;
    }

    public void retirar(LocalDateTime ahora) {
        this.retiradoEn = ahora;
    }

    public void reactivar() {
        this.retiradoEn = null;
    }
}

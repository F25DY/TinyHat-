package com.hexora.hexora.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name= "auditoria")
public class Auditoria {

    //Definimos Id
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria")
    private Long idAuditoria;
    
    @Column(nullable= false, length=150)
    private String nombre;

    @Column(name = "tipo_auditoria", nullable = false, length = 80)
    private String tipoAuditoria;

    @Column(nullable = false)
    private LocalDate fechaInico;

    @Column(nullable = false)
    private LocalDate fechaFin;

    @Column(nullable = false, length = 30)
    private String estado;

    @ManyToOne
    @JoinColumn(name = "organizacion_cliente_id", nullable = false)
    private Empresa empresa;

    @Column(name = "cuenta_id", nullable = false, insertable = false, updatable = false)
    private Long cuentaId;

    @Column(name = "public_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String publicId;

    //@ManyToOne
    //@JoinColumn(name = "auditor", nullable = false)
    //private Usuario auditor;
    
    //Constructores
    public Auditoria() {
    }

    public Auditoria(Long idAuditoria, String nombre, String tipoAuditoria, LocalDate fechaInico, LocalDate fechaFin, String estado,
            Empresa empresa) {
        this.idAuditoria = idAuditoria;
        this.nombre = nombre;
        this.tipoAuditoria = tipoAuditoria;
        this.fechaInico = fechaInico;
        this.fechaFin = fechaFin;
        this.estado = estado;
        this.empresa = empresa;
    }

    //Getters y Setters

    public Long getIdAuditoria() {
        return idAuditoria;
    }

    public Long getId() {
        return idAuditoria;
    }

    public void setIdAuditoria(Long idAuditoria) {
        this.idAuditoria = idAuditoria;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTipoAuditoria() {
        return tipoAuditoria;
    }

    public void setTipoAuditoria(String tipoAuditoria) {
        this.tipoAuditoria = tipoAuditoria;
    }

    public LocalDate getFechaInico() {
        return fechaInico;
    }

    public LocalDate getFechaInicio() {
        return fechaInico;
    }

    public void setFechaInico(LocalDate fechaInico) {
        this.fechaInico = fechaInico;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(LocalDate fechaFin) {
        this.fechaFin = fechaFin;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public void setEmpresa(Empresa empresa) {
        this.empresa = empresa;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    @PrePersist
    void generatePublicId() {
        publicId = PublicIds.ensure(publicId);
    }

    
}

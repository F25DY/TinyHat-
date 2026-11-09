package com.hexora.hexora.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.EntityListeners;
import java.time.LocalDateTime;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "informe")
@Filter(name = "accountFilter", condition = "cuenta_id = :tenantId")
@EntityListeners(TenantScopedEntityListener.class)
public class Informe implements TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String publicId;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Column(name = "organizacion_id", nullable = false)
    private Long organizacionId;

    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(name = "publicado_en")
    private LocalDateTime publicadoEn;

    @Column(name = "publicado_por")
    private Long publicadoPor;

    @Column(name = "hash_contenido", length = 64, columnDefinition = "CHAR(64)")
    private String hashContenido;

    protected Informe() {
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    @Override
    public void setCuentaId(Long cuentaId) {
        this.cuentaId = cuentaId;
    }

    public Long getOrganizacionId() {
        return organizacionId;
    }

    public void setOrganizacionId(Long organizacionId) {
        this.organizacionId = organizacionId;
    }

    public Long getProyectoId() {
        return proyectoId;
    }

    public void setProyectoId(Long proyectoId) {
        this.proyectoId = proyectoId;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDateTime getPublicadoEn() {
        return publicadoEn;
    }

    public void setPublicadoEn(LocalDateTime publicadoEn) {
        this.publicadoEn = publicadoEn;
    }

    public Long getPublicadoPor() {
        return publicadoPor;
    }

    public void setPublicadoPor(Long publicadoPor) {
        this.publicadoPor = publicadoPor;
    }

    public String getHashContenido() {
        return hashContenido;
    }

    public void setHashContenido(String hashContenido) {
        this.hashContenido = hashContenido;
    }

    @PrePersist
    void generatePublicId() {
        publicId = PublicIds.ensure(publicId);
    }
}

package com.hexora.hexora.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.EntityListeners;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "usuario")
@Filter(name = "accountFilter", condition = "cuenta_id = :tenantId")
@EntityListeners(TenantScopedEntityListener.class)
public class Usuario implements TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String publicId;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Column(name = "organizacion_id")
    private Long organizacionId;

    @Column(nullable = false, length = 20)
    private String rol;

    @Column(nullable = false, length = 30)
    private String estado;

    @Column(name = "email_normalizado", nullable = false, length = 254)
    private String emailNormalizado;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    public Usuario() {
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

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getEmailNormalizado() {
        return emailNormalizado;
    }

    public void setEmailNormalizado(String emailNormalizado) {
        this.emailNormalizado = emailNormalizado;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    @PrePersist
    void prepareForInsert() {
        publicId = PublicIds.ensure(publicId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (creadoEn == null) {
            creadoEn = now;
        }
        actualizadoEn = now;
    }

    @PreUpdate
    void updateTimestamp() {
        actualizadoEn = LocalDateTime.now(ZoneOffset.UTC);
    }
}

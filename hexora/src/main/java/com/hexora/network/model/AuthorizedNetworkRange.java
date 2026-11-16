package com.hexora.network.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import com.hexora.hexora.model.TenantScopedEntity;
import com.hexora.hexora.model.TenantScopedEntityListener;
import jakarta.persistence.EntityListeners;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "rango_autorizado")
@Filter(name = "accountFilter", condition = "cuenta_id = :tenantId")
@EntityListeners(TenantScopedEntityListener.class)
public class AuthorizedNetworkRange implements TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false, updatable = false)
    private Long cuentaId;

    @Column(name = "proyecto_id", nullable = false, updatable = false)
    private Long proyectoId;

    @Column(nullable = false, length = 18)
    private String cidr;

    @Column(name = "referencia_autorizacion", nullable = false, length = 255)
    private String referenciaAutorizacion;

    @Column(name = "declarado_por", nullable = false, updatable = false)
    private Long declaradoPor;

    @Column(name = "declarado_en", nullable = false, updatable = false)
    private LocalDateTime declaradoEn;

    @Column(name = "vigente_hasta")
    private LocalDateTime vigenteHasta;

    public Long getId() {
        return id;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    @Override
    public void setCuentaId(Long cuentaId) {
        this.cuentaId = cuentaId;
    }

    public Long getProyectoId() {
        return proyectoId;
    }

    public void setProyectoId(Long proyectoId) {
        this.proyectoId = proyectoId;
    }

    public String getCidr() {
        return cidr;
    }

    public void setCidr(String cidr) {
        this.cidr = cidr;
    }

    public String getReferenciaAutorizacion() {
        return referenciaAutorizacion;
    }

    public void setReferenciaAutorizacion(String referenciaAutorizacion) {
        this.referenciaAutorizacion = referenciaAutorizacion;
    }

    public Long getDeclaradoPor() {
        return declaradoPor;
    }

    public void setDeclaradoPor(Long declaradoPor) {
        this.declaradoPor = declaradoPor;
    }

    public LocalDateTime getDeclaradoEn() {
        return declaradoEn;
    }

    public void setDeclaradoEn(LocalDateTime declaradoEn) {
        this.declaradoEn = declaradoEn;
    }

    public LocalDateTime getVigenteHasta() {
        return vigenteHasta;
    }

    public void setVigenteHasta(LocalDateTime vigenteHasta) {
        this.vigenteHasta = vigenteHasta;
    }
}

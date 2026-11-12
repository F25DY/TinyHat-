package com.hexora.hexora.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Immutable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "audit_event")
@Filter(name = "accountFilter", condition = "cuenta_id = :tenantId")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ts", nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false, length = 80)
    private String evento;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Column(name = "organizacion_id")
    private Long organizacionId;

    @Column(length = 45)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "recurso_tipo", length = 80)
    private String recursoTipo;

    @Column(name = "recurso_public_id", length = 36, columnDefinition = "CHAR(36)")
    private String recursoPublicId;

    @Column(nullable = false, length = 30)
    private String resultado;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> detalle;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    public AuditEvent() {
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getEvento() {
        return evento;
    }

    public void setEvento(String evento) {
        this.evento = evento;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public void setCuentaId(Long cuentaId) {
        this.cuentaId = cuentaId;
    }

    public Long getOrganizacionId() {
        return organizacionId;
    }

    public void setOrganizacionId(Long organizacionId) {
        this.organizacionId = organizacionId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getRecursoTipo() {
        return recursoTipo;
    }

    public void setRecursoTipo(String recursoTipo) {
        this.recursoTipo = recursoTipo;
    }

    public String getRecursoPublicId() {
        return recursoPublicId;
    }

    public void setRecursoPublicId(String recursoPublicId) {
        this.recursoPublicId = recursoPublicId;
    }

    public String getResultado() {
        return resultado;
    }

    public void setResultado(String resultado) {
        this.resultado = resultado;
    }

    public Map<String, Object> getDetalle() {
        return detalle;
    }

    public void setDetalle(Map<String, Object> detalle) {
        this.detalle = detalle;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    @PrePersist
    void setUtcTimestamp() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}

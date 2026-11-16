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
import java.time.ZoneOffset;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "informe")
@Filter(name = "accountFilter", condition = "cuenta_id = :tenantId")
@EntityListeners(TenantScopedEntityListener.class)
public class Informe implements TenantScopedEntity {

    public static final String ESTADO_BORRADOR = "BORRADOR";
    public static final String ESTADO_PUBLICADO = "PUBLICADO";

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

    @Column(nullable = false, length = 200)
    private String titulo = "";

    @Column(nullable = false, length = 20)
    private String audiencia = "TECNICO";

    @Column(nullable = false, length = 10)
    private String formato = "PDF";

    @Column(name = "esquema_version", nullable = false)
    private int esquemaVersion = 1;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(name = "publicado_en")
    private LocalDateTime publicadoEn;

    @Column(name = "publicado_por")
    private Long publicadoPor;

    @Column(name = "hash_contenido", length = 64, columnDefinition = "CHAR(64)")
    private String hashContenido;

    /** Immutable JSON snapshot of the report content; its SHA-256 is {@link #hashContenido}. */
    @Column(name = "contenido_json", columnDefinition = "LONGTEXT")
    private String contenidoJson;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "creado_por", updatable = false)
    private Long creadoPor;

    public Informe() {
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

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getAudiencia() {
        return audiencia;
    }

    public void setAudiencia(String audiencia) {
        this.audiencia = audiencia;
    }

    public String getFormato() {
        return formato;
    }

    public void setFormato(String formato) {
        this.formato = formato;
    }

    public int getEsquemaVersion() {
        return esquemaVersion;
    }

    public void setEsquemaVersion(int esquemaVersion) {
        this.esquemaVersion = esquemaVersion;
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

    public String getContenidoJson() {
        return contenidoJson;
    }

    public void setContenidoJson(String contenidoJson) {
        this.contenidoJson = contenidoJson;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public Long getCreadoPor() {
        return creadoPor;
    }

    public void setCreadoPor(Long creadoPor) {
        this.creadoPor = creadoPor;
    }

    @PrePersist
    void prepareForInsert() {
        publicId = PublicIds.ensure(publicId);
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}

package com.hexora.hexora.model;

import com.hexora.hexora.informes.FormatoInforme;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.Filter;

/**
 * Archivo generado al publicar una versión. La base guarda solo la ruta relativa dentro del directorio de informes
 * y el SHA-256 con el que se verifica cada descarga. Es inmutable: ninguna columna se actualiza.
 */
@Entity
@Table(name = "informe_archivo")
@Filter(name = "accountFilter", condition = "cuenta_id = :tenantId")
@EntityListeners(TenantScopedEntityListener.class)
public class InformeArchivo implements TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String publicId;

    @Column(name = "cuenta_id", nullable = false, updatable = false)
    private Long cuentaId;

    @Column(name = "version_id", nullable = false, updatable = false)
    private Long versionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4, updatable = false)
    private FormatoInforme formato;

    @Column(name = "ruta_relativa", nullable = false, length = 255, updatable = false)
    private String rutaRelativa;

    @Column(nullable = false, length = 64, columnDefinition = "CHAR(64)", updatable = false)
    private String sha256;

    @Column(nullable = false, updatable = false)
    private long bytes;

    @Column(nullable = false, length = 100, updatable = false)
    private String mime;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    protected InformeArchivo() {
    }

    /** Con el UUID público ya elegido (el mismo que lleva el nombre del archivo en disco). */
    public InformeArchivo(
        String publicId, Long versionId, FormatoInforme formato, String rutaRelativa, String sha256, long bytes
    ) {
        this(versionId, formato, rutaRelativa, sha256, bytes);
        this.publicId = publicId;
    }

    public InformeArchivo(Long versionId, FormatoInforme formato, String rutaRelativa, String sha256, long bytes) {
        this.versionId = versionId;
        this.formato = formato;
        this.rutaRelativa = rutaRelativa;
        this.sha256 = sha256;
        this.bytes = bytes;
        this.mime = formato.mime();
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    @Override
    public Long getCuentaId() {
        return cuentaId;
    }

    @Override
    public void setCuentaId(Long cuentaId) {
        this.cuentaId = cuentaId;
    }

    public Long getVersionId() {
        return versionId;
    }

    public FormatoInforme getFormato() {
        return formato;
    }

    public String getRutaRelativa() {
        return rutaRelativa;
    }

    public String getSha256() {
        return sha256;
    }

    public long getBytes() {
        return bytes;
    }

    public String getMime() {
        return mime;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    @PrePersist
    void prepararInsercion() {
        publicId = PublicIds.ensure(publicId);
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}

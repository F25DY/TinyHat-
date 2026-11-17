package com.hexora.hexora.model;

import com.hexora.hexora.informes.TipoInforme;
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
 * Serie de informes de un proyecto para un tipo (MR, TEC, EJE, MIT, FIN): hay una por proyecto y tipo. Sus
 * versiones viven en {@link InformeVersion}. {@code organizacionId} está desnormalizado (y la base lo ata al proyecto
 * con una clave foránea compuesta) para filtrar por organización dentro de la consulta.
 */
@Entity
@Table(name = "informe")
@Filter(name = "accountFilter", condition = "cuenta_id = :tenantId")
@EntityListeners(TenantScopedEntityListener.class)
public class Informe implements TenantScopedEntity {

    /** Alcance de la v1: siempre el proyecto completo. La columna queda reservada para filtros por área. */
    public static final String ALCANCE_PROYECTO = "PROYECTO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String publicId;

    @Column(name = "cuenta_id", nullable = false, updatable = false)
    private Long cuentaId;

    @Column(name = "organizacion_id", nullable = false, updatable = false)
    private Long organizacionId;

    @Column(name = "proyecto_id", nullable = false, updatable = false)
    private Long proyectoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3, updatable = false)
    private TipoInforme tipo;

    @Column(nullable = false, length = 16, updatable = false)
    private String alcance = ALCANCE_PROYECTO;

    /** Año de creación de la serie; forma parte del ID del documento. */
    @Column(nullable = false, updatable = false)
    private short anio;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "creado_por", nullable = false, updatable = false)
    private Long creadoPor;

    protected Informe() {
    }

    public Informe(Long organizacionId, Long proyectoId, TipoInforme tipo, short anio, Long creadoPor) {
        this.organizacionId = organizacionId;
        this.proyectoId = proyectoId;
        this.tipo = tipo;
        this.anio = anio;
        this.creadoPor = creadoPor;
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

    public Long getOrganizacionId() {
        return organizacionId;
    }

    public Long getProyectoId() {
        return proyectoId;
    }

    public TipoInforme getTipo() {
        return tipo;
    }

    public String getAlcance() {
        return alcance;
    }

    public short getAnio() {
        return anio;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public Long getCreadoPor() {
        return creadoPor;
    }

    @PrePersist
    void prepararInsercion() {
        publicId = PublicIds.ensure(publicId);
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}

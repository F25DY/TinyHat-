package com.hexora.hexora.model;

import com.hexora.hexora.informes.EstadoVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Una versión de un {@link Informe}. Nace como BORRADOR (datos vivos y narrativa editable); al publicarse congela el
 * snapshot y su SHA-256 y ya no se edita. La única modificación posterior es PUBLICADO a DESPUBLICADO con motivo.
 *
 * <p>No hay setters de estado ni de snapshot: todo cambio pasa por {@link #publicar}, {@link #despublicar} y
 * {@link #actualizarNarrativa}, que aplican las invariantes I3 e I5. La base impone además I1 e I2 (un solo
 * borrador y un solo publicado por serie) y la coherencia de los campos de cada estado.
 */
@Entity
@Table(name = "informe_version")
@Filter(name = "accountFilter", condition = "cuenta_id = :tenantId")
@EntityListeners(TenantScopedEntityListener.class)
public class InformeVersion implements TenantScopedEntity {

    public static final int MOTIVO_MINIMO = 10;
    public static final int MOTIVO_MAXIMO = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String publicId;

    @Column(name = "cuenta_id", nullable = false, updatable = false)
    private Long cuentaId;

    @Column(name = "informe_id", nullable = false, updatable = false)
    private Long informeId;

    @Column(nullable = false, updatable = false)
    private int numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EstadoVersion estado = EstadoVersion.BORRADOR;

    /** Mapa de sección a Markdown original, por ejemplo con la clave resumenEjecutivo. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "narrativa_json", nullable = false, columnDefinition = "json")
    private String narrativaJson = "{}";

    /** Snapshot canónico congelado al publicar. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", columnDefinition = "json")
    private String snapshotJson;

    @Column(name = "snapshot_sha256", length = 64, columnDefinition = "CHAR(64)")
    private String snapshotSha256;

    @Column(name = "plantilla_version", length = 16)
    private String plantillaVersion;

    @Column(name = "autor_id", nullable = false, updatable = false)
    private Long autorId;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Column(name = "publicado_en")
    private LocalDateTime publicadoEn;

    @Column(name = "despublicado_en")
    private LocalDateTime despublicadoEn;

    @Column(name = "motivo_despublicacion", length = MOTIVO_MAXIMO)
    private String motivoDespublicacion;

    /** Bloqueo optimista: dos ediciones simultáneas de la misma versión no se pisan en silencio. */
    @Version
    @Column(name = "version_bloqueo", nullable = false)
    private Integer versionBloqueo;

    protected InformeVersion() {
    }

    /** Crea un borrador con la narrativa indicada (JSON; objeto vacío si es la primera versión). */
    public InformeVersion(Long informeId, int numero, Long autorId, String narrativaJson) {
        this.informeId = informeId;
        this.numero = numero;
        this.autorId = autorId;
        this.narrativaJson = narrativaJson == null ? "{}" : narrativaJson;
    }

    /** Reemplaza la narrativa. Solo un borrador es editable (I3). */
    public void actualizarNarrativa(String nuevaNarrativaJson, LocalDateTime ahora) {
        if (estado != EstadoVersion.BORRADOR) {
            throw new IllegalStateException("Solo un borrador puede editarse");
        }
        this.narrativaJson = nuevaNarrativaJson;
        this.actualizadoEn = ahora;
    }

    /** BORRADOR a PUBLICADO: congela el snapshot (I5). */
    public void publicar(String snapshot, String sha256, String plantilla, LocalDateTime ahora) {
        if (!estado.puedePasarA(EstadoVersion.PUBLICADO)) {
            throw new IllegalStateException("Solo un borrador puede publicarse");
        }
        if (snapshot == null || sha256 == null || plantilla == null) {
            throw new IllegalArgumentException("Publicar exige snapshot, hash y versión de plantilla");
        }
        this.snapshotJson = snapshot;
        this.snapshotSha256 = sha256;
        this.plantillaVersion = plantilla;
        this.publicadoEn = ahora;
        this.actualizadoEn = ahora;
        this.estado = EstadoVersion.PUBLICADO;
    }

    /** PUBLICADO a DESPUBLICADO con motivo obligatorio (I5). Nada más cambia. */
    public void despublicar(String motivo, LocalDateTime ahora) {
        if (!estado.puedePasarA(EstadoVersion.DESPUBLICADO)) {
            throw new IllegalStateException("Solo una versión publicada puede despublicarse");
        }
        String limpio = motivo == null ? "" : motivo.strip();
        if (limpio.length() < MOTIVO_MINIMO || limpio.length() > MOTIVO_MAXIMO) {
            throw new IllegalArgumentException("El motivo debe tener entre 10 y 500 caracteres");
        }
        this.motivoDespublicacion = limpio;
        this.despublicadoEn = ahora;
        this.actualizadoEn = ahora;
        this.estado = EstadoVersion.DESPUBLICADO;
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

    public Long getInformeId() {
        return informeId;
    }

    public int getNumero() {
        return numero;
    }

    public EstadoVersion getEstado() {
        return estado;
    }

    public String getNarrativaJson() {
        return narrativaJson;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public String getSnapshotSha256() {
        return snapshotSha256;
    }

    public String getPlantillaVersion() {
        return plantillaVersion;
    }

    public Long getAutorId() {
        return autorId;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    public LocalDateTime getPublicadoEn() {
        return publicadoEn;
    }

    public LocalDateTime getDespublicadoEn() {
        return despublicadoEn;
    }

    public String getMotivoDespublicacion() {
        return motivoDespublicacion;
    }

    @PrePersist
    void prepararInsercion() {
        publicId = PublicIds.ensure(publicId);
        LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);
        if (creadoEn == null) {
            creadoEn = ahora;
        }
        if (actualizadoEn == null) {
            actualizadoEn = ahora;
        }
    }

    /** I3 e I5: una versión publicada o despublicada nunca se elimina. */
    @PreRemove
    void impedirEliminarLoPublicado() {
        if (!estado.permiteEliminar()) {
            throw new IllegalStateException("Una versión publicada o despublicada no puede eliminarse");
        }
    }
}

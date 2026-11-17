package com.hexora.hexora.repository;

import com.hexora.hexora.informes.EstadoVersion;
import com.hexora.hexora.model.InformeVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Versiones de informe. Las consultas "visibles" aplican la regla de acceso dentro del propio JPQL, no después de
 * cargar la fila:
 * <ul>
 *   <li>AUDITOR: todas las versiones (borrador, publicada, despublicada) de los proyectos a los que está asignado.</li>
 *   <li>CLIENTE_ADMIN: solo versiones PUBLICADAS de informes de su organización.</li>
 *   <li>Cualquier otro rol, u otra cuenta u organización: nada.</li>
 * </ul>
 */
public interface InformeVersionRepository extends JpaRepository<InformeVersion, Long> {

    String VISIBLE = """
        i.id = v.informeId
        and i.cuentaId = v.cuentaId
        and v.cuentaId = :cuentaId
        and (
            (:rol = 'AUDITOR' and exists (
                select a.id.usuarioId from AsignacionAuditorProyecto a
                where a.id.proyectoId = i.proyectoId
                  and a.id.cuentaId = i.cuentaId
                  and a.id.usuarioId = :usuarioId))
            or
            (:rol = 'CLIENTE_ADMIN'
                and i.organizacionId = :organizacionId
                and v.estado = com.hexora.hexora.informes.EstadoVersion.PUBLICADO)
        )
        """;

    /** Una versión por su identificador público, solo si el usuario puede verla; vacío equivale a 404. */
    @Query("select v from InformeVersion v, Informe i where v.publicId = :publicId and " + VISIBLE)
    Optional<InformeVersion> findVisibleByPublicId(
            @Param("publicId") String publicId,
            @Param("cuentaId") Long cuentaId,
            @Param("organizacionId") Long organizacionId,
            @Param("usuarioId") Long usuarioId,
            @Param("rol") String rol);

    /** Versiones visibles de un proyecto, por tipo y de la más nueva a la más antigua. */
    @Query("select v from InformeVersion v, Informe i where i.proyectoId = :proyectoId and " + VISIBLE
            + " order by i.tipo asc, v.numero desc")
    List<InformeVersion> findVisibleByProyecto(
            @Param("proyectoId") Long proyectoId,
            @Param("cuentaId") Long cuentaId,
            @Param("organizacionId") Long organizacionId,
            @Param("usuarioId") Long usuarioId,
            @Param("rol") String rol);

    /** Informes publicados de una organización, para la bandeja del CLIENTE_ADMIN (más recientes primero). */
    @Query("""
        select v from InformeVersion v, Informe i
        where i.id = v.informeId
          and i.cuentaId = v.cuentaId
          and v.cuentaId = :cuentaId
          and i.organizacionId = :organizacionId
          and v.estado = com.hexora.hexora.informes.EstadoVersion.PUBLICADO
        order by v.publicadoEn desc
        """)
    List<InformeVersion> findPublicadasDeOrganizacion(
            @Param("cuentaId") Long cuentaId, @Param("organizacionId") Long organizacionId);

    /** Versiones publicadas vigentes de todos los informes de un proyecto (trazabilidad del informe final). */
    @Query("""
        select v from InformeVersion v, Informe i
        where i.id = v.informeId
          and i.cuentaId = v.cuentaId
          and v.cuentaId = :cuentaId
          and i.proyectoId = :proyectoId
          and v.estado = com.hexora.hexora.informes.EstadoVersion.PUBLICADO
        order by i.tipo asc
        """)
    List<InformeVersion> findPublicadasDeProyecto(
            @Param("cuentaId") Long cuentaId, @Param("proyectoId") Long proyectoId);

    /** Búsqueda interna dentro de la cuenta, sin regla de rol: el servicio ya autorizó el acceso por otra vía. */
    Optional<InformeVersion> findByPublicIdAndCuentaId(String publicId, Long cuentaId);

    List<InformeVersion> findByInformeIdAndCuentaIdOrderByNumeroDesc(Long informeId, Long cuentaId);

    Optional<InformeVersion> findFirstByInformeIdAndCuentaIdOrderByNumeroDesc(Long informeId, Long cuentaId);

    Optional<InformeVersion> findByInformeIdAndCuentaIdAndEstado(
            Long informeId, Long cuentaId, EstadoVersion estado);

    @Query("select coalesce(max(v.numero), 0) from InformeVersion v where v.informeId = :informeId "
            + "and v.cuentaId = :cuentaId")
    int maxNumero(@Param("informeId") Long informeId, @Param("cuentaId") Long cuentaId);
}

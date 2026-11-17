package com.hexora.hexora.repository;

import com.hexora.hexora.informes.FormatoInforme;
import com.hexora.hexora.model.InformeArchivo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Archivos publicados. Misma regla de visibilidad que {@link InformeVersionRepository}: el archivo de una versión
 * DESPUBLICADA, o de otra cuenta u organización, no se devuelve al CLIENTE_ADMIN.
 */
public interface InformeArchivoRepository extends JpaRepository<InformeArchivo, Long> {

    /** Un archivo por su identificador público, solo si el usuario puede verlo; vacío equivale a 404. */
    @Query("""
        select f from InformeArchivo f, InformeVersion v, Informe i
        where f.publicId = :publicId
          and v.id = f.versionId
          and v.cuentaId = f.cuentaId
          and i.id = v.informeId
          and i.cuentaId = v.cuentaId
          and f.cuentaId = :cuentaId
          and (
              (:rol = 'AUDITOR' and exists (
                  select a.id.usuarioId from AsignacionAuditorProyecto a
                  where a.id.proyectoId = i.proyectoId
                    and a.id.cuentaId = i.cuentaId
                    and a.id.usuarioId = :usuarioId))
              or
              (:rol = 'CLIENTE_ADMIN'
                  and i.organizacionId = :organizacionId
                  and v.estado = com.hexora.hexora.informes.EstadoVersion.PUBLICADO
                  and exists (
                      select ac.id.usuarioId from AsignacionClienteProyecto ac
                      where ac.id.usuarioId = :usuarioId
                        and ac.id.proyectoId = i.proyectoId
                        and ac.id.cuentaId = i.cuentaId
                        and ac.retiradoEn is null))
          )
        """)
    Optional<InformeArchivo> findVisibleByPublicId(
            @Param("publicId") String publicId,
            @Param("cuentaId") Long cuentaId,
            @Param("organizacionId") Long organizacionId,
            @Param("usuarioId") Long usuarioId,
            @Param("rol") String rol);

    List<InformeArchivo> findByVersionIdAndCuentaIdOrderByFormatoAsc(Long versionId, Long cuentaId);

    Optional<InformeArchivo> findByVersionIdAndFormatoAndCuentaId(
            Long versionId, FormatoInforme formato, Long cuentaId);
}

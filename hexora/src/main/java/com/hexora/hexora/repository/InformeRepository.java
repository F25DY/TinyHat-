package com.hexora.hexora.repository;

import com.hexora.hexora.model.Informe;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InformeRepository extends JpaRepository<Informe, Long> {

    Optional<Informe> findByPublicIdAndCuentaId(String publicId, Long cuentaId);

    @Query("""
        select informe from Informe informe
        where informe.publicId = :publicId
          and informe.cuentaId = :cuentaId
          and (
              (:rol = 'AUDITOR' and exists (
                  select proyecto from Auditoria proyecto
                  join AsignacionAuditorProyecto asignacion
                    on asignacion.id.proyectoId = proyecto.idAuditoria
                   and asignacion.id.cuentaId = proyecto.cuentaId
                  where proyecto.idAuditoria = informe.proyectoId
                    and proyecto.cuentaId = informe.cuentaId
                    and asignacion.id.usuarioId = :usuarioId
              ))
              or
              (:rol = 'CLIENTE_ADMIN'
                  and informe.organizacionId = :organizacionId
                  and informe.estado = 'PUBLICADO')
          )
        """)
    Optional<Informe> findVisibleByPublicId(
            @Param("publicId") String publicId,
            @Param("cuentaId") Long cuentaId,
            @Param("organizacionId") Long organizacionId,
            @Param("usuarioId") Long usuarioId,
            @Param("rol") String rol);

    List<Informe> findByCuentaIdAndProyectoIdOrderByCreadoEnDesc(Long cuentaId, Long proyectoId);

    List<Informe> findByCuentaIdAndProyectoIdAndEstadoOrderByPublicadoEnDesc(
            Long cuentaId,
            Long proyectoId,
            String estado);

    List<Informe> findByCuentaIdAndOrganizacionIdAndEstadoOrderByPublicadoEnDesc(
            Long cuentaId,
            Long organizacionId,
            String estado);
}

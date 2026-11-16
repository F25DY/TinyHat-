package com.hexora.hexora.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.Empresa;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

    long countByEmpresa(Empresa empresa);

    @Query("select a.publicId from Auditoria a where a.idAuditoria = :projectId and a.cuentaId = :cuentaId")
    Optional<String> findPublicIdByIdAndCuentaId(
            @Param("projectId") Long projectId,
            @Param("cuentaId") Long cuentaId);

    @Query("""
        select a from Auditoria a
        where a.publicId = :publicId
          and a.cuentaId = :cuentaId
          and (
              (:rol = 'CLIENTE_ADMIN' and a.empresa.id = :organizacionId)
              or
              (:rol = 'AUDITOR' and exists (
                  select asignacion from AsignacionAuditorProyecto asignacion
                  where asignacion.id.proyectoId = a.idAuditoria
                    and asignacion.id.cuentaId = a.cuentaId
                    and asignacion.id.usuarioId = :usuarioId
              ))
          )
        """)
    Optional<Auditoria> findVisibleByPublicId(
            @Param("publicId") String publicId,
            @Param("cuentaId") Long cuentaId,
            @Param("organizacionId") Long organizacionId,
            @Param("usuarioId") Long usuarioId,
            @Param("rol") String rol);

    @Query("""
        select a from Auditoria a
        where a.cuentaId = :cuentaId
          and (
              (:rol = 'CLIENTE_ADMIN' and a.empresa.id = :organizacionId)
              or
              (:rol = 'AUDITOR' and exists (
                  select asignacion from AsignacionAuditorProyecto asignacion
                  where asignacion.id.proyectoId = a.idAuditoria
                    and asignacion.id.cuentaId = a.cuentaId
                    and asignacion.id.usuarioId = :usuarioId
              ))
          )
        order by a.fechaInico desc
        """)
    List<Auditoria> findVisibleProjects(
            @Param("cuentaId") Long cuentaId,
            @Param("organizacionId") Long organizacionId,
            @Param("usuarioId") Long usuarioId,
            @Param("rol") String rol);

    @Query("""
        select a.idAuditoria from Auditoria a
        where a.cuentaId = :cuentaId
          and a.monitoreoContinuoHabilitado = true
        """)
    List<Long> findContinuousMonitoringEnabledProjectIds(@Param("cuentaId") Long cuentaId);
}

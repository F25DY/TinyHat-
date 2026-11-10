package com.hexora.hexora.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hexora.hexora.model.AreaAuditable;
import com.hexora.hexora.model.Auditoria;

public interface AreaAuditableRepository extends JpaRepository<AreaAuditable, Long> {

    List<AreaAuditable> findByAuditoria(Auditoria auditoria);

    List<AreaAuditable> findByAuditoriaAndCuentaId(Auditoria auditoria, Long cuentaId);

    @Query("""
        select area from AreaAuditable area
        join fetch area.auditoria proyecto
        where area.publicId = :areaPublicId
          and area.cuentaId = :cuentaId
          and (:proyectoPublicId is null or proyecto.publicId = :proyectoPublicId)
          and (
              (:rol = 'CLIENTE_ADMIN' and proyecto.empresa.id = :organizacionId)
              or
              (:rol = 'AUDITOR' and exists (
                  select asignacion from AsignacionAuditorProyecto asignacion
                  where asignacion.id.proyectoId = proyecto.idAuditoria
                    and asignacion.id.cuentaId = proyecto.cuentaId
                    and asignacion.id.usuarioId = :usuarioId
              ))
          )
        """)
    Optional<AreaAuditable> findVisibleArea(
            @Param("areaPublicId") String areaPublicId,
            @Param("proyectoPublicId") String proyectoPublicId,
            @Param("cuentaId") Long cuentaId,
            @Param("organizacionId") Long organizacionId,
            @Param("usuarioId") Long usuarioId,
            @Param("rol") String rol);

    @Query("""
        select area from AreaAuditable area
        join fetch area.auditoria proyecto
        where area.publicId = :areaPublicId
          and proyecto.publicId = :proyectoPublicId
          and area.cuentaId = :cuentaId
          and exists (
              select asignacion from AsignacionAuditorProyecto asignacion
              where asignacion.id.proyectoId = proyecto.idAuditoria
                and asignacion.id.cuentaId = proyecto.cuentaId
                and asignacion.id.usuarioId = :usuarioId
          )
        """)
    Optional<AreaAuditable> findAssignedArea(
            @Param("areaPublicId") String areaPublicId,
            @Param("proyectoPublicId") String proyectoPublicId,
            @Param("cuentaId") Long cuentaId,
            @Param("usuarioId") Long usuarioId);
}

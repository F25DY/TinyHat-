package com.hexora.hexora.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hexora.hexora.model.AreaAuditable;
import com.hexora.hexora.model.Vulnerabilidad;

public interface VulnerabilidadRepository extends JpaRepository<Vulnerabilidad, Long> {

    List<Vulnerabilidad> findByAreaAuditable(AreaAuditable areaAuditable);

    List<Vulnerabilidad> findByAreaAuditableAndCuentaId(AreaAuditable areaAuditable, Long cuentaId);

    @Query("""
        select vulnerabilidad from Vulnerabilidad vulnerabilidad
        join fetch vulnerabilidad.areaAuditable area
        join fetch area.auditoria proyecto
        where vulnerabilidad.publicId = :hallazgoPublicId
          and area.publicId = :areaPublicId
          and proyecto.publicId = :proyectoPublicId
          and vulnerabilidad.cuentaId = :cuentaId
          and exists (
              select asignacion from AsignacionAuditorProyecto asignacion
              where asignacion.id.proyectoId = proyecto.idAuditoria
                and asignacion.id.cuentaId = proyecto.cuentaId
                and asignacion.id.usuarioId = :usuarioId
          )
        """)
    Optional<Vulnerabilidad> findAssignedFinding(
            @Param("hallazgoPublicId") String hallazgoPublicId,
            @Param("areaPublicId") String areaPublicId,
            @Param("proyectoPublicId") String proyectoPublicId,
            @Param("cuentaId") Long cuentaId,
            @Param("usuarioId") Long usuarioId);
}

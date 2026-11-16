package com.hexora.hexora.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hexora.hexora.dto.ConteoHallazgos;
import com.hexora.hexora.model.AreaAuditable;
import com.hexora.hexora.model.Vulnerabilidad;

public interface VulnerabilidadRepository extends JpaRepository<Vulnerabilidad, Long> {

    List<Vulnerabilidad> findByAreaAuditable(AreaAuditable areaAuditable);

    List<Vulnerabilidad> findByAreaAuditableAndCuentaId(AreaAuditable areaAuditable, Long cuentaId);

    /** Cuenta hallazgos por nivel de riesgo y estado, solo en los proyectos indicados y de la cuenta indicada. */
    @Query("""
        select new com.hexora.hexora.dto.ConteoHallazgos(vulnerabilidad.nivelRiesgo, vulnerabilidad.estado, count(vulnerabilidad))
        from Vulnerabilidad vulnerabilidad
        where vulnerabilidad.cuentaId = :cuentaId
          and vulnerabilidad.areaAuditable.auditoria.idAuditoria in :proyectoIds
        group by vulnerabilidad.nivelRiesgo, vulnerabilidad.estado
        """)
    List<ConteoHallazgos> contarPorRiesgoYEstado(
            @Param("cuentaId") Long cuentaId,
            @Param("proyectoIds") Collection<Long> proyectoIds);

    /** Los hallazgos más recientes de los proyectos indicados (el más nuevo primero). */
    @Query("""
        select vulnerabilidad from Vulnerabilidad vulnerabilidad
        join fetch vulnerabilidad.areaAuditable area
        join fetch area.auditoria proyecto
        where vulnerabilidad.cuentaId = :cuentaId
          and proyecto.idAuditoria in :proyectoIds
        order by vulnerabilidad.fechaDeteccion desc, vulnerabilidad.id desc
        """)
    List<Vulnerabilidad> recientes(
            @Param("cuentaId") Long cuentaId,
            @Param("proyectoIds") Collection<Long> proyectoIds,
            Pageable pageable);

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

package com.hexora.hexora.repository;

import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.AsignacionAuditorProyectoId;
import com.hexora.hexora.model.AsignacionClienteProyecto;
import com.hexora.hexora.model.Usuario;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Accesos de los administradores de la MiPyme a sus proyectos. "Activo" significa que el acceso no fue retirado y que el
 * proyecto no está "Completado".
 */
public interface AsignacionClienteProyectoRepository
        extends JpaRepository<AsignacionClienteProyecto, AsignacionAuditorProyectoId> {

    String ACTIVA = """
        a.id.usuarioId = :usuarioId
        and a.id.cuentaId = :cuentaId
        and a.retiradoEn is null
        and p.idAuditoria = a.id.proyectoId
        and p.cuentaId = a.id.cuentaId
        and p.estado <> 'Completado'
        """;

    /** ¿Tiene alguna fila (vigente o retirada)? Sin filas es una cuenta anterior a este control de acceso. */
    @Query("select count(a) from AsignacionClienteProyecto a where a.id.usuarioId = :usuarioId and a.id.cuentaId = :cuentaId")
    long contarTodas(@Param("usuarioId") Long usuarioId, @Param("cuentaId") Long cuentaId);

    @Query("select count(a) from AsignacionClienteProyecto a, Auditoria p where " + ACTIVA)
    long contarActivas(@Param("usuarioId") Long usuarioId, @Param("cuentaId") Long cuentaId);

    /** Proyectos activos a los que el usuario tiene acceso, para su panel. */
    @Query("select p from AsignacionClienteProyecto a, Auditoria p where " + ACTIVA + " order by p.nombre")
    List<Auditoria> proyectosActivos(@Param("usuarioId") Long usuarioId, @Param("cuentaId") Long cuentaId);

    /** Accesos de un proyecto (vigentes y retirados), los más recientes primero. */
    @Query("""
        select a from AsignacionClienteProyecto a
        where a.id.proyectoId = :proyectoId and a.id.cuentaId = :cuentaId
        order by a.creadoEn desc
        """)
    List<AsignacionClienteProyecto> delProyecto(@Param("proyectoId") Long proyectoId, @Param("cuentaId") Long cuentaId);

    @Query("select u from Usuario u where u.id in :ids and u.cuentaId = :cuentaId")
    List<Usuario> usuarios(@Param("ids") java.util.Collection<Long> ids, @Param("cuentaId") Long cuentaId);
}

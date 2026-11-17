package com.hexora.hexora.repository;

import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.model.Informe;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Series de informes. Toda consulta lleva la cuenta; la visibilidad por rol (auditor asignado, cliente de la
 * organización) se decide sobre las versiones y archivos, que son lo que el cliente puede ver.
 */
public interface InformeRepository extends JpaRepository<Informe, Long> {

    Optional<Informe> findByPublicIdAndCuentaId(String publicId, Long cuentaId);

    /** La serie de un tipo en un proyecto (hay una sola: clave única proyecto + tipo). */
    Optional<Informe> findByProyectoIdAndTipoAndCuentaId(Long proyectoId, TipoInforme tipo, Long cuentaId);

    List<Informe> findByProyectoIdAndCuentaIdOrderByTipoAsc(Long proyectoId, Long cuentaId);
}

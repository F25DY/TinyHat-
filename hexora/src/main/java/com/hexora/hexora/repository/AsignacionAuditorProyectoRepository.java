package com.hexora.hexora.repository;

import com.hexora.hexora.model.AsignacionAuditorProyecto;
import com.hexora.hexora.model.AsignacionAuditorProyectoId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsignacionAuditorProyectoRepository
        extends JpaRepository<AsignacionAuditorProyecto, AsignacionAuditorProyectoId> {
}

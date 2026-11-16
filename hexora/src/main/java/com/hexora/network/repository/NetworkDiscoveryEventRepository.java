package com.hexora.network.repository;

import com.hexora.network.model.NetworkDiscoveryEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NetworkDiscoveryEventRepository extends JpaRepository<NetworkDiscoveryEvent, Long> {

    List<NetworkDiscoveryEvent> findTop20ByOrderByCreatedAtDesc();

    List<NetworkDiscoveryEvent> findAllByDevice_ProyectoIdOrderByCreatedAtDesc(Long proyectoId, Pageable pageable);

    List<NetworkDiscoveryEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
        select event.id from NetworkDiscoveryEvent event
        where event.cuentaId = :cuentaId and event.createdAt < :cutoff
        order by event.createdAt, event.id
        """)
    List<Long> findIdsCreatedBefore(
        @Param("cutoff") LocalDateTime cutoff,
        @Param("cuentaId") Long cuentaId,
        Pageable pageable);

    @Modifying
    @Query("delete from NetworkDiscoveryEvent event where event.cuentaId = :cuentaId")
    int deleteAllForAccount(@Param("cuentaId") Long cuentaId);

    @Modifying
    @Query("""
        delete from NetworkDiscoveryEvent event
        where event.device.id in (
            select device.id from NetworkDetectedDevice device where device.proyectoId = :proyectoId
        )
        """)
    int deleteAllForProject(@Param("proyectoId") Long proyectoId);
}

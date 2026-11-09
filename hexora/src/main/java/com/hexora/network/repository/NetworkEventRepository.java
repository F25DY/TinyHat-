package com.hexora.network.repository;

import com.hexora.network.model.NetworkEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NetworkEventRepository extends JpaRepository<NetworkEvent, Long> {

    List<NetworkEvent> findTop20ByOrderByCreatedAtDesc();

    @Modifying
    @Query("delete from NetworkEvent event where event.host.id = :hostId")
    int deleteAllForHost(@Param("hostId") Long hostId);

    @Modifying
    @Query("delete from NetworkEvent event where event.scan.id in :scanIds")
    int deleteAllForScans(@Param("scanIds") List<Long> scanIds);

    @Modifying
    @Query("delete from NetworkEvent event where event.cuentaId = :cuentaId")
    int deleteAllForAccount(@Param("cuentaId") Long cuentaId);

    @Query("""
        select event.id from NetworkEvent event
        where event.cuentaId = :cuentaId and event.scan is null and event.createdAt < :cutoff
        order by event.createdAt, event.id
        """)
    List<Long> findUnlinkedIdsBefore(
        @Param("cutoff") LocalDateTime cutoff,
        @Param("cuentaId") Long cuentaId,
        Pageable pageable);
}

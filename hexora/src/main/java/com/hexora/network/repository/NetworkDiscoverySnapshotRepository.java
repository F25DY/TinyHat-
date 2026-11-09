package com.hexora.network.repository;

import com.hexora.network.model.NetworkDiscoverySnapshot;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NetworkDiscoverySnapshotRepository extends JpaRepository<NetworkDiscoverySnapshot, Long> {

    Optional<NetworkDiscoverySnapshot> findFirstByOrderByObservedAtDesc();

    List<NetworkDiscoverySnapshot> findAllByOrderByObservedAtDesc(Pageable pageable);

    @Query("""
        select snapshot.id from NetworkDiscoverySnapshot snapshot
        where snapshot.cuentaId = :cuentaId and snapshot.observedAt < :cutoff
        order by snapshot.observedAt, snapshot.id
        """)
    List<Long> findIdsObservedBefore(
        @Param("cutoff") LocalDateTime cutoff,
        @Param("cuentaId") Long cuentaId,
        Pageable pageable);

    @Modifying
    @Query("delete from NetworkDiscoverySnapshot snapshot where snapshot.cuentaId = :cuentaId")
    int deleteAllForAccount(@Param("cuentaId") Long cuentaId);
}

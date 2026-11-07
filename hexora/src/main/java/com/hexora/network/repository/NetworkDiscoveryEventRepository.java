package com.hexora.network.repository;

import com.hexora.network.model.NetworkDiscoveryEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NetworkDiscoveryEventRepository extends JpaRepository<NetworkDiscoveryEvent, Long> {

    List<NetworkDiscoveryEvent> findTop20ByOrderByCreatedAtDesc();

    @Query("select event.id from NetworkDiscoveryEvent event where event.createdAt < :cutoff order by event.createdAt, event.id")
    List<Long> findIdsCreatedBefore(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);
}

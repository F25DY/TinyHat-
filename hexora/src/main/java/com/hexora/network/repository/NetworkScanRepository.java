package com.hexora.network.repository;

import com.hexora.network.model.NetworkScan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NetworkScanRepository extends JpaRepository<NetworkScan, Long> {

    Slice<NetworkScan> findAllByOrderByStartedAtDesc(Pageable pageable);

    Slice<NetworkScan> findAllByHost_IdOrderByStartedAtDesc(Long hostId, Pageable pageable);

    Optional<NetworkScan> findFirstByHost_IdOrderByStartedAtDesc(Long hostId);

        @Query("""
                select scan from NetworkScan scan
                where scan.host.id in :hostIds
                    and not exists (
                            select newer.id from NetworkScan newer
                            where newer.host.id = scan.host.id
                                and (newer.startedAt > scan.startedAt
                                    or (newer.startedAt = scan.startedAt and newer.id > scan.id))
                    )
                """)
        List<NetworkScan> findLatestByHostIds(@Param("hostIds") List<Long> hostIds);

        @Query("select scan.id from NetworkScan scan where scan.startedAt < :cutoff order by scan.startedAt, scan.id")
        List<Long> findIdsStartedBefore(@Param("cutoff") java.time.LocalDateTime cutoff, Pageable pageable);

    @Modifying
    @Query("delete from NetworkScan scan where scan.host.id = :hostId")
    int deleteAllForHost(@Param("hostId") Long hostId);
}

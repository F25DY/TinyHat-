package com.hexora.network.repository;

import com.hexora.network.model.PortScanResult;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortScanResultRepository extends JpaRepository<PortScanResult, Long> {

    Optional<PortScanResult> findTopByHost_IdAndPortOrderByDetectedAtDesc(Long hostId, int port);

        @Query("""
                select result from PortScanResult result
                where result.host.id = :hostId
                    and result.port in :ports
                    and not exists (
                            select newer.id from PortScanResult newer
                            where newer.host.id = result.host.id
                                and newer.port = result.port
                                and (newer.detectedAt > result.detectedAt
                                    or (newer.detectedAt = result.detectedAt and newer.id > result.id))
                    )
                """)
        List<PortScanResult> findLatestByHostAndPorts(
                @Param("hostId") Long hostId,
                @Param("ports") List<Integer> ports
        );

    @Modifying
    @Query("delete from PortScanResult result where result.host.id = :hostId")
    int deleteAllForHost(@Param("hostId") Long hostId);

    @Modifying
    @Query("delete from PortScanResult result where result.host.proyectoId = :projectId")
    int deleteAllForProject(@Param("projectId") Long projectId);

    @Modifying
    @Query("delete from PortScanResult result where result.scan.id in :scanIds")
    int deleteAllForScans(@Param("scanIds") List<Long> scanIds);

    @Modifying
    @Query("delete from PortScanResult result where result.cuentaId = :cuentaId")
    int deleteAllForAccount(@Param("cuentaId") Long cuentaId);
}

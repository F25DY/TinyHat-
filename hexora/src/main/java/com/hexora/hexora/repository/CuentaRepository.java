package com.hexora.hexora.repository;

import com.hexora.hexora.model.Cuenta;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    Optional<Cuenta> findByPublicId(String publicId);

    @Query("select cuenta.id from Cuenta cuenta where cuenta.estado = 'ACTIVA' order by cuenta.id")
    List<Long> findActiveAccountIds();

    @Modifying
    @Query(value = "UPDATE cuenta SET sesiones_revocadas_en = :revokedAt WHERE id = :cuentaId", nativeQuery = true)
    int markSessionsRevoked(
        @Param("cuentaId") Long cuentaId,
        @Param("revokedAt") LocalDateTime revokedAt
    );

    @Query(value = "SELECT sesiones_revocadas_en FROM cuenta WHERE id = :cuentaId", nativeQuery = true)
    Optional<LocalDateTime> findSessionsRevokedAtById(@Param("cuentaId") Long cuentaId);
}

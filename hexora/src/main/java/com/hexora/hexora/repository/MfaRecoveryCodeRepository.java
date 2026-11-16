package com.hexora.hexora.repository;

import com.hexora.hexora.model.MfaRecoveryCode;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, Long> {

    List<MfaRecoveryCode> findByUsuarioIdAndUsedAtIsNull(Long usuarioId);

    @Modifying
    @Query("""
        update MfaRecoveryCode code
           set code.usedAt = :usedAt
         where code.id = :id and code.usedAt is null
        """)
    int markUsed(@Param("id") Long id, @Param("usedAt") LocalDateTime usedAt);

    void deleteByUsuarioId(Long usuarioId);
}

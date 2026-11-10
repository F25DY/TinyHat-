package com.hexora.hexora.repository;

import com.hexora.hexora.model.MfaCredential;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MfaCredentialRepository extends JpaRepository<MfaCredential, Long> {

    Optional<MfaCredential> findByUsuarioIdAndActivoTrue(Long usuarioId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update MfaCredential credential
           set credential.lastStepUsed = :step, credential.activo = true
         where credential.usuarioId = :userId
           and credential.activo = false
           and (credential.lastStepUsed is null or credential.lastStepUsed < :step)
        """)
    int activateAtStep(@Param("userId") Long userId, @Param("step") long step);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update MfaCredential credential
           set credential.lastStepUsed = :step
         where credential.usuarioId = :userId
           and credential.activo = true
           and (credential.lastStepUsed is null or credential.lastStepUsed < :step)
        """)
    int consumeActiveStep(@Param("userId") Long userId, @Param("step") long step);

    void deleteByUsuarioId(Long usuarioId);
}

package com.hexora.hexora.repository;

import com.hexora.hexora.model.TokenSeguridad;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenSeguridadRepository extends JpaRepository<TokenSeguridad, Long> {

    Optional<TokenSeguridad> findByHashToken(String hashToken);

    Optional<TokenSeguridad> findByHashTokenAndTipo(String hashToken, String tipo);

    List<TokenSeguridad> findByUsuarioIdAndUsadoEnIsNull(Long usuarioId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE TokenSeguridad t SET t.usadoEn = :usedAt WHERE t.hashToken = :hashToken AND t.usadoEn IS NULL AND t.expiraEn > :usedAt")
    int markTokenAsUsed(@Param("hashToken") String hashToken, @Param("usedAt") LocalDateTime usedAt);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE TokenSeguridad t SET t.usadoEn = :invalidatedAt WHERE t.usuarioId = :usuarioId AND t.usadoEn IS NULL")
    int invalidatePendingTokensForUser(@Param("usuarioId") Long usuarioId, @Param("invalidatedAt") LocalDateTime invalidatedAt);
}


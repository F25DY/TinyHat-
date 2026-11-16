package com.hexora.hexora.security;

import com.hexora.hexora.model.TokenSeguridad;
import com.hexora.hexora.model.Usuario;
import com.hexora.hexora.repository.TokenSeguridadRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityTokenService {

    public static final String TIPO_VERIFICACION = "VERIFICACION";
    public static final String TIPO_INVITACION = "INVITACION";
    public static final String TIPO_RECUPERACION = "RECUPERACION";

    public static final Duration CADUCIDAD_VERIFICACION = Duration.ofHours(24);
    public static final Duration CADUCIDAD_INVITACION = Duration.ofHours(72);
    public static final Duration CADUCIDAD_RECUPERACION = Duration.ofMinutes(30);

    private final TokenSeguridadRepository tokenRepository;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecurityTokenService(TokenSeguridadRepository tokenRepository, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.clock = clock;
    }

    public record GeneratedToken(String rawToken, TokenSeguridad tokenEntity) {}

    @Transactional
    public GeneratedToken createVerificationToken(Usuario usuario) {
        return createToken(
            TIPO_VERIFICACION,
            usuario.getId(),
            usuario.getEmailNormalizado(),
            null,
            "AUDITOR",
            usuario.getCuentaId(),
            CADUCIDAD_VERIFICACION
        );
    }

    @Transactional
    public GeneratedToken createInvitationToken(Usuario usuario, Long organizacionId, Long cuentaId) {
        return createToken(
            TIPO_INVITACION,
            usuario.getId(),
            usuario.getEmailNormalizado(),
            organizacionId,
            "CLIENTE_ADMIN",
            cuentaId,
            CADUCIDAD_INVITACION
        );
    }

    @Transactional
    public GeneratedToken createPasswordResetToken(Usuario usuario) {
        return createToken(
            TIPO_RECUPERACION,
            usuario.getId(),
            usuario.getEmailNormalizado(),
            usuario.getOrganizacionId(),
            usuario.getRol(),
            usuario.getCuentaId(),
            CADUCIDAD_RECUPERACION
        );
    }

    private GeneratedToken createToken(
        String tipo,
        Long usuarioId,
        String email,
        Long organizacionId,
        String rolDestino,
        Long cuentaId,
        Duration duration
    ) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String hashToken = hashToken(rawToken);

        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        LocalDateTime expiraEn = now.plus(duration);

        TokenSeguridad tokenEntity = new TokenSeguridad(
            tipo,
            hashToken,
            usuarioId,
            email,
            organizacionId,
            rolDestino,
            cuentaId,
            expiraEn
        );
        tokenEntity.setCreadoEn(now);
        TokenSeguridad saved = tokenRepository.save(tokenEntity);
        return new GeneratedToken(rawToken, saved);
    }

    @Transactional(readOnly = true)
    public Optional<TokenSeguridad> findValidToken(String rawToken, String expectedTipo) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String hash = hashToken(rawToken);
        LocalDateTime now = LocalDateTime.now(clock);
        return tokenRepository.findByHashTokenAndTipo(hash, expectedTipo)
            .filter(t -> t.getUsadoEn() == null && t.getExpiraEn().isAfter(now));
    }

    @Transactional
    public Optional<TokenSeguridad> consumeToken(String rawToken, String expectedTipo) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String hash = hashToken(rawToken);
        LocalDateTime now = LocalDateTime.now(clock);

        int rowsUpdated = tokenRepository.markTokenAsUsed(hash, expectedTipo, now);
        if (rowsUpdated != 1) {
            return Optional.empty();
        }

        return tokenRepository.findByHashTokenAndTipo(hash, expectedTipo);
    }

    @Transactional
    public int invalidatePendingTokensForUser(Long usuarioId) {
        if (usuarioId == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return tokenRepository.invalidatePendingTokensForUser(usuarioId, now);
    }

    public static String hashToken(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken cannot be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}

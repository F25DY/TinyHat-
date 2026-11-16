package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexora.hexora.repository.TokenSeguridadRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SecurityTokenServiceTest {

    @Test
    void doesNotConsumeTokenWhenRequestedAsAnotherType() {
        TokenSeguridadRepository repository = mock(TokenSeguridadRepository.class);
        SecurityTokenService service = new SecurityTokenService(
            repository,
            Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC)
        );
        String rawToken = "raw-token";
        String hash = SecurityTokenService.hashToken(rawToken);
        when(repository.markTokenAsUsed(
            hash,
            SecurityTokenService.TIPO_RECUPERACION,
            java.time.LocalDateTime.of(2026, 11, 9, 12, 0)
        )).thenReturn(0);

        Optional<?> consumed = service.consumeToken(rawToken, SecurityTokenService.TIPO_RECUPERACION);

        assertThat(consumed).isEmpty();
        verify(repository).markTokenAsUsed(
            hash,
            SecurityTokenService.TIPO_RECUPERACION,
            java.time.LocalDateTime.of(2026, 11, 9, 12, 0)
        );
    }
}

package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexora.hexora.model.MfaCredential;
import com.hexora.hexora.repository.MfaCredentialRepository;
import com.hexora.hexora.repository.MfaRecoveryCodeRepository;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class MfaServiceEncryptionTest {

    @Test
    void changingTheEncryptionKeyPreventsReadingTheStoredTotpSecret() {
        MfaCredentialRepository credentials = mock(MfaCredentialRepository.class);
        MfaRecoveryCodeRepository recoveryCodes = mock(MfaRecoveryCodeRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        TinyHatPrincipal principal = mock(TinyHatPrincipal.class);
        when(principal.getUserId()).thenReturn(1L);
        when(principal.getUsername()).thenReturn("auditor@example.test");
        when(credentials.findByUsuarioIdAndActivoTrue(1L)).thenReturn(Optional.empty());

        MfaService originalService = service(
            credentials,
            recoveryCodes,
            passwordEncoder,
            new byte[32]
        );
        originalService.beginEnrollment(principal);

        ArgumentCaptor<MfaCredential> savedCredential = ArgumentCaptor.forClass(MfaCredential.class);
        verify(credentials).save(savedCredential.capture());
        when(credentials.findByUsuarioIdAndActivoTrue(1L)).thenReturn(Optional.of(savedCredential.getValue()));

        byte[] differentKey = new byte[32];
        differentKey[0] = 1;
        MfaService serviceWithWrongKey = service(
            credentials,
            recoveryCodes,
            passwordEncoder,
            differentKey
        );
        assertThatThrownBy(() -> serviceWithWrongKey.verifyLoginCode(1L, "123456"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to read MFA credential");
    }

    private MfaService service(
        MfaCredentialRepository credentials,
        MfaRecoveryCodeRepository recoveryCodes,
        PasswordEncoder passwordEncoder,
        byte[] key
    ) {
        TinyHatSecurityProperties properties = new TinyHatSecurityProperties();
        properties.getMfa().setKey(Base64.getEncoder().encodeToString(key));
        return new MfaService(
            credentials,
            recoveryCodes,
            passwordEncoder,
            new TotpService(Clock.systemUTC()),
            properties,
            Clock.systemUTC()
        );
    }
}

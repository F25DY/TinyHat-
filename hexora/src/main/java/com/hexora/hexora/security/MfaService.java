package com.hexora.hexora.security;

import com.hexora.hexora.model.MfaCredential;
import com.hexora.hexora.model.MfaRecoveryCode;
import com.hexora.hexora.repository.MfaCredentialRepository;
import com.hexora.hexora.repository.MfaRecoveryCodeRepository;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MfaService {

    private static final int NONCE_LENGTH = 12;
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final char[] RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final MfaCredentialRepository credentialRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final TinyHatSecurityProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec encryptionKey;

    public MfaService(
        MfaCredentialRepository credentialRepository,
        MfaRecoveryCodeRepository recoveryCodeRepository,
        PasswordEncoder passwordEncoder,
        TotpService totpService,
        TinyHatSecurityProperties properties,
        Clock clock
    ) {
        this.credentialRepository = credentialRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
        this.properties = properties;
        this.clock = clock;
        if (!MfaKeyValidator.isValid(properties.getMfa().getKey())) {
            throw new IllegalStateException("tinyhat.security.mfa.key must be a Base64-encoded 256-bit key");
        }
        this.encryptionKey = new SecretKeySpec(
            Base64.getDecoder().decode(properties.getMfa().getKey()), "AES"
        );
    }

    @Transactional(readOnly = true)
    public boolean isActive(long userId) {
        return credentialRepository.findByUsuarioIdAndActivoTrue(userId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isPending(long userId) {
        return credentialRepository.findById(userId)
            .map(credential -> !credential.isActivo())
            .orElse(false);
    }

    @Transactional
    public Enrollment beginEnrollment(TinyHatPrincipal principal) {
        if (isActive(principal.getUserId())) {
            throw new IllegalStateException("MFA is already enrolled");
        }
        credentialRepository.deleteByUsuarioId(principal.getUserId());
        byte[] secretBytes = new byte[20];
        secureRandom.nextBytes(secretBytes);
        String secret = totpService.encodeBase32(secretBytes);
        EncryptedSecret encrypted = encrypt(secretBytes);
        credentialRepository.save(new MfaCredential(principal.getUserId(), encrypted.ciphertext(), encrypted.nonce()));
        return new Enrollment(totpService.provisioningUri(principal.getUsername(), secret), secret);
    }

    @Transactional
    public List<String> confirmEnrollment(long userId, String submittedCode) {
        MfaCredential credential = credentialRepository.findById(userId).orElse(null);
        if (credential == null || credential.isActivo()) {
            return List.of();
        }
        String secret = decrypt(credential);
        long step = totpService.matchingStep(secret, submittedCode);
        if (step < 0 || credentialRepository.activateAtStep(userId, step) != 1) {
            return List.of();
        }
        return createRecoveryCodes(userId);
    }

    @Transactional
    public boolean verifyLoginCode(long userId, String submittedCode) {
        MfaCredential credential = credentialRepository.findByUsuarioIdAndActivoTrue(userId).orElse(null);
        if (credential == null || submittedCode == null) {
            return false;
        }
        long step = totpService.matchingStep(decrypt(credential), submittedCode.trim());
        if (step >= 0) {
            return credentialRepository.consumeActiveStep(userId, step) == 1;
        }
        return consumeRecoveryCode(userId, submittedCode);
    }

    @Transactional
    public boolean verifyTotpForReauthentication(long userId, String submittedCode) {
        MfaCredential credential = credentialRepository.findByUsuarioIdAndActivoTrue(userId).orElse(null);
        if (credential == null) {
            return false;
        }
        long step = totpService.matchingStep(decrypt(credential), submittedCode == null ? "" : submittedCode.trim());
        return step >= 0 && credentialRepository.consumeActiveStep(userId, step) == 1;
    }

    @Transactional
    public void reset(long userId) {
        recoveryCodeRepository.deleteByUsuarioId(userId);
        credentialRepository.deleteByUsuarioId(userId);
    }

    private List<String> createRecoveryCodes(long userId) {
        List<String> plainCodes = new ArrayList<>(RECOVERY_CODE_COUNT);
        List<MfaRecoveryCode> storedCodes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int index = 0; index < RECOVERY_CODE_COUNT; index++) {
            byte[] randomCode = new byte[16];
            secureRandom.nextBytes(randomCode);
            StringBuilder rawCode = new StringBuilder(16);
            for (byte randomValue : randomCode) {
                rawCode.append(RECOVERY_ALPHABET[randomValue & 0x1f]);
            }
            String raw = rawCode.toString();
            String formatted = raw.substring(0, 4) + "-" + raw.substring(4, 8)
                + "-" + raw.substring(8, 12) + "-" + raw.substring(12);
            plainCodes.add(formatted);
            storedCodes.add(new MfaRecoveryCode(userId, passwordEncoder.encode(raw)));
        }
        recoveryCodeRepository.saveAll(storedCodes);
        return List.copyOf(plainCodes);
    }

    private boolean consumeRecoveryCode(long userId, String submittedCode) {
        String normalized = submittedCode.trim()
            .replace("-", "")
            .replace(" ", "")
            .toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("[A-HJ-NP-Z2-9]{16}")) {
            return false;
        }
        for (MfaRecoveryCode recoveryCode : recoveryCodeRepository.findByUsuarioIdAndUsedAtIsNull(userId)) {
            if (passwordEncoder.matches(normalized, recoveryCode.getHash())) {
                LocalDateTime usedAt = LocalDateTime.now(clock).withNano(0);
                return recoveryCodeRepository.markUsed(recoveryCode.getId(), usedAt) == 1;
            }
        }
        return false;
    }

    private EncryptedSecret encrypt(byte[] secret) {
        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
            return new EncryptedSecret(cipher.doFinal(secret), nonce);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to protect MFA credential", exception);
        }
    }

    private String decrypt(MfaCredential credential) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.DECRYPT_MODE,
                encryptionKey,
                new GCMParameterSpec(128, credential.getNonce())
            );
            return totpService.encodeBase32(cipher.doFinal(credential.getEncryptedSecret()));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to read MFA credential; verify TINYHAT_MFA_KEY", exception);
        }
    }

    public record Enrollment(String provisioningUri, String secret) {
    }

    private record EncryptedSecret(byte[] ciphertext, byte[] nonce) {
    }
}

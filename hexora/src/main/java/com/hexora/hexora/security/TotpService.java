package com.hexora.hexora.security;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class TotpService {

    private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final long STEP_SECONDS = 30;
    private final Clock clock;

    public TotpService(Clock clock) {
        this.clock = clock;
    }

    public String provisioningUri(String email, String secret) {
        String label = "Hexora:" + email;
        return "otpauth://totp/" + uriComponent(label)
            + "?secret=" + secret
            + "&issuer=Hexora&algorithm=SHA1&digits=6&period=30";
    }

    public long matchingStep(String secret, String submittedCode) {
        if (submittedCode == null || !submittedCode.matches("\\d{6}")) {
            return -1;
        }
        long currentStep = Math.floorDiv(clock.instant().getEpochSecond(), STEP_SECONDS);
        for (long step = currentStep + 1; step >= currentStep - 1; step--) {
            if (constantTimeEquals(codeForStep(secret, step), submittedCode)) {
                return step;
            }
        }
        return -1;
    }

    public String codeForStep(String secret, long step) {
        try {
            byte[] key = decodeBase32(secret);
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP cryptographic provider is unavailable", exception);
        }
    }

    public String encodeBase32(byte[] value) {
        StringBuilder result = new StringBuilder((value.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsInBuffer = 0;
        for (byte current : value) {
            buffer = (buffer << 8) | (current & 0xff);
            bitsInBuffer += 8;
            while (bitsInBuffer >= 5) {
                result.append(BASE32_ALPHABET[(buffer >> (bitsInBuffer - 5)) & 0x1f]);
                bitsInBuffer -= 5;
            }
        }
        if (bitsInBuffer > 0) {
            result.append(BASE32_ALPHABET[(buffer << (5 - bitsInBuffer)) & 0x1f]);
        }
        return result.toString();
    }

    private byte[] decodeBase32(String value) {
        String normalized = value.replace("=", "").toUpperCase(Locale.ROOT);
        byte[] output = new byte[normalized.length() * 5 / 8];
        int buffer = 0;
        int bitsInBuffer = 0;
        int offset = 0;
        for (char current : normalized.toCharArray()) {
            int index = current >= 'A' && current <= 'Z'
                ? current - 'A'
                : current >= '2' && current <= '7' ? current - '2' + 26 : -1;
            if (index < 0) {
                throw new IllegalArgumentException("Invalid TOTP secret encoding");
            }
            buffer = (buffer << 5) | index;
            bitsInBuffer += 5;
            if (bitsInBuffer >= 8) {
                output[offset++] = (byte) (buffer >> (bitsInBuffer - 8));
                bitsInBuffer -= 8;
            }
        }
        return output;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.US_ASCII),
            actual.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private String uriComponent(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

package com.hexora.hexora.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TotpServiceTest {

    @Test
    void followsRfc6238SixDigitSha1VectorAndAdjacentTimeWindow() {
        Clock clock = Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC);
        TotpService service = new TotpService(clock);
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

        assertEquals("287082", service.codeForStep(secret, 1));
        assertEquals(1, service.matchingStep(secret, "287082"));
        assertNotEquals("287082", service.codeForStep(secret, 2));
    }

    @Test
    void base32RoundTripsTwentyByteSecrets() {
        TotpService service = new TotpService(Clock.systemUTC());
        byte[] secret = new byte[20];
        for (int index = 0; index < secret.length; index++) {
            secret[index] = (byte) index;
        }

        assertEquals("AAAQEAYEAUDAOCAJBIFQYDIOB4IBCEQT", service.encodeBase32(secret));
    }
}

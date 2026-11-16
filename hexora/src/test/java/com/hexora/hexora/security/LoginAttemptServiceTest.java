package com.hexora.hexora.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    @Test
    void throttlesByNormalizedEmailAndIpAndResetsAfterTheWindow() {
        TinyHatSecurityProperties properties = new TinyHatSecurityProperties();
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginAttemptService service = new LoginAttemptService(properties, clock);

        for (int attempt = 0; attempt < 4; attempt++) {
            service.recordFailure(" Auditor@Example.com ", "192.0.2.10");
        }
        assertEquals(0, service.retryAfterSeconds("auditor@example.com", "192.0.2.10"));

        service.recordFailure("auditor@example.com", "192.0.2.10");
        assertTrue(service.retryAfterSeconds("AUDITOR@example.com", "192.0.2.10") > 0);
        assertTrue(service.retryAfterSeconds("other@example.com", "192.0.2.10") > 0);
        service.recordSuccess("auditor@example.com");
        assertEquals(0, service.retryAfterSeconds("auditor@example.com", "192.0.2.11"));
        assertTrue(service.retryAfterSeconds("other@example.com", "192.0.2.10") > 0);

        clock.advance(Duration.ofMinutes(31));
        assertEquals(0, service.retryAfterSeconds("auditor@example.com", "192.0.2.10"));
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;

        private MutableClock(Instant initial) {
            instant = new AtomicReference<>(initial);
        }

        private void advance(Duration duration) {
            instant.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}

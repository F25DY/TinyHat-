package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class EnumerationDetectorTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-11T12:00:10Z"));
    private final Clock clock = new Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    private final TinyHatSecurityProperties properties = new TinyHatSecurityProperties();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;
    private EnumerationDetector detector;

    @BeforeEach
    void setUp() {
        properties.getAudit().setEnumerationThreshold(3);
        detector = new EnumerationDetector(properties, clock);
        logger = (Logger) LoggerFactory.getLogger(EnumerationDetector.class);
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void warnsOnceWhenAUserExceedsTheThresholdWithinTheSameMinute() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(detector.recordScopedNotFound(7, 1)).isFalse();
        }
        assertThat(appender.list).isEmpty();

        assertThat(detector.recordScopedNotFound(7, 1)).isTrue();
        assertThat(detector.recordScopedNotFound(7, 1)).isFalse();

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
            .contains("Possible resource enumeration", "userId=7", "accountId=1", "threshold=3");
    }

    @Test
    void countersAreIndependentPerUserAndResetEveryMinute() {
        for (int attempt = 0; attempt < 3; attempt++) {
            detector.recordScopedNotFound(1, 1);
            detector.recordScopedNotFound(2, 1);
        }
        assertThat(appender.list).isEmpty();

        now.set(now.get().plus(Duration.ofMinutes(1)));
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(detector.recordScopedNotFound(1, 1)).isFalse();
        }
        assertThat(appender.list).isEmpty();
    }
}

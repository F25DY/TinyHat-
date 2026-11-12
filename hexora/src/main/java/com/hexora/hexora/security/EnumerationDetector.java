package com.hexora.hexora.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Detects resource enumeration: a user that triggers many scoped 404 responses in one minute is
 * probably walking identifiers that belong to other accounts or organizations.
 *
 * <p>Counters live in memory (documented limitation for v1). One WARN line is written per user and
 * minute, the first time the count exceeds {@code tinyhat.security.audit.enumeration-threshold}.
 */
@Component
public class EnumerationDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnumerationDetector.class);
    private static final long WINDOW_MILLIS = 60_000L;

    private final TinyHatSecurityProperties properties;
    private final Clock clock;
    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(2))
        .maximumSize(10_000)
        .build();

    public EnumerationDetector(TinyHatSecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Records one scoped 404 and returns true when the count crossed the threshold on this call. */
    public boolean recordScopedNotFound(long userId, long accountId) {
        int threshold = properties.getAudit().getEnumerationThreshold();
        String key = userId + ":" + (clock.millis() / WINDOW_MILLIS);
        int count = counters.get(key, ignored -> new AtomicInteger()).incrementAndGet();
        if (count == threshold + 1) {
            LOGGER.warn(
                "Possible resource enumeration: userId={} accountId={} scopedNotFound={} threshold={} windowSeconds=60",
                userId, accountId, count, threshold
            );
            return true;
        }
        return false;
    }
}

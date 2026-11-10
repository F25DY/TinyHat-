package com.hexora.hexora.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
        .maximumSize(100_000)
        .expireAfterAccess(Duration.ofHours(2))
        .build();

    public long tryConsume(String key, TinyHatSecurityProperties.Bucket limits) {
        Bucket bucket = buckets.get(key, ignored -> createBucket(limits));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        return probe.isConsumed() ? 0 : Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
    }

    private Bucket createBucket(TinyHatSecurityProperties.Bucket limits) {
        Bandwidth bandwidth = Bandwidth.classic(
            limits.getCapacity(),
            Refill.intervally(limits.getRefillTokens(), Duration.ofSeconds(limits.getRefillSeconds()))
        );
        return Bucket.builder().addLimit(bandwidth).build();
    }
}

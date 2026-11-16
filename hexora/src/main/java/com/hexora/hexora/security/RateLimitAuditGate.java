package com.hexora.hexora.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RateLimitAuditGate {

    private final Cache<String, Boolean> recentRejections = Caffeine.newBuilder()
        .maximumSize(50_000)
        .expireAfterWrite(Duration.ofMinutes(1))
        .build();

    public boolean shouldAudit(String key) {
        return recentRejections.asMap().putIfAbsent(key, Boolean.TRUE) == null;
    }
}

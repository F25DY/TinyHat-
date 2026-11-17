package com.hexora.hexora.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class NvdRateLimitService {

    private final Bucket bucket;

    public NvdRateLimitService(TinyHatNvdProperties properties) {
        int requests = properties.getApiKey().isBlank()
            ? properties.getRequestsWithoutKey()
            : properties.getRequestsWithKey();
        this.bucket = Bucket.builder()
            .addLimit(Bandwidth.builder()
                .capacity(requests)
                .refillIntervally(requests, Duration.ofSeconds(properties.getWindowSeconds()))
                .build())
            .build();
    }

    public long tryConsume() {
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        return probe.isConsumed()
            ? 0
            : Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
    }
}

package com.hexora.hexora.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RateLimitServiceTest {

    @Test
    void rejectsRequestsAfterTheConfiguredBucketIsExhausted() {
        RateLimitService service = new RateLimitService();
        TinyHatSecurityProperties.Bucket limits = new TinyHatSecurityProperties.Bucket(1, 1, 60);

        assertEquals(0, service.tryConsume("test", limits));
        assertTrue(service.tryConsume("test", limits) > 0);
    }

    @Test
    void acceptsRequestsAgainAfterTheConfiguredRefillInterval() throws InterruptedException {
        RateLimitService service = new RateLimitService();
        TinyHatSecurityProperties.Bucket limits = new TinyHatSecurityProperties.Bucket(1, 1, 1);
        service.tryConsume("refill", limits);

        assertTrue(service.tryConsume("refill", limits) > 0);
        Thread.sleep(1_100);
        assertEquals(0, service.tryConsume("refill", limits));
    }
}

package com.hexora.hexora.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    private final Cache<String, AttemptState> emailAttempts = newCache();
    private final Cache<String, AttemptState> ipAttempts = newCache();
    private final TinyHatSecurityProperties properties;
    private final Clock clock;

    public LoginAttemptService(TinyHatSecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public long retryAfterSeconds(String email, String ip) {
        Instant now = Instant.now(clock);
        return Math.max(
            remaining(emailAttempts.getIfPresent(normalize(email)), now),
            remaining(ipAttempts.getIfPresent(ip), now)
        );
    }

    public void recordFailure(String email, String ip) {
        Instant now = Instant.now(clock);
        String normalizedEmail = normalize(email);
        emailAttempts.asMap().compute(normalizedEmail, (key, previous) -> increment(previous, now));
        ipAttempts.asMap().compute(ip, (key, previous) -> increment(previous, now));
    }

    public void recordSuccess(String email) {
        emailAttempts.invalidate(normalize(email));
    }

    private AttemptState increment(AttemptState previous, Instant now) {
        Duration resetWindow = Duration.ofMinutes(
            properties.getLogin().getThrottle().getResetWindowMinutes()
        );
        int failures = previous == null || Duration.between(previous.windowStarted(), now).compareTo(resetWindow) >= 0
            ? 1
            : previous.failures() + 1;
        List<Integer> schedule = properties.getLogin().getThrottle().getScheduleSeconds();
        int delayIndex = Math.min(failures - 1, schedule.size() - 1);
        Instant blockedUntil = schedule.get(delayIndex) == 0 ? Instant.EPOCH : now.plusSeconds(schedule.get(delayIndex));
        Instant windowStarted = failures == 1 ? now : previous.windowStarted();
        return new AttemptState(failures, windowStarted, blockedUntil);
    }

    private long remaining(AttemptState state, Instant now) {
        if (state == null || !state.blockedUntil().isAfter(now)) {
            return 0;
        }
        return Math.max(1, Duration.between(now, state.blockedUntil()).toSeconds() + 1);
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static Cache<String, AttemptState> newCache() {
        return Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();
    }

    private record AttemptState(int failures, Instant windowStarted, Instant blockedUntil) {
    }
}

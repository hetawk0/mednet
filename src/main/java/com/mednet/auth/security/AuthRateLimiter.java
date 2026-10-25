package com.mednet.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mednet.auth.security.AuthRateLimitProperties.Limit;
import com.mednet.auth.security.RateLimitCounterStore.Counter;

/**
 * Fixed-window authentication abuse protection. Counter keys are hashed before reaching the backing
 * store, so email addresses and client addresses are neither stored nor logged.
 */
@Component
public class AuthRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimiter.class);

    private final AuthRateLimitProperties properties;
    private final Clock clock;
    private final RateLimitCounterStore counters;

    @Autowired
    public AuthRateLimiter(AuthRateLimitProperties properties, Clock clock, RateLimitCounterStore counters) {
        this.properties = properties;
        this.clock = clock;
        this.counters = counters;
    }

    /** In-memory constructor retained for isolated unit tests. */
    public AuthRateLimiter(AuthRateLimitProperties properties, Clock clock) {
        this(properties, clock, new InMemoryRateLimitCounterStore(properties.getMaxTrackedKeys()));
    }

    public AuthRateLimitProperties properties() {
        return properties;
    }

    /** Counts this request and rejects it when the window already holds {@code limit} requests. */
    public void consume(String bucket, String scope, String key, Limit limit) {
        if (!properties.isEnabled()) {
            return;
        }
        Instant now = clock.instant();
        Counter counter = counters.increment(bucket, scope, counterKey(key), now, limit);
        if (counter.count() > limit.getRequests()) {
            throw exceeded(counter, limit, now);
        }
        if (counter.count() == limit.getRequests()) {
            log.warn("Authentication rate limit reached for {} per {}", bucket, scope);
        }
    }

    /** Rejects the request when earlier {@link #record} calls have filled the window; counts nothing. */
    public void check(String bucket, String scope, String key, Limit limit) {
        if (!properties.isEnabled()) {
            return;
        }
        Instant now = clock.instant();
        Counter counter = counters.find(bucket, scope, counterKey(key), now, limit).orElse(null);
        if (counter != null && counter.count() >= limit.getRequests()) {
            throw exceeded(counter, limit, now);
        }
    }

    /** Counts an event (for example a failed sign-in) for a later {@link #check}. */
    public void record(String bucket, String scope, String key, Limit limit) {
        if (!properties.isEnabled()) {
            return;
        }
        Instant now = clock.instant();
        Counter counter = counters.increment(bucket, scope, counterKey(key), now, limit);
        if (counter.count() == limit.getRequests()) {
            log.warn("Authentication rate limit reached for {} per {}", bucket, scope);
        }
    }

    public void reset(String bucket, String scope, String key) {
        counters.reset(bucket, scope, counterKey(key));
    }

    int trackedKeys() {
        return Math.toIntExact(counters.trackedCounters());
    }

    /** A stable, non-reversible key for an email address. */
    public static String accountKey(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String counterKey(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((key == null ? "" : key).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static RateLimitExceededException exceeded(Counter counter, Limit limit, Instant now) {
        long seconds = Duration.between(now, counter.startedAt().plus(limit.getWindow())).toSeconds() + 1;
        return new RateLimitExceededException(Math.max(1, seconds));
    }
}

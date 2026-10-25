package com.mednet.auth.security;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.util.Assert;

import com.mednet.auth.security.AuthRateLimitProperties.Limit;
import com.mednet.auth.security.RateLimitCounterStore.Counter;

final class InMemoryRateLimitCounterStore implements RateLimitCounterStore {

    private final int maxKeys;
    private final Map<String, Entry> counters;

    InMemoryRateLimitCounterStore(int maxKeys) {
        this.maxKeys = Math.max(1, maxKeys);
        this.counters = new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > InMemoryRateLimitCounterStore.this.maxKeys;
            }
        };
    }

    @Override
    public synchronized Counter increment(String bucket, String scope, String keyHash, Instant now, Limit limit) {
        String key = key(bucket, scope, keyHash);
        long seconds = windowSeconds(limit);
        Entry entry = counters.get(key);
        if (entry == null || entry.expired(now, seconds)) {
            entry = new Entry(now, seconds, 1);
        } else {
            entry = new Entry(entry.startedAt(), seconds, Math.min(entry.count() + 1, (long) limit.getRequests() + 1));
        }
        counters.put(key, entry);
        return entry.counter();
    }

    @Override
    public synchronized Optional<Counter> find(String bucket, String scope, String keyHash, Instant now, Limit limit) {
        Entry entry = counters.get(key(bucket, scope, keyHash));
        if (entry == null || entry.expired(now, windowSeconds(limit))) {
            return Optional.empty();
        }
        return Optional.of(entry.counter());
    }

    @Override
    public synchronized void reset(String bucket, String scope, String keyHash) {
        counters.remove(key(bucket, scope, keyHash));
    }

    @Override
    public synchronized void purgeExpired(Instant now) {
        counters.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    @Override
    public synchronized long trackedCounters() {
        return counters.size();
    }

    private static String key(String bucket, String scope, String keyHash) {
        Assert.hasText(bucket, "bucket must not be blank");
        Assert.hasText(scope, "scope must not be blank");
        Assert.hasText(keyHash, "keyHash must not be blank");
        return bucket + '|' + scope + '|' + keyHash;
    }

    private static long windowSeconds(Limit limit) {
        return Math.max(1, limit.getWindow().toSeconds());
    }

    private record Entry(Instant startedAt, long windowSeconds, long count) {

        private Instant expiresAt() {
            return startedAt.plusSeconds(windowSeconds);
        }

        private boolean expired(Instant now, long expectedWindowSeconds) {
            return windowSeconds != expectedWindowSeconds || !now.isBefore(expiresAt());
        }

        private Counter counter() {
            return new Counter(startedAt, count);
        }
    }
}

package com.mednet.auth.security;

import java.time.Instant;
import java.util.Optional;

import com.mednet.auth.security.AuthRateLimitProperties.Limit;

public interface RateLimitCounterStore {

    Counter increment(String bucket, String scope, String keyHash, Instant now, Limit limit);

    Optional<Counter> find(String bucket, String scope, String keyHash, Instant now, Limit limit);

    void reset(String bucket, String scope, String keyHash);

    void purgeExpired(Instant now);

    long trackedCounters();

    record Counter(Instant startedAt, long count) {
    }
}

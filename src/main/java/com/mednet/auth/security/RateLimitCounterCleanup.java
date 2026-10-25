package com.mednet.auth.security;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${spring.datasource.url:}'.startsWith('jdbc:postgresql:')")
public class RateLimitCounterCleanup {

    private final RateLimitCounterStore counters;
    private final Clock clock;

    public RateLimitCounterCleanup(RateLimitCounterStore counters, Clock clock) {
        this.counters = counters;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${mednet.rate-limit.cleanup-interval-ms:300000}")
    public void purgeExpiredCounters() {
        counters.purgeExpired(clock.instant());
    }
}

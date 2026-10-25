package com.mednet.auth.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits for the authentication and email-delivery endpoints ({@code mednet.rate-limit.*}). PostgreSQL
 * deployments share counters across API instances; no-database and non-PostgreSQL contexts use bounded
 * in-memory counters for local development and tests.
 */
@ConfigurationProperties("mednet.rate-limit")
public class AuthRateLimitProperties {

    private boolean enabled = true;
    /** Upper bound on tracked counters; the least recently used are dropped beyond it. */
    private int maxTrackedKeys = 50_000;
    /**
     * Addresses or CIDR ranges of reverse proxies whose {@code X-Forwarded-For} is trusted. Empty means
     * the TCP peer address is the client, whatever headers the request carries.
     */
    private List<String> trustedProxies = new ArrayList<>();
    private final Limit signInFailuresPerAccount = new Limit(10, Duration.ofMinutes(15));
    private final Limit signInFailuresPerClient = new Limit(50, Duration.ofMinutes(15));
    private final Limit emailDeliveryPerAccount = new Limit(5, Duration.ofHours(1));
    private final Limit emailDeliveryPerClient = new Limit(20, Duration.ofHours(1));
    private final Limit codeChecksPerAccount = new Limit(10, Duration.ofHours(1));
    private final Limit codeChecksPerClient = new Limit(30, Duration.ofMinutes(15));
    private final Limit linkChecksPerClient = new Limit(30, Duration.ofMinutes(15));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxTrackedKeys() {
        return maxTrackedKeys;
    }

    public void setMaxTrackedKeys(int maxTrackedKeys) {
        this.maxTrackedKeys = maxTrackedKeys;
    }

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? new ArrayList<>() : trustedProxies;
    }

    public Limit getSignInFailuresPerAccount() {
        return signInFailuresPerAccount;
    }

    public Limit getSignInFailuresPerClient() {
        return signInFailuresPerClient;
    }

    public Limit getEmailDeliveryPerAccount() {
        return emailDeliveryPerAccount;
    }

    public Limit getEmailDeliveryPerClient() {
        return emailDeliveryPerClient;
    }

    public Limit getCodeChecksPerAccount() {
        return codeChecksPerAccount;
    }

    public Limit getCodeChecksPerClient() {
        return codeChecksPerClient;
    }

    public Limit getLinkChecksPerClient() {
        return linkChecksPerClient;
    }

    /** At most {@code requests} in a fixed window of {@code window}, starting at the first request. */
    public static class Limit {

        private int requests;
        private Duration window;

        public Limit(int requests, Duration window) {
            this.requests = requests;
            this.window = window;
        }

        public int getRequests() {
            return requests;
        }

        public void setRequests(int requests) {
            this.requests = requests;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }
}

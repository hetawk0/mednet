package com.mednet.admin.api;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminOverviewController {

    private final HealthEndpoint healthEndpoint;

    public AdminOverviewController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/overview")
    public AdminOverview overview() {
        String status = healthEndpoint.health().getStatus().getCode();
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1_000;

        return new AdminOverview(
                status,
                Instant.now(),
                uptimeSeconds,
                List.of(
                        new AdminModule("providerReview", "NOT_IMPLEMENTED"),
                        new AdminModule("accountSupport", "NOT_IMPLEMENTED"),
                        new AdminModule("serviceRequests", "NOT_IMPLEMENTED"),
                        new AdminModule("auditTrail", "NOT_IMPLEMENTED")));
    }

    public record AdminOverview(
            String apiStatus,
            Instant checkedAt,
            long uptimeSeconds,
            List<AdminModule> modules) {
    }

    public record AdminModule(String key, String status) {
    }
}
package com.mednet.admin.api;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mednet.admin.app.AdminWorkflowService;
import com.mednet.admin.data.AdminWorkflowModels.AdminCounts;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminOverviewController {

    private final HealthEndpoint healthEndpoint;
    private final ObjectProvider<AdminWorkflowService> workflowService;

    public AdminOverviewController(
            HealthEndpoint healthEndpoint,
            ObjectProvider<AdminWorkflowService> workflowService) {
        this.healthEndpoint = healthEndpoint;
        this.workflowService = workflowService;
    }

    @GetMapping("/overview")
    public AdminOverview overview() {
        String status = healthEndpoint.health().getStatus().getCode();
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1_000;
        AdminWorkflowService workflows = workflowService.getIfAvailable();
        String moduleStatus = workflows == null ? "NOT_CONFIGURED" : "CONNECTED";

        return new AdminOverview(
                status,
                Instant.now(),
                uptimeSeconds,
                workflows == null ? null : workflows.counts(),
                List.of(
                        new AdminModule("providerReview", moduleStatus),
                        new AdminModule("accountSupport", moduleStatus),
                        new AdminModule("serviceRequests", moduleStatus),
                        new AdminModule("auditTrail", moduleStatus),
                        new AdminModule("messaging", moduleStatus),
                        new AdminModule("medication", moduleStatus),
                        new AdminModule("vitals", moduleStatus),
                        new AdminModule("notifications", moduleStatus),
                        new AdminModule("homeCare", moduleStatus),
                        new AdminModule("laboratory", moduleStatus),
                        new AdminModule("textConsultations", moduleStatus),
                        new AdminModule("virtualConsultation", "DISABLED")));
    }

    public record AdminOverview(
            String apiStatus,
            Instant checkedAt,
            long uptimeSeconds,
            AdminCounts counts,
            List<AdminModule> modules) {
    }

    public record AdminModule(String key, String status) {
    }
}
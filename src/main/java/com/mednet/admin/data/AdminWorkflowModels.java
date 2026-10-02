package com.mednet.admin.data;

import java.time.Instant;

public final class AdminWorkflowModels {

    private AdminWorkflowModels() {
    }

    public record ProviderApplication(
            String id,
            String displayName,
            String email,
            String specialty,
            String credentialReference,
            String status,
            Instant createdAt,
            Instant reviewedAt,
            String reviewedBy) {
    }

    public record PlatformAccount(
            String id,
            String email,
            String accountType,
            String status,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ServiceRequest(
            String id,
            String referenceId,
            String requestType,
            String requesterEmail,
            String status,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record AdminAuditEvent(
            String id,
            String actorEmail,
            String action,
            String resourceType,
            String resourceId,
            Instant createdAt) {
    }

    public record AdminCounts(
            long pendingProviders,
            long activeAccounts,
            long openRequests) {
    }
}
package com.mednet.provider.app;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mednet.admin.data.AdminAuditEventEntity;
import com.mednet.admin.data.AdminAuditEventRepository;
import com.mednet.notification.app.NotificationService;
import com.mednet.provider.data.ProviderApplicationEntity;
import com.mednet.provider.data.ProviderApplicationRepository;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class ProviderService {

    private static final Set<String> ACTIVE_APPLICATION_STATUSES = Set.of("PENDING", "APPROVED", "SUSPENDED");

    private final ProviderApplicationRepository applications;
    private final AdminAuditEventRepository auditEvents;
    private final NotificationService notifications;

    public ProviderService(
            ProviderApplicationRepository applications,
            AdminAuditEventRepository auditEvents,
            NotificationService notifications) {
        this.applications = applications;
        this.auditEvents = auditEvents;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public Page<ProviderDirectoryEntry> directory(int page, int size, String specialty, String search) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return applications.findApprovedDirectory(
                        normalizeFilter(specialty),
                        normalizeFilter(search),
                        PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "displayName")))
                .map(ProviderService::toDirectoryEntry);
    }

    @Transactional
    public ProviderApplication apply(
            String applicantEmail,
            String displayName,
            String specialty,
            String credentialReference) {
        String email = normalizeEmail(applicantEmail);
        for (String status : ACTIVE_APPLICATION_STATUSES) {
            if (applications.findFirstByEmailIgnoreCaseAndStatusOrderByReviewedAtDesc(email, status).isPresent()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "A provider application already exists for this account");
            }
        }
        ProviderApplicationEntity application = applications.save(new ProviderApplicationEntity(
                UUID.randomUUID().toString(),
                displayName.trim(),
                email,
                specialty.trim(),
                credentialReference.trim()));
        auditEvents.save(new AdminAuditEventEntity(
                UUID.randomUUID().toString(),
                email,
                "provider.application_submitted",
                "provider",
                application.getId()));
        notifications.createForAccountTypes(
                List.of("ADMIN", "SUPER_ADMIN"),
                "provider-application:" + application.getId() + ":pending",
                "PROVIDER_APPLICATION",
                "A provider application is awaiting review",
                "provider",
                application.getId());
        return toApplication(application);
    }

    @Transactional(readOnly = true)
    public ProviderApplication myApplication(String applicantEmail) {
        ProviderApplicationEntity application = applications
                .findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(normalizeEmail(applicantEmail))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider application not found"));
        return toApplication(application);
    }

    private static String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search filters may not exceed 120 characters");
        }
        return normalized;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ProviderApplication toApplication(ProviderApplicationEntity application) {
        return new ProviderApplication(
                application.getId(),
                application.getDisplayName(),
                application.getSpecialty(),
                application.getCredentialReference(),
                application.getStatus(),
                application.getCreatedAt(),
                application.getReviewedAt());
    }

    private static ProviderDirectoryEntry toDirectoryEntry(ProviderApplicationEntity provider) {
        return new ProviderDirectoryEntry(
                provider.getId(),
                provider.getDisplayName(),
                provider.getSpecialty(),
                provider.getCredentialReference());
    }

    public record ProviderApplication(
            String id,
            String displayName,
            String specialty,
            String credentialReference,
            String status,
            java.time.Instant createdAt,
            java.time.Instant reviewedAt) {
    }

    public record ProviderDirectoryEntry(
            String id,
            String displayName,
            String specialty,
            String credentialReference) {
    }
}

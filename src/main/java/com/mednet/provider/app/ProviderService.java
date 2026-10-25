package com.mednet.provider.app;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mednet.api.Pagination;
import com.mednet.admin.data.AdminAuditEventEntity;
import com.mednet.admin.data.AdminAuditEventRepository;
import com.mednet.patient.app.PatientCareAccess;
import com.mednet.patient.data.PatientProfileRepository;
import com.mednet.notification.app.NotificationService;
import com.mednet.provider.data.ProviderApplicationEntity;
import com.mednet.provider.data.ProviderApplicationRepository;
import com.mednet.provider.data.ProviderAvailabilitySlotRepository;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class ProviderService {

    private static final Set<String> ACTIVE_APPLICATION_STATUSES = Set.of("PENDING", "APPROVED", "SUSPENDED");

    private final ProviderApplicationRepository applications;
    private final ProviderAvailabilitySlotRepository slots;
    private final AdminAuditEventRepository auditEvents;
    private final NotificationService notifications;
    private final PatientCareAccess careAccess;
    private final PatientProfileRepository patientProfiles;

    public ProviderService(
            ProviderApplicationRepository applications,
            ProviderAvailabilitySlotRepository slots,
            AdminAuditEventRepository auditEvents,
            NotificationService notifications,
            PatientCareAccess careAccess,
            PatientProfileRepository patientProfiles) {
        this.applications = applications;
        this.slots = slots;
        this.auditEvents = auditEvents;
        this.notifications = notifications;
        this.careAccess = careAccess;
        this.patientProfiles = patientProfiles;
    }

    @Transactional(readOnly = true)
    public Page<ProviderPatientSummary> patients(String actorEmail, int page, int size) {
        var provider = careAccess.requireApprovedProviderByEmail(actorEmail);
        return patientProfiles.findProviderCarePatients(
                        provider.getId(),
                        PatientCareAccess.CARE_STATUSES,
                        Pagination.pageRequest(
                                page,
                                size,
                                Sort.by(Sort.Order.asc("fullName"), Sort.Order.asc("accountId"))))
                .map(entry -> new ProviderPatientSummary(entry.getPatientAccountId(), entry.getName()));
    }

    @Transactional(readOnly = true)
    public ProviderPatientSummary patient(String actorEmail, String patientAccountId) {
        var provider = careAccess.requireApprovedProviderByEmail(actorEmail);
        return patientProfiles.findProviderCarePatient(
                        patientAccountId, provider.getId(), PatientCareAccess.CARE_STATUSES)
                .map(entry -> new ProviderPatientSummary(entry.getPatientAccountId(), entry.getName()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Patient not found"));
    }

    @Transactional(readOnly = true)
    public Page<ProviderDirectoryEntry> directory(int page, int size, String specialty, String search) {
        Page<ProviderApplicationEntity> providers = applications.findApprovedDirectory(
                normalizeFilter(specialty),
                normalizeFilter(search),
                Pagination.pageRequest(
                        page,
                        size,
                        Sort.by(Sort.Order.asc("displayName"), Sort.Order.asc("id"))));
        // One grouped query for the page's next open times, instead of one availability call per provider.
        Map<String, Instant> nextOpen = providers.isEmpty()
                ? Map.of()
                : slots.findNextOpenSlots(providers.map(ProviderApplicationEntity::getId).toList(), Instant.now())
                        .stream()
                        .collect(Collectors.toMap(
                                ProviderAvailabilitySlotRepository.NextOpenSlot::getProviderId,
                                ProviderAvailabilitySlotRepository.NextOpenSlot::getStartsAt));
        return providers.map(provider -> toDirectoryEntry(provider, nextOpen.get(provider.getId())));
    }

    /** One approved provider as the directory shows it; 404 for anyone else. */
    @Transactional(readOnly = true)
    public ProviderDirectoryEntry directoryEntry(String providerId) {
        ProviderApplicationEntity provider = applications.findByIdAndStatus(providerId, "APPROVED")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));
        Instant next = slots.findNextOpenSlots(List.of(provider.getId()), Instant.now()).stream()
                .findFirst()
                .map(ProviderAvailabilitySlotRepository.NextOpenSlot::getStartsAt)
                .orElse(null);
        return toDirectoryEntry(provider, next);
    }

    /** Approved providers per specialty across the whole directory. */
    @Transactional(readOnly = true)
    public List<SpecialtyCount> specialties() {
        return applications.countApprovedBySpecialty().stream()
                .map(row -> new SpecialtyCount(row.getSpecialty(), row.getProviders()))
                .toList();
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

    private static ProviderDirectoryEntry toDirectoryEntry(ProviderApplicationEntity provider, Instant nextAvailableAt) {
        return new ProviderDirectoryEntry(
                provider.getId(),
                provider.getDisplayName(),
                provider.getSpecialty(),
                provider.getCredentialReference(),
                nextAvailableAt);
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

    /** {@code nextAvailableAt}: the earliest open future slot, or null when none (Phase 5D, additive). */
    public record ProviderDirectoryEntry(
            String id,
            String displayName,
            String specialty,
            String credentialReference,
            Instant nextAvailableAt) {
    }

    public record SpecialtyCount(String specialty, long providers) {
    }

    public record ProviderPatientSummary(String patientAccountId, String name) {
    }
}

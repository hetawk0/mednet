package com.mednet.admin.app;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mednet.admin.data.AdminAuditEventEntity;
import com.mednet.admin.data.AdminAuditEventRepository;
import com.mednet.admin.data.AdminWorkflowModels.AdminAuditEvent;
import com.mednet.admin.data.AdminWorkflowModels.AdminCounts;
import com.mednet.admin.data.AdminWorkflowModels.PlatformAccount;
import com.mednet.admin.data.AdminWorkflowModels.ProviderApplication;
import com.mednet.admin.data.AdminWorkflowModels.ServiceRequest;
import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.admin.data.ServiceRequestEntity;
import com.mednet.admin.data.ServiceRequestRepository;
import com.mednet.provider.data.ProviderApplicationEntity;
import com.mednet.provider.data.ProviderApplicationRepository;
import com.mednet.record.data.ClinicalRecordRepository;
import com.mednet.record.data.PatientProviderRecordConsentRepository;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class AdminWorkflowService {

    private static final Set<String> PROVIDER_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED", "SUSPENDED");
    private static final Set<String> ACCOUNT_TYPES = Set.of("PATIENT", "PROVIDER", "ADMIN", "SUPER_ADMIN");
    private static final Set<String> CREATABLE_ACCOUNT_TYPES = Set.of("PATIENT", "PROVIDER");
    private static final Set<String> ACCOUNT_STATUSES = Set.of("ACTIVE", "SUSPENDED");
    private static final Set<String> REQUEST_TYPES = Set.of("APPOINTMENT", "HOME_CARE", "LABORATORY");
    private static final Set<String> REQUEST_STATUSES = Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CANCELLED");

    private final ProviderApplicationRepository providers;
    private final PlatformAccountRepository accounts;
    private final ServiceRequestRepository requests;
    private final AdminAuditEventRepository auditEvents;
    private final ClinicalRecordRepository clinicalRecords;
    private final PatientProviderRecordConsentRepository recordConsents;

    public AdminWorkflowService(
            ProviderApplicationRepository providers,
            PlatformAccountRepository accounts,
            ServiceRequestRepository requests,
            AdminAuditEventRepository auditEvents,
            ClinicalRecordRepository clinicalRecords,
            PatientProviderRecordConsentRepository recordConsents) {
        this.providers = providers;
        this.accounts = accounts;
        this.requests = requests;
        this.auditEvents = auditEvents;
        this.clinicalRecords = clinicalRecords;
        this.recordConsents = recordConsents;
    }

    @Transactional(readOnly = true)
    public List<ProviderApplication> providers(String status) {
        String filter = optionalStatus(status, PROVIDER_STATUSES);
        List<ProviderApplicationEntity> rows = filter == null
                ? providers.findTop100ByOrderByCreatedAtDesc()
                : providers.findTop100ByStatusOrderByCreatedAtDesc(filter);
        return rows.stream().map(AdminWorkflowService::toModel).toList();
    }

    @Transactional
    public ProviderApplication createProvider(
            String displayName, String email, String specialty, String credentialReference, String actor) {
        ProviderApplicationEntity entity = providers.save(new ProviderApplicationEntity(
                UUID.randomUUID().toString(),
                displayName.trim(),
                normalizeEmail(email),
                specialty.trim(),
                credentialReference.trim()));
        audit(actor, "provider.created", "provider", entity.getId());
        return toModel(entity);
    }

    @Transactional
    public ProviderApplication reviewProvider(String id, String status, String actor) {
        ProviderApplicationEntity entity = providers.findById(id).orElseThrow(() -> notFound("Provider application"));
        String nextStatus = requireStatus(status, PROVIDER_STATUSES);
        entity.review(nextStatus, actor);
        audit(actor, "provider." + nextStatus.toLowerCase(Locale.ROOT), "provider", id);
        return toModel(entity);
    }

    @Transactional(readOnly = true)
    public Page<PlatformAccount> accounts(int page, int size, String search, String status) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedSearch = search == null ? "" : search.trim();
        String normalizedStatus = status == null || status.isBlank() ? null : requireStatus(status, ACCOUNT_STATUSES);
        Page<PlatformAccountEntity> rows;
        if (normalizedStatus == null && normalizedSearch.isBlank()) {
            rows = accounts.findAll(pageable);
        } else if (normalizedStatus == null) {
            rows = accounts.findByEmailContainingIgnoreCase(normalizedSearch, pageable);
        } else if (normalizedSearch.isBlank()) {
            rows = accounts.findByStatus(normalizedStatus, pageable);
        } else {
            rows = accounts.findByEmailContainingIgnoreCaseAndStatus(normalizedSearch, normalizedStatus, pageable);
        }
        return rows.map(AdminWorkflowService::toModel);
    }

    @Transactional
    public PlatformAccount createAccount(String email, String accountType, String actor) {
        String type = requireStatus(accountType, CREATABLE_ACCOUNT_TYPES);
        try {
            PlatformAccountEntity entity = accounts.save(new PlatformAccountEntity(
                    UUID.randomUUID().toString(), normalizeEmail(email), type));
            audit(actor, "account.created", "account", entity.getId());
            return toModel(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
    }

    @Transactional
    public PlatformAccount changeAccountStatus(String id, String status, String actor) {
        PlatformAccountEntity entity = accounts.findById(id).orElseThrow(() -> notFound("Platform account"));
        String nextStatus = requireStatus(status, ACCOUNT_STATUSES);
        entity.changeStatus(nextStatus);
        audit(actor, "account." + nextStatus.toLowerCase(Locale.ROOT), "account", id);
        return toModel(entity);
    }

    @Transactional
    public PlatformAccount changeAccountType(String id, String accountType, String actor) {
        PlatformAccountEntity entity = accounts.findById(id).orElseThrow(() -> notFound("Platform account"));
        String nextType = requireStatus(accountType, ACCOUNT_TYPES);
        if (hasClinicalAccountData(entity)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Accounts linked to medical records or record consent cannot change account type");
        }
        entity.changeAccountType(nextType);
        audit(actor, "account.role_changed", "account", id);
        return toModel(entity);
    }

    @Transactional
    public void deleteAccount(String id, String actor) {
        PlatformAccountEntity entity = accounts.findById(id).orElseThrow(() -> notFound("Platform account"));
        if (hasClinicalAccountData(entity)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Accounts linked to medical records or record consent cannot be deleted");
        }
        accounts.delete(entity);
        audit(actor, "account.deleted", "account", id);
    }

    private boolean hasClinicalAccountData(PlatformAccountEntity account) {
        if (clinicalRecords.existsByPatientAccountIdOrAuthorAccountId(account.getId(), account.getId())
                || recordConsents.existsByPatientAccountId(account.getId())) {
            return true;
        }
        if ("PROVIDER".equals(account.getAccountType())) {
            return providers.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(account.getEmail())
                    .map(provider -> recordConsents.existsByProviderApplicationId(provider.getId()))
                    .orElse(false);
        }
        return false;
    }

    @Transactional(readOnly = true)
    public List<ServiceRequest> requests(String status) {
        String filter = optionalStatus(status, REQUEST_STATUSES);
        List<ServiceRequestEntity> rows = filter == null
                ? requests.findTop100ByOrderByCreatedAtDesc()
                : requests.findTop100ByStatusOrderByCreatedAtDesc(filter);
        return rows.stream().map(AdminWorkflowService::toModel).toList();
    }

    @Transactional
    public ServiceRequest createRequest(String referenceId, String requestType, String requesterEmail, String actor) {
        String type = requireStatus(requestType, REQUEST_TYPES);
        try {
            ServiceRequestEntity entity = requests.save(new ServiceRequestEntity(
                    UUID.randomUUID().toString(), referenceId.trim(), type, normalizeEmail(requesterEmail)));
            audit(actor, "request.created", "service_request", entity.getId());
            return toModel(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request reference already exists");
        }
    }

    @Transactional
    public ServiceRequest changeRequestStatus(String id, String status, String actor) {
        ServiceRequestEntity entity = requests.findById(id).orElseThrow(() -> notFound("Service request"));
        String nextStatus = requireStatus(status, REQUEST_STATUSES);
        entity.changeStatus(nextStatus);
        audit(actor, "request." + nextStatus.toLowerCase(Locale.ROOT), "service_request", id);
        return toModel(entity);
    }

    @Transactional(readOnly = true)
    public List<AdminAuditEvent> audit() {
        return auditEvents.findTop100ByOrderByCreatedAtDesc().stream().map(AdminWorkflowService::toModel).toList();
    }

    @Transactional(readOnly = true)
    public AdminCounts counts() {
        return new AdminCounts(
                providers.countByStatus("PENDING"),
                accounts.countByStatus("ACTIVE"),
                requests.countByStatusIn(List.of("OPEN", "IN_PROGRESS")));
    }

    private void audit(String actor, String action, String resourceType, String resourceId) {
        auditEvents.save(new AdminAuditEventEntity(
                UUID.randomUUID().toString(), actor, action, resourceType, resourceId));
    }

    private static String optionalStatus(String value, Set<String> allowed) {
        return value == null || value.isBlank() ? null : requireStatus(value, allowed);
    }

    private static String requireStatus(String value, Set<String> allowed) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported status or type");
        }
        return normalized;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException notFound(String resource) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " not found");
    }

    private static ProviderApplication toModel(ProviderApplicationEntity entity) {
        return new ProviderApplication(
                entity.getId(), entity.getDisplayName(), entity.getEmail(), entity.getSpecialty(),
                entity.getCredentialReference(), entity.getStatus(), entity.getCreatedAt(),
                entity.getReviewedAt(), entity.getReviewedBy());
    }

    private static PlatformAccount toModel(PlatformAccountEntity entity) {
        return new PlatformAccount(
                entity.getId(), entity.getEmail(), entity.getAccountType(), entity.getStatus(),
                entity.isEmailVerified(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private static ServiceRequest toModel(ServiceRequestEntity entity) {
        return new ServiceRequest(
                entity.getId(), entity.getReferenceId(), entity.getRequestType(), entity.getRequesterEmail(),
                entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private static AdminAuditEvent toModel(AdminAuditEventEntity entity) {
        return new AdminAuditEvent(
                entity.getId(), entity.getActorEmail(), entity.getAction(), entity.getResourceType(),
                entity.getResourceId(), entity.getCreatedAt());
    }
}
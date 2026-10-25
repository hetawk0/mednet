package com.mednet.laboratory.app;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mednet.admin.data.AdminAuditEventEntity;
import com.mednet.admin.data.AdminAuditEventRepository;
import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.laboratory.data.LaboratoryResultEntity;
import com.mednet.laboratory.data.LaboratoryResultRepository;
import com.mednet.notification.app.NotificationService;
import com.mednet.patient.app.PatientCareAccess;
import com.mednet.patient.data.PatientProfileEntity;
import com.mednet.patient.data.PatientProfileRepository;
import com.mednet.record.data.ClinicalRecordEntity;
import com.mednet.record.data.ClinicalRecordRepository;
import com.mednet.service.data.PatientServiceRequestEntity;
import com.mednet.service.data.PatientServiceRequestRepository;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class LaboratoryResultService {

    private final PatientCareAccess careAccess;
    private final PlatformAccountRepository accounts;
    private final PatientServiceRequestRepository requests;
    private final LaboratoryResultRepository results;
    private final ClinicalRecordRepository records;
    private final AdminAuditEventRepository auditEvents;
    private final NotificationService notifications;
    private final PatientProfileRepository profiles;

    public LaboratoryResultService(
            PatientCareAccess careAccess,
            PlatformAccountRepository accounts,
            PatientServiceRequestRepository requests,
            LaboratoryResultRepository results,
            ClinicalRecordRepository records,
            AdminAuditEventRepository auditEvents,
            NotificationService notifications,
            PatientProfileRepository profiles) {
        this.careAccess = careAccess;
        this.accounts = accounts;
        this.requests = requests;
        this.results = results;
        this.records = records;
        this.auditEvents = auditEvents;
        this.notifications = notifications;
        this.profiles = profiles;
    }

    @Transactional
    public LaboratoryResultDetails submit(String labEmail, String requestId, String summary) {
        PlatformAccountEntity staff = requireLaboratoryStaff(labEmail);
        PatientServiceRequestEntity request = requests.findByIdForUpdate(requestId)
                .filter(value -> "LABORATORY".equals(value.getRequestType())
                        && staff.getId().equals(value.getAssignedStaffAccountId()))
                .orElseThrow(LaboratoryResultService::notFound);
        if (!"IN_PROGRESS".equals(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Laboratory request is not in progress");
        }
        if (results.existsByRequestIdAndStatus(requestId, "PENDING_REVIEW")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A result is already awaiting provider review");
        }
        LaboratoryResultEntity result = results.save(new LaboratoryResultEntity(
                UUID.randomUUID().toString(),
                request.getId(),
                request.getPatientAccountId(),
                staff.getId(),
                summary.trim()));
        audit(labEmail, "laboratory_result.submitted", result.getId());
        return details(result);
    }

    /**
     * The calling laboratory's own submissions, newest first, limited to requests still assigned to it.
     * Read-only: review decisions stay with the doctor. No patient identifier is returned.
     */
    @Transactional
    public List<LaboratoryResultDetails> partnerResults(String labEmail) {
        PlatformAccountEntity staff = requireLaboratoryStaff(labEmail);
        Set<String> assignedRequestIds = requests
                .findByAssignedStaffAccountIdAndRequestTypeOrderByCreatedAtDesc(staff.getId(), "LABORATORY")
                .stream()
                .map(PatientServiceRequestEntity::getId)
                .collect(Collectors.toSet());
        List<LaboratoryResultDetails> submitted = results
                .findTop100ByAuthorAccountIdOrderByCreatedAtDesc(staff.getId())
                .stream()
                .filter(result -> assignedRequestIds.contains(result.getRequestId()))
                .map(LaboratoryResultService::details)
                .toList();
        audit(labEmail, "laboratory_result.partner_accessed", staff.getId());
        return submitted;
    }

    /**
     * Paged version of {@link #partnerResults}: the laboratory's own submissions on requests still assigned
     * to it, newest first, optionally one review status. Nothing is cut off.
     */
    @Transactional
    public Page<LaboratoryResultDetails> partnerResultsPage(String labEmail, String status, int page, int size) {
        PlatformAccountEntity staff = requireLaboratoryStaff(labEmail);
        String filter = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!filter.isEmpty() && !List.of("PENDING_REVIEW", "RETURNED", "RELEASED").contains(filter)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported laboratory result status");
        }
        Page<LaboratoryResultDetails> submitted = results
                .findPartnerResults(staff.getId(), filter, pageRequest(page, size))
                .map(LaboratoryResultService::details);
        audit(labEmail, "laboratory_result.partner_accessed", staff.getId());
        return submitted;
    }

    /**
     * The provider's laboratory review queue in one call (Phase 5D): every care-relationship patient who
     * either has results awaiting this provider's review (only with granted record consent) or has not
     * granted record access. Counts and dates only, no result text; results are opened per patient through
     * {@link #pendingForProvider}, which checks access again.
     */
    @Transactional
    public ReviewQueuePage reviewQueue(String providerEmail, int page, int size) {
        var provider = careAccess.requireApprovedProviderByEmail(providerEmail);
        PageRequest pageable = pageRequest(page, size);
        Page<LaboratoryResultRepository.ReviewQueueRow> rows = results.findReviewQueue(
                provider.getId(), PatientCareAccess.CARE_STATUSES, pageable);
        List<String> patientIds = rows.getContent().stream()
                .map(LaboratoryResultRepository.ReviewQueueRow::getPatientAccountId)
                .toList();
        Map<String, String> names = profiles.findByAccountIdIn(patientIds)
                .stream()
                .collect(Collectors.toMap(PatientProfileEntity::getAccountId, PatientProfileEntity::getFullName, (a, b) -> a));
        Page<ReviewQueueEntry> named = rows.map(row -> new ReviewQueueEntry(
                row.getPatientAccountId(),
                names.get(row.getPatientAccountId()),
                Boolean.TRUE.equals(row.getRecordAccess()),
                row.getPendingResults(),
                row.getOldestPendingAtEpochMillis() == null
                        ? null
                        : Instant.ofEpochMilli(row.getOldestPendingAtEpochMillis())));
        audit(providerEmail, "laboratory_result.review_queue_accessed", provider.getId());
        long totalPending = results.countPendingForProvider(
                provider.getId(), PatientCareAccess.CARE_STATUSES);
        long withoutAccess = results.countPatientsWithoutRecordAccess(
                provider.getId(), PatientCareAccess.CARE_STATUSES);
        return new ReviewQueuePage(named, totalPending, withoutAccess);
    }

    @Transactional
    public List<LaboratoryResultDetails> pendingForProvider(String providerEmail, String patientAccountId) {
        PlatformAccountEntity patient = careAccess.requirePatientById(patientAccountId);
        var provider = careAccess.requireApprovedProviderByEmail(providerEmail);
        careAccess.requireRecordAccess(patient.getId(), provider.getId());
        List<LaboratoryResultDetails> pending = results
                .findTop100ByPatientAccountIdAndStatusOrderByCreatedAtAsc(patient.getId(), "PENDING_REVIEW")
                .stream()
                .map(LaboratoryResultService::details)
                .toList();
        audit(providerEmail, "laboratory_result.review_queue_accessed", patient.getId());
        return pending;
    }

    @Transactional
    public Page<LaboratoryResultDetails> pendingForProviderPage(
            String providerEmail, String patientAccountId, int page, int size) {
        PlatformAccountEntity patient = careAccess.requirePatientById(patientAccountId);
        var provider = careAccess.requireApprovedProviderByEmail(providerEmail);
        careAccess.requireRecordAccess(patient.getId(), provider.getId());
        Page<LaboratoryResultDetails> pending = results
                .findByPatientAccountIdAndStatusOrderByCreatedAtAscIdAsc(
                        patient.getId(), "PENDING_REVIEW", pageRequest(page, size))
                .map(LaboratoryResultService::details);
        audit(providerEmail, "laboratory_result.review_queue_accessed", patient.getId());
        return pending;
    }

    @Transactional
    public LaboratoryResultDetails review(String providerEmail, String resultId, String action) {
        LaboratoryResultEntity result = results.findByIdForUpdate(resultId)
                .orElseThrow(LaboratoryResultService::notFound);
        PlatformAccountEntity patient = careAccess.requirePatientById(result.getPatientAccountId());
        var provider = careAccess.requireApprovedProviderByEmail(providerEmail);
        careAccess.requireRecordAccess(patient.getId(), provider.getId());
        if (!"PENDING_REVIEW".equals(result.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending laboratory results can be reviewed");
        }

        String normalizedAction = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        if (!"RELEASE".equals(normalizedAction) && !"RETURN".equals(normalizedAction)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported laboratory result review action");
        }
        PatientServiceRequestEntity request = requests.findByIdForUpdate(result.getRequestId())
                .filter(value -> patient.getId().equals(value.getPatientAccountId())
                        && "LABORATORY".equals(value.getRequestType())
                        && "IN_PROGRESS".equals(value.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "The laboratory request is no longer available for review"));
        PlatformAccountEntity reviewer = careAccess.requireActiveProviderAccount(providerEmail);
        String nextStatus = "RELEASE".equals(normalizedAction) ? "RELEASED" : "RETURNED";
        result.review(nextStatus, reviewer.getId());

        if ("RELEASED".equals(nextStatus)) {
            records.save(new ClinicalRecordEntity(
                    UUID.randomUUID().toString(),
                    patient.getId(),
                    reviewer.getId(),
                    provider.getId(),
                    "LAB_RESULT",
                    "Laboratory result",
                    null,
                    "Test: " + request.getRequestedService() + "\n\nResult:\n" + result.getSummary(),
                    Instant.now(),
                    null));
            request.changeStatus("RESOLVED");
            notifications.create(
                    patient.getId(),
                    "lab-result:" + result.getId() + ":released",
                    "LAB_RESULT",
                    "A laboratory result is available in your record",
                    "laboratory_result",
                    result.getId());
        }
        audit(providerEmail, "laboratory_result." + nextStatus.toLowerCase(Locale.ROOT), result.getId());
        return details(result);
    }

    @Transactional
    public List<LaboratoryResultDetails> patientResults(String patientEmail) {
        PlatformAccountEntity patient = careAccess.requirePatient(patientEmail);
        List<LaboratoryResultDetails> released = results
                .findTop100ByPatientAccountIdAndStatusOrderByCreatedAtDesc(patient.getId(), "RELEASED")
                .stream()
                .map(LaboratoryResultService::details)
                .toList();
        audit(patientEmail, "laboratory_result.accessed", patient.getId());
        return released;
    }

    /** Paged version of {@link #patientResults}: released results only, newest first. */
    @Transactional
    public Page<LaboratoryResultDetails> patientResultsPage(String patientEmail, int page, int size) {
        PlatformAccountEntity patient = careAccess.requirePatient(patientEmail);
        Page<LaboratoryResultDetails> released = results
                .findByPatientAccountIdAndStatusOrderByCreatedAtDesc(patient.getId(), "RELEASED", pageRequest(page, size))
                .map(LaboratoryResultService::details);
        audit(patientEmail, "laboratory_result.accessed", patient.getId());
        return released;
    }

    @Transactional
    public LaboratoryResultDetails patientResult(String patientEmail, String resultId) {
        PlatformAccountEntity patient = careAccess.requirePatient(patientEmail);
        LaboratoryResultEntity result = results
                .findByIdAndPatientAccountIdAndStatus(resultId, patient.getId(), "RELEASED")
                .orElseThrow(LaboratoryResultService::notFound);
        audit(patientEmail, "laboratory_result.accessed", result.getId());
        return details(result);
    }

    private static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be 0 or more and size 1 to 100");
        }
        return PageRequest.of(page, size);
    }

    private PlatformAccountEntity requireLaboratoryStaff(String email) {
        return accounts.findFirstByEmailIgnoreCase(email)
                .filter(account -> "LABORATORY".equals(account.getAccountType())
                        && "ACTIVE".equals(account.getStatus())
                        && account.isEmailVerified())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Laboratory staff access is not allowed"));
    }

    private void audit(String actorEmail, String action, String resourceId) {
        auditEvents.save(new AdminAuditEventEntity(
                UUID.randomUUID().toString(),
                actorEmail.trim().toLowerCase(Locale.ROOT),
                action,
                "laboratory_result",
                resourceId));
    }

    private static LaboratoryResultDetails details(LaboratoryResultEntity result) {
        return new LaboratoryResultDetails(
                result.getId(),
                result.getRequestId(),
                result.getSummary(),
                result.getStatus(),
                result.getCreatedAt(),
                result.getReviewedAt());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Laboratory result not found");
    }

    /**
     * One patient in a provider's review queue. {@code recordAccess} false means a care relationship
     * without granted record consent: no counts are given and no result can be opened.
     */
    public record ReviewQueueEntry(
            String patientAccountId,
            String patientName,
            boolean recordAccess,
            long pendingResults,
            Instant oldestPendingAt) {
    }

    /** A page of review-queue patients plus totals across every page. */
    public record ReviewQueuePage(
            List<ReviewQueueEntry> content,
            long totalElements,
            int totalPages,
            int number,
            int size,
            long totalPendingResults,
            long patientsWithoutRecordAccess) {

        ReviewQueuePage(Page<ReviewQueueEntry> page, long totalPendingResults, long patientsWithoutRecordAccess) {
            this(page.getContent(), page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize(),
                    totalPendingResults, patientsWithoutRecordAccess);
        }
    }

    public record LaboratoryResultDetails(
            String id,
            String requestId,
            String summary,
            String status,
            Instant createdAt,
            Instant reviewedAt) {
    }
}

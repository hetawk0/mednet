package com.mednet.laboratory.app;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    public LaboratoryResultService(
            PatientCareAccess careAccess,
            PlatformAccountRepository accounts,
            PatientServiceRequestRepository requests,
            LaboratoryResultRepository results,
            ClinicalRecordRepository records,
            AdminAuditEventRepository auditEvents,
            NotificationService notifications) {
        this.careAccess = careAccess;
        this.accounts = accounts;
        this.requests = requests;
        this.results = results;
        this.records = records;
        this.auditEvents = auditEvents;
        this.notifications = notifications;
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

    @Transactional
    public LaboratoryResultDetails patientResult(String patientEmail, String resultId) {
        PlatformAccountEntity patient = careAccess.requirePatient(patientEmail);
        LaboratoryResultEntity result = results
                .findByIdAndPatientAccountIdAndStatus(resultId, patient.getId(), "RELEASED")
                .orElseThrow(LaboratoryResultService::notFound);
        audit(patientEmail, "laboratory_result.accessed", result.getId());
        return details(result);
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

    public record LaboratoryResultDetails(
            String id,
            String requestId,
            String summary,
            String status,
            Instant createdAt,
            Instant reviewedAt) {
    }
}

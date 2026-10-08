package com.mednet.service.app;

import java.util.List;
import java.util.Locale;
import java.util.Set;
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
import com.mednet.notification.app.NotificationService;
import com.mednet.patient.app.PatientCareAccess;
import com.mednet.service.data.PatientServiceRequestEntity;
import com.mednet.service.data.PatientServiceRequestRepository;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class PatientServiceRequestService {

    private static final Set<String> PATIENT_REQUEST_TYPES = Set.of("HOME_CARE", "LABORATORY");

    private final PatientCareAccess careAccess;
    private final PlatformAccountRepository accounts;
    private final PatientServiceRequestRepository requests;
    private final AdminAuditEventRepository auditEvents;
    private final NotificationService notifications;

    public PatientServiceRequestService(
            PatientCareAccess careAccess,
            PlatformAccountRepository accounts,
            PatientServiceRequestRepository requests,
            AdminAuditEventRepository auditEvents,
            NotificationService notifications) {
        this.careAccess = careAccess;
        this.accounts = accounts;
        this.requests = requests;
        this.auditEvents = auditEvents;
        this.notifications = notifications;
    }

    @Transactional
    public PatientServiceRequestDetails create(
            String email, String requestType, String requestedService, String locationDescription) {
        PlatformAccountEntity patient = careAccess.requirePatient(email);
        String type = normalizeType(requestType);
        String location = locationDescription == null || locationDescription.isBlank()
                ? null
                : locationDescription.trim();
        if ("HOME_CARE".equals(type) && location == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A location description is required");
        }
        PatientServiceRequestEntity saved = requests.save(new PatientServiceRequestEntity(
                UUID.randomUUID().toString(),
                patient.getId(),
                type,
                requestedService.trim(),
                location));
        audit(email, "service_request.created", saved.getId());
        notifications.create(
                patient.getId(),
                "service-request:" + saved.getId() + ":created",
                "SERVICE_REQUEST",
                "Your service request has been received",
                "service_request",
                saved.getId());
        return details(saved);
    }

    @Transactional
    public List<PatientServiceRequestDetails> listPatient(String email, String requestType) {
        PlatformAccountEntity patient = careAccess.requirePatient(email);
        String type = normalizeType(requestType);
        audit(email, "service_request.accessed", patient.getId());
        return requests.findByPatientAccountIdAndRequestTypeOrderByCreatedAtDesc(patient.getId(), type).stream()
                .map(PatientServiceRequestService::details)
                .toList();
    }

    @Transactional
    public PatientServiceRequestDetails cancel(String email, String requestType, String requestId) {
        PlatformAccountEntity patient = careAccess.requirePatient(email);
        String type = normalizeType(requestType);
        PatientServiceRequestEntity request = requests
                .findByIdAndPatientAccountIdAndRequestType(requestId, patient.getId(), type)
                .orElseThrow(PatientServiceRequestService::notFound);
        if (!"OPEN".equals(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an open service request can be cancelled");
        }
        request.changeStatus("CANCELLED");
        audit(email, "service_request.cancelled", requestId);
        return details(request);
    }

    @Transactional
    public List<AdminRequestSummary> listForAdministrator(String requestType, String actorEmail) {
        String type = normalizeType(requestType);
        audit(actorEmail, "service_request.queue_accessed", type);
        return requests.findTop100ByRequestTypeOrderByCreatedAtDesc(type).stream()
                .map(request -> new AdminRequestSummary(
                        request.getId(), request.getRequestType(), request.getStatus(), request.getCreatedAt()))
                .toList();
    }

    @Transactional
    public AdminRequestSummary updateStatus(String requestId, String status, String actorEmail) {
        PatientServiceRequestEntity request = requests.findById(requestId)
                .orElseThrow(PatientServiceRequestService::notFound);
        String next = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        boolean valid = ("OPEN".equals(request.getStatus())
                        && Set.of("IN_PROGRESS", "CANCELLED").contains(next))
                || ("IN_PROGRESS".equals(request.getStatus())
                        && "CANCELLED".equals(next));
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid service request status transition");
        }
        request.changeStatus(next);
        audit(actorEmail, "service_request." + next.toLowerCase(Locale.ROOT), requestId);
        notifications.create(
                request.getPatientAccountId(),
                "service-request:" + requestId + ":" + next,
                "SERVICE_REQUEST",
                "Your service request status was updated",
                "service_request",
                requestId);
        return new AdminRequestSummary(
                request.getId(), request.getRequestType(), request.getStatus(), request.getCreatedAt());
    }

    @Transactional
    public PartnerAssignment assignStaff(String requestId, String staffAccountId, String actorEmail) {
        PatientServiceRequestEntity request = requests.findById(requestId)
                .orElseThrow(PatientServiceRequestService::notFound);
        PlatformAccountEntity staff = accounts.findById(staffAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff account not found"));
        String requiredRole = "HOME_CARE".equals(request.getRequestType()) ? "HOME_CARE" : "LABORATORY";
        if (!requiredRole.equals(staff.getAccountType())
                || !"ACTIVE".equals(staff.getStatus())
                || !staff.isEmailVerified()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Staff account must be active, verified, and match the request type");
        }
        if (!"OPEN".equals(request.getStatus()) || request.getAssignedStaffAccountId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only unassigned open requests can be assigned");
        }
        request.assignTo(staff.getId());
        audit(actorEmail, "service_request.assigned", requestId);
        notifications.create(
                staff.getId(),
                "service-request:" + requestId + ":assigned",
                "SERVICE_REQUEST",
                "A service request has been assigned to you",
                "service_request",
                requestId);
        return new PartnerAssignment(request.getId(), staff.getId(), request.getRequestType(), request.getStatus());
    }

    @Transactional
    public List<PartnerRequestDetails> listAssigned(String email, String requestType) {
        String type = normalizeType(requestType);
        PlatformAccountEntity staff = requirePartnerAccount(email, type);
        List<PatientServiceRequestEntity> assigned = requests
                .findByAssignedStaffAccountIdAndRequestTypeOrderByCreatedAtDesc(staff.getId(), type);
        audit(email, "service_request.partner_queue_accessed", staff.getId());
        return assigned.stream()
                .map(PatientServiceRequestService::partnerDetails)
                .toList();
    }

    @Transactional
    public PartnerRequestDetails updateAssignedStatus(String email, String requestId, String status) {
        PlatformAccountEntity staff = requirePartnerAccountForRequest(email, requestId);
        PatientServiceRequestEntity request = requests.findByIdAndAssignedStaffAccountIdAndRequestType(
                        requestId, staff.getId(), staff.getAccountType())
                .orElseThrow(PatientServiceRequestService::notFound);
        String next = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        boolean starting = "OPEN".equals(request.getStatus()) && "IN_PROGRESS".equals(next);
        boolean completingHomeCare = "HOME_CARE".equals(staff.getAccountType())
                && "IN_PROGRESS".equals(request.getStatus())
                && "RESOLVED".equals(next);
        if (!starting && !completingHomeCare) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot move " + request.getRequestType() + " request from " + request.getStatus() + " to " + next);
        }
        request.changeStatus(next);
        audit(email, "service_request.partner_" + next.toLowerCase(Locale.ROOT), requestId);
        notifications.create(
                request.getPatientAccountId(),
                "service-request:" + requestId + ":" + next,
                "SERVICE_REQUEST",
                "Your service request status was updated",
                "service_request",
                requestId);
        return partnerDetails(request);
    }

    private PlatformAccountEntity requirePartnerAccount(String email, String requestType) {
        String type = normalizeType(requestType);
        String role = "HOME_CARE".equals(type) ? "HOME_CARE" : "LABORATORY";
        PlatformAccountEntity account = accounts.findFirstByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Partner account access is not allowed"));
        if (!role.equals(account.getAccountType()) || !"ACTIVE".equals(account.getStatus()) || !account.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Partner account access is not allowed");
        }
        return account;
    }

    private PlatformAccountEntity requirePartnerAccountForRequest(String email, String requestId) {
        PatientServiceRequestEntity request = requests.findById(requestId)
                .orElseThrow(PatientServiceRequestService::notFound);
        return requirePartnerAccount(email, request.getRequestType());
    }

    private static String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!PATIENT_REQUEST_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported service request type");
        }
        return normalized;
    }

    private void audit(String actorEmail, String action, String requestId) {
        auditEvents.save(new AdminAuditEventEntity(
                UUID.randomUUID().toString(),
                actorEmail.trim().toLowerCase(Locale.ROOT),
                action,
                "service_request",
                requestId));
    }

    private static PatientServiceRequestDetails details(PatientServiceRequestEntity request) {
        return new PatientServiceRequestDetails(
                request.getId(),
                request.getRequestType(),
                request.getRequestedService(),
                request.getLocationDescription(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }

    private static PartnerRequestDetails partnerDetails(PatientServiceRequestEntity request) {
        return new PartnerRequestDetails(
                request.getId(),
                request.getRequestType(),
                request.getRequestedService(),
                request.getLocationDescription(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Service request not found");
    }

    public record PatientServiceRequestDetails(
            String id,
            String requestType,
            String requestedService,
            String locationDescription,
            String status,
            java.time.Instant createdAt,
            java.time.Instant updatedAt) {
    }

    public record AdminRequestSummary(
            String id,
            String requestType,
            String status,
            java.time.Instant createdAt) {
    }

    public record PartnerAssignment(
            String requestId,
            String assignedStaffAccountId,
            String requestType,
            String status) {
    }

    public record PartnerRequestDetails(
            String id,
            String requestType,
            String requestedService,
            String locationDescription,
            String status,
            java.time.Instant createdAt,
            java.time.Instant updatedAt) {
    }
}

package com.mednet.service.app;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mednet.admin.data.AdminAuditEventEntity;
import com.mednet.admin.data.AdminAuditEventRepository;
import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.laboratory.data.LaboratoryResultRepository;
import com.mednet.notification.app.NotificationService;
import com.mednet.patient.app.PatientCareAccess;
import com.mednet.service.data.PatientServiceRequestEntity;
import com.mednet.service.data.PatientServiceRequestRepository;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class PatientServiceRequestService {

    private static final Set<String> PATIENT_REQUEST_TYPES = Set.of("HOME_CARE", "LABORATORY");
    private static final Set<String> SERVICE_REQUEST_STATUSES = Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CANCELLED");
    private static final int MAX_SEARCH_LENGTH = 120;
    private static final Sort REQUEST_ORDER = Sort.by(Sort.Direction.DESC, "createdAt")
            .and(Sort.by(Sort.Direction.DESC, "id"));

    private final PatientCareAccess careAccess;
    private final PlatformAccountRepository accounts;
    private final PatientServiceRequestRepository requests;
    private final AdminAuditEventRepository auditEvents;
    private final NotificationService notifications;
    private final LaboratoryResultRepository labResults;

    public PatientServiceRequestService(
            PatientCareAccess careAccess,
            PlatformAccountRepository accounts,
            PatientServiceRequestRepository requests,
            AdminAuditEventRepository auditEvents,
            NotificationService notifications,
            LaboratoryResultRepository labResults) {
        this.careAccess = careAccess;
        this.accounts = accounts;
        this.requests = requests;
        this.auditEvents = auditEvents;
        this.notifications = notifications;
        this.labResults = labResults;
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
        notifications.createForAccountTypes(
                List.of("ADMIN", "SUPER_ADMIN"),
                "service-request:" + saved.getId() + ":new",
                "SERVICE_REQUEST",
                "A new service request is awaiting assignment",
                "service_request",
                saved.getId());
        return details(saved);
    }

    @Transactional
    public List<PatientServiceRequestDetails> listPatient(
            String email, String requestType, String status, String search) {
        PlatformAccountEntity patient = careAccess.requirePatient(email);
        String type = normalizeType(requestType);
        String normalizedStatus = normalizeStatus(status);
        String searchPattern = normalizeSearch(search);
        audit(email, "service_request.accessed", patient.getId());
        List<PatientServiceRequestEntity> history = normalizedStatus == null && searchPattern == null
                ? requests.findByPatientAccountIdAndRequestTypeOrderByCreatedAtDescIdDesc(patient.getId(), type)
                : requests.searchPatientHistory(patient.getId(), type, normalizedStatus, searchPattern, REQUEST_ORDER);
        return history.stream()
                .map(PatientServiceRequestService::details)
                .toList();
    }

    @Transactional
    public Page<PatientServiceRequestDetails> pagePatient(
            String email, String requestType, String status, String search, int page, int size) {
        PlatformAccountEntity patient = careAccess.requirePatient(email);
        String type = normalizeType(requestType);
        validatePage(page, size);
        String normalizedStatus = normalizeStatus(status);
        String searchPattern = normalizeSearch(search);
        audit(email, "service_request.accessed", patient.getId());
        PageRequest pageable = PageRequest.of(page, size, REQUEST_ORDER);
        List<PatientServiceRequestEntity> history = requests.searchPatientHistoryPage(
                        patient.getId(), type, normalizedStatus, searchPattern,
                        pageable);
        long total = requests.countPatientHistory(patient.getId(), type, normalizedStatus, searchPattern);
        return new PageImpl<>(
                history.stream().map(PatientServiceRequestService::details).toList(), pageable, total);
    }

    @Transactional
    public PatientServiceRequestDetails cancel(String email, String requestType, String requestId) {
        PlatformAccountEntity patient = careAccess.requirePatient(email);
        String type = normalizeType(requestType);
        PatientServiceRequestEntity request = requests.findByIdForUpdate(requestId)
                .filter(value -> patient.getId().equals(value.getPatientAccountId())
                        && type.equals(value.getRequestType()))
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
        List<PatientServiceRequestEntity> rows = requests.findTop100ByRequestTypeOrderByCreatedAtDesc(type);
        // One lookup for every assigned staff member; only their public IDs leave the service.
        Map<String, String> publicIds = accounts.findAllById(rows.stream()
                        .map(PatientServiceRequestEntity::getAssignedStaffAccountId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(PlatformAccountEntity::getId, PlatformAccountEntity::getPublicId));
        return rows.stream()
                .map(request -> adminSummary(request, publicIds.get(request.getAssignedStaffAccountId())))
                .toList();
    }

    /** Paged version of {@link #listForAdministrator}: newest first, complete (Phase 5D). */
    @Transactional
    public Page<AdminRequestSummary> pageForAdministrator(
            String requestType, List<String> statuses, String actorEmail, int page, int size) {
        String type = normalizeType(requestType);
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be 0 or more and size 1 to 100");
        }
        List<String> statusFilter = statuses == null ? List.of() : statuses.stream()
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        if (!List.of("OPEN", "IN_PROGRESS", "RESOLVED", "CANCELLED").containsAll(statusFilter)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported service request status");
        }
        audit(actorEmail, "service_request.queue_accessed", type);
        Page<PatientServiceRequestEntity> rows = statusFilter.isEmpty()
                ? requests.findByRequestTypeOrderByCreatedAtDesc(type, PageRequest.of(page, size))
                : requests.findByRequestTypeAndStatusInOrderByCreatedAtDesc(type, statusFilter, PageRequest.of(page, size));
        Map<String, String> publicIds = accounts.findAllById(rows.stream()
                        .map(PatientServiceRequestEntity::getAssignedStaffAccountId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(PlatformAccountEntity::getId, PlatformAccountEntity::getPublicId));
        return rows.map(request -> adminSummary(request, publicIds.get(request.getAssignedStaffAccountId())));
    }

    @Transactional
    public AdminRequestSummary updateStatus(String requestId, String status, String actorEmail) {
        PatientServiceRequestEntity request = requests.findByIdForUpdate(requestId)
                .orElseThrow(PatientServiceRequestService::notFound);
        String next = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        boolean valid = ("OPEN".equals(request.getStatus())
                        && ("CANCELLED".equals(next)
                                || ("IN_PROGRESS".equals(next) && request.getAssignedStaffAccountId() == null)))
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
        String assignedPublicId = request.getAssignedStaffAccountId() == null
                ? null
                : accounts.findById(request.getAssignedStaffAccountId()).map(PlatformAccountEntity::getPublicId).orElse(null);
        return adminSummary(request, assignedPublicId);
    }

    @Transactional
    public PartnerAssignment assignStaff(String requestId, String staffAccountId, String actorEmail) {
        PatientServiceRequestEntity request = requests.findByIdForUpdate(requestId)
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
    public PartnerRequestDetails scheduleHomeCare(String staffEmail, String requestId, Instant scheduledAt) {
        PlatformAccountEntity staff = requirePartnerAccount(staffEmail, "HOME_CARE");
        PatientServiceRequestEntity request = requests.findByIdForUpdate(requestId)
                .filter(value -> "HOME_CARE".equals(value.getRequestType())
                        && staff.getId().equals(value.getAssignedStaffAccountId()))
                .orElseThrow(PatientServiceRequestService::notFound);
        if (!"OPEN".equals(request.getStatus()) || request.getScheduledAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only unscheduled open home-care requests can be scheduled");
        }
        if (scheduledAt == null || !scheduledAt.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The home-care appointment time must be in the future");
        }
        request.scheduleAt(scheduledAt);
        audit(staffEmail, "service_request.home_care_scheduled", requestId);
        notifications.create(
                request.getPatientAccountId(),
                "service-request:" + requestId + ":scheduled",
                "SERVICE_REQUEST",
                "Your home-care request has been scheduled",
                "service_request",
                requestId);
        return partnerDetails(request);
    }

    @Transactional
    public List<PartnerRequestDetails> listAssigned(
            String email, String requestType, String status, String search) {
        String type = normalizeType(requestType);
        PlatformAccountEntity staff = requirePartnerAccount(email, type);
        String normalizedStatus = normalizeStatus(status);
        String searchPattern = normalizeSearch(search);
        List<PatientServiceRequestEntity> assigned = normalizedStatus == null && searchPattern == null
                ? requests.findByAssignedStaffAccountIdAndRequestTypeOrderByCreatedAtDescIdDesc(staff.getId(), type)
                : requests.searchAssignedRequests(staff.getId(), type, normalizedStatus, searchPattern, REQUEST_ORDER);
        audit(email, "service_request.partner_queue_accessed", staff.getId());
        // Laboratory orders carry their latest result's review state (one query for the whole list), so the
        // queue does not depend on a separately loaded, page-limited result list (Phase 5D).
        Map<String, LatestResult> latest = new java.util.HashMap<>();
        if ("LABORATORY".equals(type) && !assigned.isEmpty()) {
            for (var result : labResults.findByRequestIdInOrderByCreatedAtDesc(
                    assigned.stream().map(PatientServiceRequestEntity::getId).toList())) {
                latest.putIfAbsent(result.getRequestId(), new LatestResult(
                        result.getId(), result.getStatus(), result.getCreatedAt(), result.getReviewedAt()));
            }
        }
        return assigned.stream()
                .map(request -> partnerDetails(request, latest.get(request.getId())))
                .toList();
    }

    @Transactional
    public Page<PartnerRequestDetails> pageAssigned(
            String email, String requestType, String status, String search, int page, int size) {
        String type = normalizeType(requestType);
        PlatformAccountEntity staff = requirePartnerAccount(email, type);
        validatePage(page, size);
        String normalizedStatus = normalizeStatus(status);
        String searchPattern = normalizeSearch(search);
        audit(email, "service_request.partner_queue_accessed", staff.getId());
        PageRequest pageable = PageRequest.of(page, size, REQUEST_ORDER);
        List<PatientServiceRequestEntity> assigned = requests.searchAssignedRequestsPage(
                staff.getId(), type, normalizedStatus, searchPattern,
                pageable);
        long total = requests.countAssignedRequests(staff.getId(), type, normalizedStatus, searchPattern);
        Map<String, LatestResult> latest = new java.util.HashMap<>();
        if ("LABORATORY".equals(type) && !assigned.isEmpty()) {
            for (var result : labResults.findByRequestIdInOrderByCreatedAtDesc(
                    assigned.stream().map(PatientServiceRequestEntity::getId).toList())) {
                latest.putIfAbsent(result.getRequestId(), new LatestResult(
                        result.getId(), result.getStatus(), result.getCreatedAt(), result.getReviewedAt()));
            }
        }
        return new PageImpl<>(
                assigned.stream().map(request -> partnerDetails(request, latest.get(request.getId()))).toList(),
                pageable,
                total);
    }

    @Transactional
    public PartnerRequestDetails updateAssignedStatus(String email, String requestId, String status) {
        PatientServiceRequestEntity request = requests.findByIdForUpdate(requestId)
                .orElseThrow(PatientServiceRequestService::notFound);
        PlatformAccountEntity staff = requirePartnerAccount(email, request.getRequestType());
        if (!staff.getId().equals(request.getAssignedStaffAccountId())) {
            throw notFound();
        }
        String next = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        boolean starting = "OPEN".equals(request.getStatus())
                && "IN_PROGRESS".equals(next)
                && (!"HOME_CARE".equals(request.getRequestType()) || request.getScheduledAt() != null);
        boolean completingHomeCare = "HOME_CARE".equals(staff.getAccountType())
                && "IN_PROGRESS".equals(request.getStatus())
                && request.getScheduledAt() != null
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

    private static String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!PATIENT_REQUEST_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported service request type");
        }
        return normalized;
    }

    private static String normalizeStatus(String status) {
        if (status == null) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!SERVICE_REQUEST_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported service request status");
        }
        return normalized;
    }

    private static String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        if (search.length() > MAX_SEARCH_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search must be 120 characters or fewer");
        }
        String normalized = search.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        String escaped = normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be 0 or more and size 1 to 100");
        }
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
                request.getScheduledAt(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }

    private static AdminRequestSummary adminSummary(PatientServiceRequestEntity request, String assignedStaffPublicId) {
        return new AdminRequestSummary(
                request.getId(), request.getRequestType(), request.getStatus(), request.getCreatedAt(),
                assignedStaffPublicId);
    }

    private static PartnerRequestDetails partnerDetails(PatientServiceRequestEntity request) {
        return partnerDetails(request, null);
    }

    private static PartnerRequestDetails partnerDetails(PatientServiceRequestEntity request, LatestResult latestResult) {
        return new PartnerRequestDetails(
                request.getId(),
                request.getRequestType(),
                request.getRequestedService(),
                request.getLocationDescription(),
                request.getStatus(),
                request.getScheduledAt(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                latestResult);
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
            Instant scheduledAt,
            java.time.Instant createdAt,
            java.time.Instant updatedAt) {
    }

    /**
     * Administrator view of a patient request: no service description, location or result.
     * {@code assignedStaffPublicId} is the assigned partner's public account ID, or null when unassigned.
     */
    public record AdminRequestSummary(
            String id,
            String requestType,
            String status,
            java.time.Instant createdAt,
            String assignedStaffPublicId) {
    }

    public record PartnerAssignment(
            String requestId,
            String assignedStaffAccountId,
            String requestType,
            String status) {
    }

    /**
     * {@code latestResult}: for laboratory orders in the assigned-queue listing, the newest submitted result's
     * review state (no result text); null otherwise (Phase 5D, additive).
     */
    public record PartnerRequestDetails(
            String id,
            String requestType,
            String requestedService,
            String locationDescription,
            String status,
            Instant scheduledAt,
            java.time.Instant createdAt,
            java.time.Instant updatedAt,
            LatestResult latestResult) {
    }

    public record LatestResult(String id, String status, Instant createdAt, Instant reviewedAt) {
    }
}

package com.mednet.appointment.app;

import java.time.Instant;
import java.util.Locale;
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
import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.notification.app.NotificationService;
import com.mednet.patient.app.PatientCareAccess;
import com.mednet.appointment.data.AppointmentEntity;
import com.mednet.appointment.data.AppointmentRepository;
import com.mednet.patient.data.PatientProfileEntity;
import com.mednet.patient.data.PatientProfileRepository;
import com.mednet.provider.data.ProviderApplicationEntity;
import com.mednet.provider.data.ProviderApplicationRepository;
import com.mednet.provider.data.ProviderAvailabilitySlotEntity;
import com.mednet.provider.data.ProviderAvailabilitySlotRepository;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class AppointmentService {

    private final PlatformAccountRepository accounts;
    private final PatientProfileRepository patientProfiles;
    private final ProviderApplicationRepository providers;
    private final ProviderAvailabilitySlotRepository slots;
    private final AppointmentRepository appointments;
    private final AdminAuditEventRepository auditEvents;
    private final NotificationService notifications;
    private final PatientCareAccess careAccess;

    public AppointmentService(
            PlatformAccountRepository accounts,
            PatientProfileRepository patientProfiles,
            ProviderApplicationRepository providers,
            ProviderAvailabilitySlotRepository slots,
            AppointmentRepository appointments,
            AdminAuditEventRepository auditEvents,
            NotificationService notifications,
            PatientCareAccess careAccess) {
        this.accounts = accounts;
        this.patientProfiles = patientProfiles;
        this.providers = providers;
        this.slots = slots;
        this.appointments = appointments;
        this.auditEvents = auditEvents;
        this.notifications = notifications;
        this.careAccess = careAccess;
    }

    @Transactional
    public AppointmentDetails request(String patientEmail, String slotId) {
        PlatformAccountEntity patient = patient(patientEmail);
        if (patientProfiles.findFirstByAccountId(patient.getId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Complete your patient profile before booking");
        }

        ProviderAvailabilitySlotEntity slot = slots.findById(slotId)
                .orElseThrow(AppointmentService::notFound);
        ProviderApplicationEntity provider = providers.findByIdForUpdate(slot.getProviderApplicationId())
                .orElseThrow(AppointmentService::notFound);
        requireApprovedProvider(provider);

        ProviderAvailabilitySlotEntity lockedSlot = slots.findByIdForUpdate(slotId)
                .orElseThrow(AppointmentService::notFound);
        if (!"OPEN".equals(lockedSlot.getStatus()) || !lockedSlot.getStartsAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The selected appointment time is unavailable");
        }
        lockedSlot.reserve();
        slots.save(lockedSlot);

        AppointmentEntity appointment = appointments.save(new AppointmentEntity(
                UUID.randomUUID().toString(),
                patient.getId(),
                provider.getId(),
                lockedSlot.getId()));
        audit(patientEmail, "appointment.requested", appointment.getId());
        String providerAccountId = careAccess.requireActiveProviderAccount(provider.getEmail()).getId();
        notify(providerAccountId, appointment.getId(), "A patient requested an appointment");
        return details(appointment);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentDetails> list(String actorEmail, String role, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        PageRequest pageable = PageRequest.of(
                safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        if ("PATIENT".equals(role)) {
            PlatformAccountEntity patient = patient(actorEmail);
            return appointments.findByPatientAccountIdOrderByCreatedAtDesc(patient.getId(), pageable)
                    .map(this::details);
        }
        ProviderApplicationEntity provider = approvedProvider(actorEmail);
        return appointments.findByProviderApplicationIdOrderByCreatedAtDesc(provider.getId(), pageable)
                .map(this::details);
    }

    @Transactional
    public Page<AdminAppointmentSummary> adminList(String actorEmail, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Page<AdminAppointmentSummary> result = appointments
                .findAll(PageRequest.of(
                        safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(appointment -> {
                    ProviderAvailabilitySlotEntity slot = slots.findById(appointment.getAvailabilitySlotId())
                            .orElseThrow(AppointmentService::notFound);
                    return new AdminAppointmentSummary(
                            appointment.getId(),
                            appointment.getPatientAccountId(),
                            appointment.getProviderApplicationId(),
                            appointment.getStatus(),
                            slot.getStartsAt(),
                            slot.getEndsAt(),
                            appointment.getCreatedAt(),
                            appointment.getUpdatedAt());
                });
        audit(actorEmail, "appointment.admin_list_accessed", "admin_list");
        return result;
    }

    @Transactional(readOnly = true)
    public AppointmentDetails get(String actorEmail, String role, String appointmentId) {
        AppointmentEntity appointment = appointments.findById(appointmentId)
                .orElseThrow(AppointmentService::notFound);
        requireParticipant(appointment, actorEmail, role);
        return details(appointment);
    }

    @Transactional
    public AppointmentDetails act(
            String actorEmail,
            String role,
            String appointmentId,
            String action,
            String proposedSlotId) {
        AppointmentEntity initial = appointments.findById(appointmentId)
                .orElseThrow(AppointmentService::notFound);
        ProviderApplicationEntity provider = providers.findByIdForUpdate(initial.getProviderApplicationId())
                .orElseThrow(AppointmentService::notFound);
        requireApprovedProvider(provider);
        AppointmentEntity appointment = appointments.findByIdForUpdate(appointmentId)
                .orElseThrow(AppointmentService::notFound);
        requireParticipant(appointment, actorEmail, role);

        String normalizedAction = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        switch (normalizedAction) {
            case "ACCEPT" -> {
                requireProvider(role);
                requireState(appointment, "PENDING");
                appointment.confirm();
            }
            case "DECLINE" -> {
                requireProvider(role);
                requireState(appointment, "PENDING");
                appointment.decline();
                release(appointment.getAvailabilitySlotId());
            }
            case "CANCEL" -> {
                requireStateIn(appointment, "PENDING", "CONFIRMED", "RESCHEDULE_REQUESTED");
                ProviderAvailabilitySlotEntity bookedSlot = slotForUpdate(appointment.getAvailabilitySlotId());
                if (!bookedSlot.getStartsAt().isAfter(Instant.now())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "An appointment cannot be cancelled after it starts");
                }
                appointment.cancel();
                bookedSlot.release();
                if (appointment.getRequestedAvailabilitySlotId() != null) {
                    release(appointment.getRequestedAvailabilitySlotId());
                }
            }
            case "REQUEST_RESCHEDULE" -> {
                requireState(appointment, "CONFIRMED");
                if (proposedSlotId == null || proposedSlotId.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A proposed availability slot is required");
                }
                ProviderAvailabilitySlotEntity oldSlot = slots.findById(appointment.getAvailabilitySlotId())
                        .orElseThrow(AppointmentService::notFound);
                if (!oldSlot.getStartsAt().isAfter(Instant.now())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "A started appointment cannot be rescheduled");
                }
                ProviderAvailabilitySlotEntity proposed = slotForUpdate(proposedSlotId);
                requireSlotForProvider(proposed, provider.getId());
                if (!"OPEN".equals(proposed.getStatus()) || !proposed.getStartsAt().isAfter(Instant.now())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "The proposed appointment time is unavailable");
                }
                proposed.reserve();
                appointment.requestReschedule(proposed.getId(), normalizeEmail(actorEmail));
            }
            case "ACCEPT_RESCHEDULE" -> {
                requireState(appointment, "RESCHEDULE_REQUESTED");
                requireOtherRescheduleParty(appointment, actorEmail);
                ProviderAvailabilitySlotEntity oldSlot = slotForUpdate(appointment.getAvailabilitySlotId());
                ProviderAvailabilitySlotEntity proposed = slotForUpdate(appointment.getRequestedAvailabilitySlotId());
                if (!proposed.getStartsAt().isAfter(Instant.now())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "The proposed appointment time has passed");
                }
                oldSlot.release();
                appointment.acceptReschedule();
            }
            case "DECLINE_RESCHEDULE" -> {
                requireState(appointment, "RESCHEDULE_REQUESTED");
                requireOtherRescheduleParty(appointment, actorEmail);
                release(appointment.getRequestedAvailabilitySlotId());
                appointment.declineReschedule();
            }
            case "COMPLETE" -> {
                requireProvider(role);
                requireState(appointment, "CONFIRMED");
                ProviderAvailabilitySlotEntity slot = slots.findById(appointment.getAvailabilitySlotId())
                        .orElseThrow(AppointmentService::notFound);
                if (slot.getEndsAt().isAfter(Instant.now())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "An appointment cannot be completed before it ends");
                }
                appointment.complete();
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported appointment action");
        }

        AppointmentEntity saved = appointments.save(appointment);
        audit(actorEmail, "appointment." + normalizedAction.toLowerCase(Locale.ROOT), saved.getId());
        String recipientAccountId = "PATIENT".equals(role)
                ? careAccess.requireActiveProviderAccount(provider.getEmail()).getId()
                : saved.getPatientAccountId();
        notify(recipientAccountId, saved.getId(), "Your appointment status was updated");
        return details(saved);
    }

    private void notify(String recipientAccountId, String appointmentId, String title) {
        notifications.create(
                recipientAccountId,
                "appointment:" + appointmentId + ":" + UUID.randomUUID(),
                "APPOINTMENT",
                title,
                "appointment",
                appointmentId);
    }

    private PlatformAccountEntity patient(String email) {
        PlatformAccountEntity patient = accounts.findFirstByEmailIgnoreCase(email)
                .orElseThrow(AppointmentService::notFound);
        if (!"PATIENT".equals(patient.getAccountType())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient appointment access is not allowed");
        }
        return patient;
    }

    private ProviderApplicationEntity approvedProvider(String email) {
        return providers.findFirstByEmailIgnoreCaseAndStatusOrderByReviewedAtDesc(email, "APPROVED")
                .orElseThrow(AppointmentService::notFound);
    }

    private static void requireApprovedProvider(ProviderApplicationEntity provider) {
        if (!"APPROVED".equals(provider.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Approved provider not found");
        }
    }

    private void requireParticipant(AppointmentEntity appointment, String actorEmail, String role) {
        if ("PATIENT".equals(role)) {
            PlatformAccountEntity patient = patient(actorEmail);
            if (!patient.getId().equals(appointment.getPatientAccountId())) {
                throw notFound();
            }
            return;
        }
        ProviderApplicationEntity provider = approvedProvider(actorEmail);
        if (!provider.getId().equals(appointment.getProviderApplicationId())) {
            throw notFound();
        }
    }

    private static void requireProvider(String role) {
        if (!"PROVIDER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned provider can perform this action");
        }
    }

    private static void requireState(AppointmentEntity appointment, String required) {
        if (!required.equals(appointment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Appointment is not in the required state");
        }
    }

    private static void requireStateIn(AppointmentEntity appointment, String first, String second, String third) {
        if (!first.equals(appointment.getStatus())
                && !second.equals(appointment.getStatus())
                && !third.equals(appointment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Appointment cannot be cancelled in its current state");
        }
    }

    private static void requireOtherRescheduleParty(AppointmentEntity appointment, String actorEmail) {
        if (normalizeEmail(actorEmail).equals(appointment.getRescheduleRequestedByEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The other appointment participant must respond");
        }
    }

    private void requireSlotForProvider(ProviderAvailabilitySlotEntity slot, String providerId) {
        if (!providerId.equals(slot.getProviderApplicationId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The proposed slot belongs to another provider");
        }
    }

    private ProviderAvailabilitySlotEntity slotForUpdate(String slotId) {
        return slots.findByIdForUpdate(slotId).orElseThrow(AppointmentService::notFound);
    }

    private void release(String slotId) {
        ProviderAvailabilitySlotEntity slot = slotForUpdate(slotId);
        slot.release();
    }

    private AppointmentDetails details(AppointmentEntity appointment) {
        ProviderApplicationEntity provider = providers.findById(appointment.getProviderApplicationId())
                .orElseThrow(AppointmentService::notFound);
        PlatformAccountEntity patient = accounts.findById(appointment.getPatientAccountId())
                .orElseThrow(AppointmentService::notFound);
        PatientProfileEntity patientProfile = patientProfiles.findFirstByAccountId(patient.getId())
                .orElseThrow(AppointmentService::notFound);
        ProviderAvailabilitySlotEntity slot = slots.findById(appointment.getAvailabilitySlotId())
                .orElseThrow(AppointmentService::notFound);
        ProviderAvailabilitySlotEntity proposedSlot = appointment.getRequestedAvailabilitySlotId() == null
                ? null
                : slots.findById(appointment.getRequestedAvailabilitySlotId())
                        .orElseThrow(AppointmentService::notFound);
        return new AppointmentDetails(
                appointment.getId(),
                patient.getId(),
                appointment.getStatus(),
                patientProfile.getFullName(),
                provider.getDisplayName(),
                slot.getStartsAt(),
                slot.getEndsAt(),
                proposedSlot == null ? null : proposedSlot.getStartsAt(),
                proposedSlot == null ? null : proposedSlot.getEndsAt(),
                appointment.getCreatedAt(),
                appointment.getUpdatedAt());
    }

    private void audit(String actorEmail, String action, String appointmentId) {
        auditEvents.save(new AdminAuditEventEntity(
                UUID.randomUUID().toString(),
                normalizeEmail(actorEmail),
                action,
                "appointment",
                appointmentId));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment or participant not found");
    }

    public record AppointmentDetails(
            String id,
            String patientAccountId,
            String status,
            String patientName,
            String providerName,
            Instant startsAt,
            Instant endsAt,
            Instant proposedStartsAt,
            Instant proposedEndsAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record AdminAppointmentSummary(
            String id,
            String patientAccountId,
            String providerApplicationId,
            String status,
            Instant startsAt,
            Instant endsAt,
            Instant createdAt,
            Instant updatedAt) {
    }
}

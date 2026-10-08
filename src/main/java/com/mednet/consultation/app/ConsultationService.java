package com.mednet.consultation.app;

import java.time.Instant;
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
import com.mednet.appointment.data.AppointmentEntity;
import com.mednet.appointment.data.AppointmentRepository;
import com.mednet.consultation.data.ConsultationEntity;
import com.mednet.consultation.data.ConsultationMessageEntity;
import com.mednet.consultation.data.ConsultationMessageRepository;
import com.mednet.consultation.data.ConsultationRepository;
import com.mednet.notification.app.NotificationService;
import com.mednet.patient.app.PatientCareAccess;
import com.mednet.provider.data.ProviderApplicationEntity;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class ConsultationService {

    private final PatientCareAccess careAccess;
    private final AppointmentRepository appointments;
    private final ConsultationRepository consultations;
    private final ConsultationMessageRepository messages;
    private final AdminAuditEventRepository auditEvents;
    private final NotificationService notifications;

    public ConsultationService(
            PatientCareAccess careAccess,
            AppointmentRepository appointments,
            ConsultationRepository consultations,
            ConsultationMessageRepository messages,
            AdminAuditEventRepository auditEvents,
            NotificationService notifications) {
        this.careAccess = careAccess;
        this.appointments = appointments;
        this.consultations = consultations;
        this.messages = messages;
        this.auditEvents = auditEvents;
        this.notifications = notifications;
    }

    @Transactional
    public ConsultationDetails open(String email, String role, String appointmentId) {
        AppointmentEntity appointment = appointments.findByIdForUpdate(appointmentId)
                .orElseThrow(ConsultationService::notFound);
        Participant participant = participant(appointment, email, role);
        if (!"CONFIRMED".equals(appointment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A confirmed appointment is required");
        }
        ConsultationEntity session = consultations.findByAppointmentId(appointmentId).orElse(null);
        if (session == null) {
            session = consultations.save(new ConsultationEntity(UUID.randomUUID().toString(), appointmentId));
            audit(email, "consultation.opened", session.getId());
            notifications.create(
                    participant.otherAccountId(),
                    "consultation:" + session.getId() + ":opened",
                    "CONSULTATION",
                    "A text consultation is ready to join",
                    "consultation",
                    session.getId());
        } else if (!"OPEN".equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The consultation has ended");
        }
        return details(session);
    }

    @Transactional
    public ConsultationDetails get(String email, String role, String consultationId) {
        ConsultationEntity session = consultations.findById(consultationId)
                .orElseThrow(ConsultationService::notFound);
        AppointmentEntity appointment = appointments.findById(session.getAppointmentId())
                .orElseThrow(ConsultationService::notFound);
        participant(appointment, email, role);
        audit(email, "consultation.accessed", session.getId());
        return details(session);
    }

    @Transactional
    public Page<ConsultationMessageDetails> messages(
            String email, String role, String consultationId, int page, int size) {
        ConsultationEntity session = consultations.findById(consultationId)
                .orElseThrow(ConsultationService::notFound);
        AppointmentEntity appointment = appointments.findById(session.getAppointmentId())
                .orElseThrow(ConsultationService::notFound);
        participant(appointment, email, role);
        Page<ConsultationMessageDetails> result = messages
                .findByConsultationIdOrderByCreatedAtAsc(
                        session.getId(),
                        PageRequest.of(
                                Math.max(page, 0),
                                Math.min(Math.max(size, 1), 100),
                                Sort.by(Sort.Direction.ASC, "createdAt")))
                .map(ConsultationService::messageDetails);
        audit(email, "consultation.messages_accessed", session.getId());
        return result;
    }

    @Transactional
    public ConsultationMessageDetails send(String email, String role, String consultationId, String body) {
        ConsultationEntity session = consultations.findByIdForUpdate(consultationId)
                .orElseThrow(ConsultationService::notFound);
        AppointmentEntity appointment = appointments.findById(session.getAppointmentId())
                .orElseThrow(ConsultationService::notFound);
        Participant participant = participant(appointment, email, role);
        if (!"OPEN".equals(session.getStatus()) || !"CONFIRMED".equals(appointment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The consultation is not open");
        }
        ConsultationMessageEntity message = messages.save(new ConsultationMessageEntity(
                UUID.randomUUID().toString(),
                session.getId(),
                participant.accountId(),
                body.trim()));
        audit(email, "consultation.message_sent", message.getId());
        notifications.create(
                participant.otherAccountId(),
                "consultation:" + session.getId() + ":message:" + message.getId(),
                "CONSULTATION",
                "You have a new consultation message",
                "consultation",
                session.getId());
        return messageDetails(message);
    }

    @Transactional
    public ConsultationDetails end(String providerEmail, String consultationId) {
        ConsultationEntity session = consultations.findByIdForUpdate(consultationId)
                .orElseThrow(ConsultationService::notFound);
        AppointmentEntity appointment = appointments.findById(session.getAppointmentId())
                .orElseThrow(ConsultationService::notFound);
        participant(appointment, providerEmail, "PROVIDER");
        if (!"OPEN".equals(session.getStatus()) || !"CONFIRMED".equals(appointment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The consultation is not open");
        }
        session.end();
        audit(providerEmail, "consultation.ended", session.getId());
        return details(session);
    }

    private Participant participant(AppointmentEntity appointment, String email, String role) {
        if ("PATIENT".equals(role)) {
            PlatformAccountEntity patient = careAccess.requirePatient(email);
            if (!patient.getId().equals(appointment.getPatientAccountId())) {
                throw notFound();
            }
            ProviderApplicationEntity provider = careAccess.requireApprovedProvider(appointment.getProviderApplicationId());
            PlatformAccountEntity providerAccount = careAccess.requireActiveProviderAccount(provider.getEmail());
            return new Participant(patient.getId(), providerAccount.getId());
        }
        if ("PROVIDER".equals(role)) {
            ProviderApplicationEntity provider = careAccess.requireApprovedProviderByEmail(email);
            if (!provider.getId().equals(appointment.getProviderApplicationId())) {
                throw notFound();
            }
            PlatformAccountEntity providerAccount = careAccess.requireActiveProviderAccount(email);
            return new Participant(providerAccount.getId(), appointment.getPatientAccountId());
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Consultation access is not allowed");
    }

    private void audit(String actorEmail, String action, String resourceId) {
        auditEvents.save(new AdminAuditEventEntity(
                UUID.randomUUID().toString(),
                actorEmail.trim().toLowerCase(java.util.Locale.ROOT),
                action,
                "consultation",
                resourceId));
    }

    private static ConsultationDetails details(ConsultationEntity consultation) {
        return new ConsultationDetails(
                consultation.getId(),
                consultation.getAppointmentId(),
                consultation.getStatus(),
                consultation.getOpenedAt(),
                consultation.getEndedAt());
    }

    private static ConsultationMessageDetails messageDetails(ConsultationMessageEntity message) {
        return new ConsultationMessageDetails(
                message.getId(),
                message.getSenderAccountId(),
                message.getBody(),
                message.getCreatedAt());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found");
    }

    private record Participant(String accountId, String otherAccountId) {
    }

    public record ConsultationDetails(
            String id,
            String appointmentId,
            String status,
            Instant openedAt,
            Instant endedAt) {
    }

    public record ConsultationMessageDetails(
            String id,
            String senderAccountId,
            String body,
            Instant createdAt) {
    }
}

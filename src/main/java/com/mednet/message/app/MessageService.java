package com.mednet.message.app;

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
import com.mednet.appointment.data.AppointmentRepository;
import com.mednet.message.data.ConversationEntity;
import com.mednet.message.data.ConversationRepository;
import com.mednet.message.data.MessageEntity;
import com.mednet.message.data.MessageRepository;
import com.mednet.notification.app.NotificationService;
import com.mednet.patient.app.PatientCareAccess;
import com.mednet.provider.data.ProviderApplicationEntity;
import com.mednet.provider.data.ProviderApplicationRepository;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class MessageService {

    private final PatientCareAccess careAccess;
    private final ProviderApplicationRepository providers;
    private final AppointmentRepository appointments;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final AdminAuditEventRepository auditEvents;
    private final NotificationService notifications;

    public MessageService(
            PatientCareAccess careAccess,
            ProviderApplicationRepository providers,
            AppointmentRepository appointments,
            ConversationRepository conversations,
            MessageRepository messages,
            AdminAuditEventRepository auditEvents,
            NotificationService notifications) {
        this.careAccess = careAccess;
        this.providers = providers;
        this.appointments = appointments;
        this.conversations = conversations;
        this.messages = messages;
        this.auditEvents = auditEvents;
        this.notifications = notifications;
    }

    @Transactional
    public ConversationDetails open(String patientEmail, String providerId) {
        PlatformAccountEntity patient = careAccess.requirePatient(patientEmail);
        ProviderApplicationEntity provider = careAccess.requireApprovedProvider(providerId);
        careAccess.requireCareRelationship(patient.getId(), provider.getId());
        providers.findByIdForUpdate(provider.getId()).orElseThrow(MessageService::notFound);
        ConversationEntity conversation = conversations
                .findByPatientAccountIdAndProviderApplicationId(patient.getId(), provider.getId())
                .orElseGet(() -> conversations.save(new ConversationEntity(
                        UUID.randomUUID().toString(), patient.getId(), provider.getId())));
        audit(patientEmail, "message.conversation_opened", "conversation", conversation.getId());
        return details(conversation, provider);
    }

    @Transactional
    public java.util.List<ConversationDetails> list(String actorEmail, String role) {
        java.util.List<ConversationEntity> rows;
        if ("PATIENT".equals(role)) {
            PlatformAccountEntity patient = careAccess.requirePatient(actorEmail);
            rows = conversations.findByPatientAccountIdOrderByUpdatedAtDesc(patient.getId());
        } else {
            ProviderApplicationEntity provider = careAccess.requireApprovedProviderByEmail(actorEmail);
            rows = conversations.findByProviderApplicationIdOrderByUpdatedAtDesc(provider.getId());
        }
        return rows.stream().filter(this::hasCareRelationship).map(conversation -> {
            audit(actorEmail, "message.conversation_accessed", "conversation", conversation.getId());
            return details(
                    conversation,
                    providers.findById(conversation.getProviderApplicationId())
                            .orElseThrow(MessageService::notFound));
        }).toList();
    }

    @Transactional
    public Page<MessageDetails> messages(String actorEmail, String role, String conversationId, int page, int size) {
        ConversationEntity conversation = conversation(conversationId);
        participant(conversation, actorEmail, role);
        audit(actorEmail, "message.history_accessed", "conversation", conversationId);
        return messages.findByConversationIdOrderByCreatedAtAsc(
                        conversationId,
                        PageRequest.of(
                                Math.max(page, 0),
                                Math.min(Math.max(size, 1), 100),
                                Sort.by(Sort.Direction.ASC, "createdAt")))
                .map(MessageService::messageDetails);
    }

    @Transactional
    public MessageDetails send(String actorEmail, String role, String conversationId, String body) {
        ConversationEntity conversation = conversation(conversationId);
        PlatformAccountEntity sender = participant(conversation, actorEmail, role);
        MessageEntity message = messages.save(new MessageEntity(
                UUID.randomUUID().toString(), conversation.getId(), sender.getId(), body.trim()));
        conversation.touch();
        String recipientId;
        if ("PATIENT".equals(role)) {
            ProviderApplicationEntity provider = careAccess.requireApprovedProvider(
                    conversation.getProviderApplicationId());
            careAccess.requireCareRelationship(conversation.getPatientAccountId(), provider.getId());
            recipientId = careAccess.requireActiveProviderAccount(provider.getEmail()).getId();
        } else {
            careAccess.requireCareRelationship(conversation.getPatientAccountId(), conversation.getProviderApplicationId());
            recipientId = conversation.getPatientAccountId();
        }
        notifications.create(
                recipientId,
                "message:" + message.getId() + ":" + recipientId,
                "MESSAGE",
                "You have a new message",
                "conversation",
                conversation.getId());
        audit(actorEmail, "message.sent", "message", message.getId());
        return messageDetails(message);
    }

    private PlatformAccountEntity participant(ConversationEntity conversation, String email, String role) {
        if ("PATIENT".equals(role)) {
            PlatformAccountEntity patient = careAccess.requirePatient(email);
            if (!patient.getId().equals(conversation.getPatientAccountId())) {
                throw notFound();
            }
            return patient;
        }
        ProviderApplicationEntity provider = careAccess.requireApprovedProviderByEmail(email);
        if (!provider.getId().equals(conversation.getProviderApplicationId())) {
            throw notFound();
        }
        careAccess.requireCareRelationship(conversation.getPatientAccountId(), provider.getId());
        return careAccess.requireActiveProviderAccount(email);
    }

    private ConversationEntity conversation(String id) {
        return conversations.findById(id).orElseThrow(MessageService::notFound);
    }

    private boolean hasCareRelationship(ConversationEntity conversation) {
        return appointments
                .findFirstByPatientAccountIdAndProviderApplicationIdAndStatusInOrderByCreatedAtDesc(
                        conversation.getPatientAccountId(),
                        conversation.getProviderApplicationId(),
                        java.util.List.of("CONFIRMED", "COMPLETED", "RESCHEDULE_REQUESTED"))
                .isPresent();
    }

    private static ConversationDetails details(
            ConversationEntity conversation, ProviderApplicationEntity provider) {
        return new ConversationDetails(
                conversation.getId(),
                conversation.getPatientAccountId(),
                provider.getId(),
                provider.getDisplayName(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt());
    }

    private static MessageDetails messageDetails(MessageEntity message) {
        return new MessageDetails(
                message.getId(),
                message.getConversationId(),
                message.getSenderAccountId(),
                message.getBody(),
                message.getCreatedAt());
    }

    private void audit(String actorEmail, String action, String resourceType, String resourceId) {
        auditEvents.save(new AdminAuditEventEntity(
                UUID.randomUUID().toString(),
                actorEmail.trim().toLowerCase(java.util.Locale.ROOT),
                action,
                resourceType,
                resourceId));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation or message not found");
    }

    public record ConversationDetails(
            String id,
            String patientAccountId,
            String providerId,
            String providerDisplayName,
            java.time.Instant createdAt,
            java.time.Instant updatedAt) {
    }

    public record MessageDetails(
            String id,
            String conversationId,
            String senderAccountId,
            String body,
            java.time.Instant createdAt) {
    }
}

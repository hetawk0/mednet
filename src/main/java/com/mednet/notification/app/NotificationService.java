package com.mednet.notification.app;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.notification.data.NotificationEntity;
import com.mednet.notification.data.NotificationRepository;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class NotificationService {

    private final PlatformAccountRepository accounts;
    private final NotificationRepository notifications;

    public NotificationService(PlatformAccountRepository accounts, NotificationRepository notifications) {
        this.accounts = accounts;
        this.notifications = notifications;
    }

    @Transactional
    public void create(
            String recipientAccountId,
            String eventKey,
            String eventType,
            String title,
            String resourceType,
            String resourceId) {
        if (!notifications.existsByEventKey(eventKey)) {
            notifications.save(new NotificationEntity(
                    UUID.randomUUID().toString(),
                    recipientAccountId,
                    eventKey,
                    eventType,
                    title,
                    resourceType,
                    resourceId));
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationDetails> list(String email, int page, int size) {
        PlatformAccountEntity account = account(email);
        return notifications.findByRecipientAccountIdOrderByCreatedAtDesc(
                        account.getId(), PageRequest.of(
                                Math.max(page, 0),
                                Math.min(Math.max(size, 1), 100),
                                Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(NotificationService::toDetails);
    }

    @Transactional(readOnly = true)
    public long unreadCount(String email) {
        return notifications.countByRecipientAccountIdAndReadAtIsNull(account(email).getId());
    }

    @Transactional
    public NotificationDetails markRead(String email, String notificationId) {
        PlatformAccountEntity account = account(email);
        NotificationEntity notification = notifications.findByIdAndRecipientAccountId(notificationId, account.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.markRead();
        return toDetails(notification);
    }

    private PlatformAccountEntity account(String email) {
        return accounts.findFirstByEmailIgnoreCase(email)
                .filter(value -> "ACTIVE".equals(value.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private static NotificationDetails toDetails(NotificationEntity entity) {
        return new NotificationDetails(
                entity.getId(),
                entity.getEventType(),
                entity.getTitle(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getCreatedAt(),
                entity.getReadAt());
    }

    public record NotificationDetails(
            String id,
            String eventType,
            String title,
            String resourceType,
            String resourceId,
            java.time.Instant createdAt,
            java.time.Instant readAt) {
    }
}

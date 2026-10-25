package com.mednet.notification.app;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mednet.notification.data.NotificationEmailOutboxEntity;
import com.mednet.notification.data.NotificationEmailOutboxRepository;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class NotificationEmailOutboxOperations {

    private static final Duration LEASE = Duration.ofMinutes(2);
    private final NotificationEmailOutboxRepository outbox;

    public NotificationEmailOutboxOperations(NotificationEmailOutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Transactional
    public NotificationEmailDeliveryService.Claim claim(String id, Instant now) {
        NotificationEmailOutboxEntity item = outbox.findByIdForUpdate(id).orElse(null);
        if (item == null || !item.claim(now, now.plus(LEASE))) {
            return null;
        }
        return new NotificationEmailDeliveryService.Claim(
                item.getId(), item.getNotificationId(), item.getRecipientAccountId());
    }

    @Transactional
    public void markSent(String id) {
        outbox.findByIdForUpdate(id).ifPresent(item -> item.markSent(Instant.now()));
    }

    @Transactional
    public boolean markFailed(String id, String errorCode) {
        var item = outbox.findByIdForUpdate(id).orElse(null);
        if (item == null) {
            return false;
        }
        item.markFailed(Instant.now(), errorCode);
        return "FAILED".equals(item.getStatus());
    }

    @Transactional
    public void suppress(String id) {
        outbox.findByIdForUpdate(id).ifPresent(NotificationEmailOutboxEntity::suppress);
    }

    @Transactional
    public void enqueue(String notificationId, String recipientAccountId) {
        if (!outbox.existsByNotificationId(notificationId)) {
            outbox.save(new NotificationEmailOutboxEntity(UUID.randomUUID().toString(), notificationId, recipientAccountId));
        }
    }

    public java.util.List<String> readyIds(Instant now, int batchSize) {
        return outbox.findReadyIds(now, PageRequest.of(0, batchSize));
    }
}

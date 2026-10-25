package com.mednet.notification.app;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.auth.email.EkdSendEmailService;
import com.mednet.notification.data.NotificationRepository;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class NotificationEmailDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationEmailDeliveryService.class);
    private static final int BATCH_SIZE = 25;

    private final NotificationEmailOutboxOperations outbox;
    private final NotificationRepository notifications;
    private final PlatformAccountRepository accounts;
    private final EkdSendEmailService emailSender;
    private final NotificationEmailContentFactory contentFactory;
    private final boolean enabled;

    public NotificationEmailDeliveryService(
            NotificationEmailOutboxOperations outbox,
            NotificationRepository notifications,
            PlatformAccountRepository accounts,
            EkdSendEmailService emailSender,
            NotificationEmailContentFactory contentFactory,
            @Value("${mednet.notifications.email.enabled:true}") boolean enabled) {
        this.outbox = outbox;
        this.notifications = notifications;
        this.accounts = accounts;
        this.emailSender = emailSender;
        this.contentFactory = contentFactory;
        this.enabled = enabled;
    }

    @Scheduled(
            initialDelayString = "${mednet.notifications.email.initial-delay:PT10S}",
            fixedDelayString = "${mednet.notifications.email.dispatch-interval:PT15S}")
    public void dispatchDueEmails() {
        if (!enabled) {
            return;
        }
        Instant now = Instant.now();
        var ready = outbox.readyIds(now, BATCH_SIZE);
        for (String id : ready) {
            Claim claim = outbox.claim(id, now);
            if (claim == null) {
                continue;
            }
            deliver(claim);
        }
    }

    private void deliver(Claim claim) {
        var account = accounts.findById(claim.recipientAccountId()).orElse(null);
        if (account == null || !"ACTIVE".equals(account.getStatus())
                || !account.isEmailVerified() || !account.isEmailNotificationsEnabled()) {
            outbox.suppress(claim.id());
            return;
        }
        var notification = notifications.findByIdAndRecipientAccountId(
                claim.notificationId(), claim.recipientAccountId()).orElse(null);
        if (notification == null) {
            outbox.suppress(claim.id());
            return;
        }
        var content = contentFactory.create(notification, claim.recipientAccountId());
        if (emailSender.send(account.getEmail(), content.subject(), content.html(), content.text())) {
            outbox.markSent(claim.id());
        } else {
            boolean exhausted = outbox.markFailed(claim.id(), "provider_rejected");
            if (exhausted) {
                log.error("Email notification delivery exhausted retries (outboxId={}, errorCode={})",
                        claim.id(), "provider_rejected");
            }
        }
    }

    record Claim(String id, String notificationId, String recipientAccountId) {
    }
}

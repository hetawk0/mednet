package com.mednet.notification.app;

import java.util.UUID;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
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
    private final String configuredAdminEmail;

    public NotificationService(
            PlatformAccountRepository accounts,
            NotificationRepository notifications,
            @Value("${mednet.admin.email:}") String configuredAdminEmail) {
        this.accounts = accounts;
        this.notifications = notifications;
        this.configuredAdminEmail = configuredAdminEmail == null
                ? ""
                : configuredAdminEmail.trim().toLowerCase(java.util.Locale.ROOT);
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

    @Transactional
    public void createForAccountTypes(
            List<String> accountTypes,
            String eventKey,
            String eventType,
            String title,
            String resourceType,
            String resourceId) {
        List<PlatformAccountEntity> recipients = accounts.findByAccountTypeInAndStatus(accountTypes, "ACTIVE");
        PlatformAccountEntity configuredAdmin = accountTypes.contains("SUPER_ADMIN")
                ? ensureConfiguredAdministratorAccount()
                : null;
        if (configuredAdmin != null && recipients.stream().noneMatch(row -> row.getId().equals(configuredAdmin.getId()))) {
            recipients = new java.util.ArrayList<>(recipients);
            recipients.add(configuredAdmin);
        }
        for (PlatformAccountEntity recipient : recipients) {
            create(
                    recipient.getId(),
                    eventKey + ":" + recipient.getId(),
                    eventType,
                    title,
                    resourceType,
                    resourceId);
        }
    }

    @Transactional
    public Page<NotificationDetails> list(String email, int page, int size) {
        PlatformAccountEntity account = account(email);
        return notifications.findByRecipientAccountIdOrderByCreatedAtDesc(
                        account.getId(), PageRequest.of(
                                Math.max(page, 0),
                                Math.min(Math.max(size, 1), 100),
                                Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(NotificationService::toDetails);
    }

    @Transactional
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
        if (!configuredAdminEmail.isBlank() && configuredAdminEmail.equalsIgnoreCase(email)) {
            return ensureConfiguredAdministratorAccount();
        }
        return accounts.findFirstByEmailIgnoreCase(email)
                .filter(value -> "ACTIVE".equals(value.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private PlatformAccountEntity ensureConfiguredAdministratorAccount() {
        if (configuredAdminEmail.isBlank()) {
            return null;
        }
        PlatformAccountEntity account = accounts.findFirstByEmailIgnoreCase(configuredAdminEmail).orElse(null);
        if (account == null) {
            account = new PlatformAccountEntity(
                    UUID.randomUUID().toString(), configuredAdminEmail, "SUPER_ADMIN");
            account.verifyEmail();
            account = accounts.saveAndFlush(account);
        }
        if (!"SUPER_ADMIN".equals(account.getAccountType()) || !"ACTIVE".equals(account.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The configured administrator email must belong to an active SUPER_ADMIN account");
        }
        if (!account.isEmailVerified()) {
            account.verifyEmail();
        }
        return account;
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

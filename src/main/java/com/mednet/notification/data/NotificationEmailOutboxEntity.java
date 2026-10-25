package com.mednet.notification.data;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_email_outbox")
public class NotificationEmailOutboxEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "notification_id", nullable = false, unique = true, length = 36)
    private String notificationId;

    @Column(name = "recipient_account_id", nullable = false, length = 36)
    private String recipientAccountId;

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected NotificationEmailOutboxEntity() {
    }

    public NotificationEmailOutboxEntity(String id, String notificationId, String recipientAccountId) {
        this.id = id;
        this.notificationId = notificationId;
        this.recipientAccountId = recipientAccountId;
        this.nextAttemptAt = Instant.now();
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean claim(Instant now, Instant leaseExpiresAt) {
        if (("PENDING".equals(status) && !nextAttemptAt.isAfter(now))
                || ("PROCESSING".equals(status) && this.leaseExpiresAt != null
                        && !this.leaseExpiresAt.isAfter(now))) {
            status = "PROCESSING";
            this.leaseExpiresAt = leaseExpiresAt;
            return true;
        }
        return false;
    }

    public void markSent(Instant now) {
        status = "SENT";
        sentAt = now;
        leaseExpiresAt = null;
        lastErrorCode = null;
    }

    public void suppress() {
        status = "SUPPRESSED";
        leaseExpiresAt = null;
    }

    public void markFailed(Instant now, String errorCode) {
        attempts++;
        leaseExpiresAt = null;
        lastErrorCode = errorCode;
        if (attempts >= 8) {
            status = "FAILED";
            return;
        }
        status = "PENDING";
        long delaySeconds = Math.min(3600, 15L << Math.min(attempts - 1, 8));
        nextAttemptAt = now.plusSeconds(delaySeconds);
    }

    public String getId() {
        return id;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public String getRecipientAccountId() {
        return recipientAccountId;
    }

    public String getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}

package com.mednet.notification.data;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_notifications")
public class NotificationEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "recipient_account_id", nullable = false, length = 36)
    private String recipientAccountId;

    @Column(name = "event_key", nullable = false, unique = true, length = 160)
    private String eventKey;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(name = "resource_type", nullable = false, length = 32)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, length = 36)
    private String resourceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected NotificationEntity() {
    }

    public NotificationEntity(
            String id,
            String recipientAccountId,
            String eventKey,
            String eventType,
            String title,
            String resourceType,
            String resourceId) {
        this.id = id;
        this.recipientAccountId = recipientAccountId;
        this.eventKey = eventKey;
        this.eventType = eventType;
        this.title = title;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public String getRecipientAccountId() {
        return recipientAccountId;
    }

    public String getEventKey() {
        return eventKey;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTitle() {
        return title;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}

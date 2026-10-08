package com.mednet.consultation.data;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "consultation_messages")
public class ConsultationMessageEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "consultation_id", nullable = false, length = 36)
    private String consultationId;

    @Column(name = "sender_account_id", nullable = false, length = 36)
    private String senderAccountId;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ConsultationMessageEntity() {
    }

    public ConsultationMessageEntity(String id, String consultationId, String senderAccountId, String body) {
        this.id = id;
        this.consultationId = consultationId;
        this.senderAccountId = senderAccountId;
        this.body = body;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public String getConsultationId() {
        return consultationId;
    }

    public String getSenderAccountId() {
        return senderAccountId;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

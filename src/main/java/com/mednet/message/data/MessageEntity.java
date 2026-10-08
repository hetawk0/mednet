package com.mednet.message.data;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "patient_messages")
public class MessageEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;

    @Column(name = "sender_account_id", nullable = false, length = 36)
    private String senderAccountId;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MessageEntity() {
    }

    public MessageEntity(String id, String conversationId, String senderAccountId, String body) {
        this.id = id;
        this.conversationId = conversationId;
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

    public String getConversationId() {
        return conversationId;
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

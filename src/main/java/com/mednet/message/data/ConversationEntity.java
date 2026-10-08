package com.mednet.message.data;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "patient_conversations")
public class ConversationEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "patient_account_id", nullable = false, length = 36)
    private String patientAccountId;

    @Column(name = "provider_application_id", nullable = false, length = 36)
    private String providerApplicationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConversationEntity() {
    }

    public ConversationEntity(String id, String patientAccountId, String providerApplicationId) {
        this.id = id;
        this.patientAccountId = patientAccountId;
        this.providerApplicationId = providerApplicationId;
    }

    @PrePersist
    void setCreatedAt() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void setUpdatedAt() {
        updatedAt = Instant.now();
    }

    public void touch() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getPatientAccountId() {
        return patientAccountId;
    }

    public String getProviderApplicationId() {
        return providerApplicationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

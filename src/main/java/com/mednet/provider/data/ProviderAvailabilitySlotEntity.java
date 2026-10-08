package com.mednet.provider.data;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "provider_availability_slots")
public class ProviderAvailabilitySlotEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "provider_application_id", nullable = false, length = 36)
    private String providerApplicationId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "consultation_mode", nullable = false, length = 16)
    private String consultationMode = "TEXT";

    @Column(nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProviderAvailabilitySlotEntity() {
    }

    public ProviderAvailabilitySlotEntity(
            String id, String providerApplicationId, Instant startsAt, Instant endsAt) {
        this(id, providerApplicationId, startsAt, endsAt, "TEXT");
    }

    public ProviderAvailabilitySlotEntity(
            String id, String providerApplicationId, Instant startsAt, Instant endsAt, String consultationMode) {
        this.id = id;
        this.providerApplicationId = providerApplicationId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.consultationMode = consultationMode;
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

    public String getProviderApplicationId() {
        return providerApplicationId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public String getConsultationMode() {
        return consultationMode;
    }

    public String getStatus() {
        return status;
    }

    public void reserve() {
        this.status = "RESERVED";
    }

    public void release() {
        this.status = "OPEN";
    }

    public void close() {
        this.status = "CLOSED";
    }
}

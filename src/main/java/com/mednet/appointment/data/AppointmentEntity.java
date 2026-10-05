package com.mednet.appointment.data;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "appointments")
public class AppointmentEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "patient_account_id", nullable = false, length = 36)
    private String patientAccountId;

    @Column(name = "provider_application_id", nullable = false, length = 36)
    private String providerApplicationId;

    @Column(name = "availability_slot_id", nullable = false, length = 36)
    private String availabilitySlotId;

    @Column(name = "requested_availability_slot_id", length = 36)
    private String requestedAvailabilitySlotId;

    @Column(name = "reschedule_requested_by_email", length = 254)
    private String rescheduleRequestedByEmail;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppointmentEntity() {
    }

    public AppointmentEntity(
            String id,
            String patientAccountId,
            String providerApplicationId,
            String availabilitySlotId) {
        this.id = id;
        this.patientAccountId = patientAccountId;
        this.providerApplicationId = providerApplicationId;
        this.availabilitySlotId = availabilitySlotId;
        this.status = "PENDING";
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

    public String getId() {
        return id;
    }

    public String getPatientAccountId() {
        return patientAccountId;
    }

    public String getProviderApplicationId() {
        return providerApplicationId;
    }

    public String getAvailabilitySlotId() {
        return availabilitySlotId;
    }

    public String getRequestedAvailabilitySlotId() {
        return requestedAvailabilitySlotId;
    }

    public String getRescheduleRequestedByEmail() {
        return rescheduleRequestedByEmail;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void confirm() {
        this.status = "CONFIRMED";
    }

    public void decline() {
        this.status = "DECLINED";
    }

    public void cancel() {
        this.status = "CANCELLED";
    }

    public void complete() {
        this.status = "COMPLETED";
    }

    public void requestReschedule(String proposedSlotId, String requestedByEmail) {
        this.requestedAvailabilitySlotId = proposedSlotId;
        this.rescheduleRequestedByEmail = requestedByEmail;
        this.status = "RESCHEDULE_REQUESTED";
    }

    public void acceptReschedule() {
        this.availabilitySlotId = this.requestedAvailabilitySlotId;
        clearReschedule();
        this.status = "CONFIRMED";
    }

    public void declineReschedule() {
        clearReschedule();
        this.status = "CONFIRMED";
    }

    private void clearReschedule() {
        this.requestedAvailabilitySlotId = null;
        this.rescheduleRequestedByEmail = null;
    }
}

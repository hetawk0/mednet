package com.mednet.service.data;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "patient_service_requests")
public class PatientServiceRequestEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "patient_account_id", nullable = false, length = 36)
    private String patientAccountId;

    @Column(name = "request_type", nullable = false, length = 20)
    private String requestType;

    @Column(name = "requested_service", nullable = false, length = 240)
    private String requestedService;

    @Column(name = "location_description", length = 500)
    private String locationDescription;

    @Column(name = "assigned_staff_account_id", length = 36)
    private String assignedStaffAccountId;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PatientServiceRequestEntity() {
    }

    public PatientServiceRequestEntity(
            String id,
            String patientAccountId,
            String requestType,
            String requestedService,
            String locationDescription) {
        this.id = id;
        this.patientAccountId = patientAccountId;
        this.requestType = requestType;
        this.requestedService = requestedService;
        this.locationDescription = locationDescription;
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

    public void changeStatus(String status) {
        this.status = status;
    }

    public void assignTo(String staffAccountId) {
        this.assignedStaffAccountId = staffAccountId;
    }

    public void scheduleAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public String getId() {
        return id;
    }

    public String getPatientAccountId() {
        return patientAccountId;
    }

    public String getRequestType() {
        return requestType;
    }

    public String getRequestedService() {
        return requestedService;
    }

    public String getLocationDescription() {
        return locationDescription;
    }

    public String getAssignedStaffAccountId() {
        return assignedStaffAccountId;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
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
}

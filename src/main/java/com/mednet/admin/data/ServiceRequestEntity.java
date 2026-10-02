package com.mednet.admin.data;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "service_requests")
public class ServiceRequestEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "reference_id", nullable = false, unique = true, length = 80)
    private String referenceId;

    @Column(name = "request_type", nullable = false, length = 20)
    private String requestType;

    @Column(name = "requester_email", nullable = false, length = 254)
    private String requesterEmail;

    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ServiceRequestEntity() {
    }

    public ServiceRequestEntity(String id, String referenceId, String requestType, String requesterEmail) {
        this.id = id;
        this.referenceId = referenceId;
        this.requestType = requestType;
        this.requesterEmail = requesterEmail;
    }

    @PrePersist
    void setCreatedAt() {
        Instant now = Instant.now();
        if (createdAt == null)
            createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void setUpdatedAt() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getRequestType() {
        return requestType;
    }

    public String getRequesterEmail() {
        return requesterEmail;
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

    public void changeStatus(String status) {
        this.status = status;
    }
}
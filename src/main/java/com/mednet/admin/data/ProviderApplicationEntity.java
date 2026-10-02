package com.mednet.admin.data;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "provider_applications")
public class ProviderApplicationEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 120)
    private String specialty;

    @Column(name = "credential_reference", nullable = false, length = 120)
    private String credentialReference;

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by", length = 254)
    private String reviewedBy;

    protected ProviderApplicationEntity() {
    }

    public ProviderApplicationEntity(
            String id, String displayName, String email, String specialty, String credentialReference) {
        this.id = id;
        this.displayName = displayName;
        this.email = email;
        this.specialty = specialty;
        this.credentialReference = credentialReference;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null)
            createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getSpecialty() {
        return specialty;
    }

    public String getCredentialReference() {
        return credentialReference;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void review(String status, String actor) {
        this.status = status;
        this.reviewedAt = Instant.now();
        this.reviewedBy = actor;
    }
}
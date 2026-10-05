package com.mednet.record.data;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "patient_provider_record_consents")
public class PatientProviderRecordConsentEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "patient_account_id", nullable = false, length = 36)
    private String patientAccountId;

    @Column(name = "provider_application_id", nullable = false, length = 36)
    private String providerApplicationId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PatientProviderRecordConsentEntity() {
    }

    public PatientProviderRecordConsentEntity(
            String id, String patientAccountId, String providerApplicationId) {
        this.id = id;
        this.patientAccountId = patientAccountId;
        this.providerApplicationId = providerApplicationId;
        grant();
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

    public String getStatus() {
        return status;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void grant() {
        this.status = "GRANTED";
        this.grantedAt = Instant.now();
        this.revokedAt = null;
    }

    public void revoke() {
        this.status = "REVOKED";
        this.revokedAt = Instant.now();
    }
}

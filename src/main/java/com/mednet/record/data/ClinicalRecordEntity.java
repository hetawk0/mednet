package com.mednet.record.data;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "clinical_records")
public class ClinicalRecordEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "patient_account_id", nullable = false, length = 36)
    private String patientAccountId;

    @Column(name = "author_account_id", nullable = false, length = 36)
    private String authorAccountId;

    @Column(name = "provider_application_id", nullable = false, length = 36)
    private String providerApplicationId;

    @Column(nullable = false, length = 32)
    private String category;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(name = "clinical_code", length = 80)
    private String clinicalCode;

    @Column(nullable = false, length = 10000)
    private String summary;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Column(name = "amends_record_id", length = 36)
    private String amendsRecordId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClinicalRecordEntity() {
    }

    public ClinicalRecordEntity(
            String id,
            String patientAccountId,
            String authorAccountId,
            String providerApplicationId,
            String category,
            String title,
            String clinicalCode,
            String summary,
            Instant effectiveAt,
            String amendsRecordId) {
        this.id = id;
        this.patientAccountId = patientAccountId;
        this.authorAccountId = authorAccountId;
        this.providerApplicationId = providerApplicationId;
        this.category = category;
        this.title = title;
        this.clinicalCode = clinicalCode;
        this.summary = summary;
        this.effectiveAt = effectiveAt;
        this.amendsRecordId = amendsRecordId;
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

    public String getPatientAccountId() {
        return patientAccountId;
    }

    public String getAuthorAccountId() {
        return authorAccountId;
    }

    public String getProviderApplicationId() {
        return providerApplicationId;
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getClinicalCode() {
        return clinicalCode;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public String getAmendsRecordId() {
        return amendsRecordId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

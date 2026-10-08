package com.mednet.vital.data;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "patient_vitals")
public class PatientVitalEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "patient_account_id", nullable = false, length = 36)
    private String patientAccountId;

    @Column(name = "author_account_id", nullable = false, length = 36)
    private String authorAccountId;

    @Column(nullable = false, length = 80)
    private String metric;

    @Column(name = "reading_value", nullable = false, precision = 12, scale = 3)
    private BigDecimal value;

    @Column(nullable = false, length = 40)
    private String unit;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(nullable = false, length = 24)
    private String source = "PATIENT_REPORTED";

    @Column(name = "clinical_record_id", nullable = false, length = 36)
    private String clinicalRecordId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PatientVitalEntity() {
    }

    public PatientVitalEntity(
            String id,
            String patientAccountId,
            String authorAccountId,
            String metric,
            BigDecimal value,
            String unit,
            Instant recordedAt,
            String clinicalRecordId) {
        this.id = id;
        this.patientAccountId = patientAccountId;
        this.authorAccountId = authorAccountId;
        this.metric = metric;
        this.value = value;
        this.unit = unit;
        this.recordedAt = recordedAt;
        this.clinicalRecordId = clinicalRecordId;
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

    public String getMetric() {
        return metric;
    }

    public BigDecimal getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public String getSource() {
        return source;
    }

    public String getClinicalRecordId() {
        return clinicalRecordId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

package com.mednet.laboratory.data;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "laboratory_results")
public class LaboratoryResultEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @Column(name = "patient_account_id", nullable = false, length = 36)
    private String patientAccountId;

    @Column(name = "author_account_id", nullable = false, length = 36)
    private String authorAccountId;

    @Column(nullable = false, length = 10000)
    private String summary;

    @Column(nullable = false, length = 24)
    private String status = "PENDING_REVIEW";

    @Column(name = "reviewer_account_id", length = 36)
    private String reviewerAccountId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LaboratoryResultEntity() {
    }

    public LaboratoryResultEntity(
            String id,
            String requestId,
            String patientAccountId,
            String authorAccountId,
            String summary) {
        this.id = id;
        this.requestId = requestId;
        this.patientAccountId = patientAccountId;
        this.authorAccountId = authorAccountId;
        this.summary = summary;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void review(String nextStatus, String reviewerAccountId) {
        this.status = nextStatus;
        this.reviewerAccountId = reviewerAccountId;
        this.reviewedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getPatientAccountId() {
        return patientAccountId;
    }

    public String getAuthorAccountId() {
        return authorAccountId;
    }

    public String getSummary() {
        return summary;
    }

    public String getStatus() {
        return status;
    }

    public String getReviewerAccountId() {
        return reviewerAccountId;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

package com.mednet.admin.data;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "platform_accounts")
public class PlatformAccountEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "google_subject", unique = true, length = 255)
    private String googleSubject;

    @Column(name = "account_type", nullable = false, length = 16)
    private String accountType;

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlatformAccountEntity() {
    }

    public PlatformAccountEntity(String id, String email, String accountType) {
        this.id = id;
        this.email = email;
        this.accountType = accountType;
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

    public String getEmail() {
        return email;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public String getAccountType() {
        return accountType;
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

    public void linkGoogleSubject(String googleSubject) {
        this.googleSubject = googleSubject;
    }

    public void changeAccountType(String accountType) {
        this.accountType = accountType;
    }
}
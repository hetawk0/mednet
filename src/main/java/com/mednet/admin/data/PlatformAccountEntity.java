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

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "verification_token_hash", length = 64)
    private String verificationTokenHash;

    @Column(name = "verification_token_expires_at")
    private Instant verificationTokenExpiresAt;

    @Column(name = "password_reset_token_hash", length = 64)
    private String passwordResetTokenHash;

    @Column(name = "password_reset_token_expires_at")
    private Instant passwordResetTokenExpiresAt;

    @Column(name = "password_reset_verified", nullable = false)
    private boolean passwordResetVerified;

    @Column(name = "password_reset_attempts", nullable = false)
    private int passwordResetAttempts;

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

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getVerificationTokenHash() {
        return verificationTokenHash;
    }

    public Instant getVerificationTokenExpiresAt() {
        return verificationTokenExpiresAt;
    }

    public String getPasswordResetTokenHash() {
        return passwordResetTokenHash;
    }

    public Instant getPasswordResetTokenExpiresAt() {
        return passwordResetTokenExpiresAt;
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
        this.emailVerified = true;
    }

    public void changeAccountType(String accountType) {
        this.accountType = accountType;
    }

    public void changeEmail(String email) {
        this.email = email;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setVerificationToken(String tokenHash, Instant expiresAt) {
        this.verificationTokenHash = tokenHash;
        this.verificationTokenExpiresAt = expiresAt;
    }

    public void verifyEmail() {
        this.emailVerified = true;
        this.verificationTokenHash = null;
        this.verificationTokenExpiresAt = null;
    }

    public void setPasswordResetCode(String tokenHash, Instant expiresAt) {
        this.passwordResetTokenHash = tokenHash;
        this.passwordResetTokenExpiresAt = expiresAt;
        this.passwordResetVerified = false;
        this.passwordResetAttempts = 0;
    }

    public int recordPasswordResetAttempt() {
        return ++this.passwordResetAttempts;
    }

    public void setPasswordResetTicket(String tokenHash, Instant expiresAt) {
        this.passwordResetTokenHash = tokenHash;
        this.passwordResetTokenExpiresAt = expiresAt;
        this.passwordResetVerified = true;
        this.passwordResetAttempts = 0;
    }

    public void clearPasswordResetToken() {
        this.passwordResetTokenHash = null;
        this.passwordResetTokenExpiresAt = null;
        this.passwordResetVerified = false;
        this.passwordResetAttempts = 0;
    }

    public boolean isPasswordResetVerified() {
        return passwordResetVerified;
    }
}
package com.mednet.patient.data;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "patient_profiles")
public class PatientProfileEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "account_id", nullable = false, unique = true, length = 36)
    private String accountId;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "phone_number", nullable = false, length = 32)
    private String phoneNumber;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PatientProfileEntity() {
    }

    public PatientProfileEntity(
            String id,
            String accountId,
            String fullName,
            LocalDate dateOfBirth,
            String phoneNumber,
            String address) {
        this.id = id;
        this.accountId = accountId;
        update(fullName, dateOfBirth, phoneNumber, address);
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

    public String getAccountId() {
        return accountId;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getAddress() {
        return address;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(String fullName, LocalDate dateOfBirth, String phoneNumber, String address) {
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.phoneNumber = phoneNumber;
        this.address = address;
    }
}

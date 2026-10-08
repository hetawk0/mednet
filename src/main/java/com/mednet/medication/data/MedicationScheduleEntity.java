package com.mednet.medication.data;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "medication_schedules")
public class MedicationScheduleEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "patient_account_id", nullable = false, length = 36)
    private String patientAccountId;

    @Column(name = "medication_name", nullable = false, length = 160)
    private String medicationName;

    @Column(nullable = false, length = 120)
    private String dose;

    @Column(name = "reminder_time", nullable = false)
    private LocalTime reminderTime;

    @Column(name = "time_zone", nullable = false, length = 80)
    private String timeZone;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "next_reminder_at", nullable = false)
    private Instant nextReminderAt;

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MedicationScheduleEntity() {
    }

    public MedicationScheduleEntity(
            String id,
            String patientAccountId,
            String medicationName,
            String dose,
            LocalTime reminderTime,
            String timeZone,
            LocalDate startDate,
            LocalDate endDate,
            Instant nextReminderAt) {
        this.id = id;
        this.patientAccountId = patientAccountId;
        this.medicationName = medicationName;
        this.dose = dose;
        this.reminderTime = reminderTime;
        this.timeZone = timeZone;
        this.startDate = startDate;
        this.endDate = endDate;
        this.nextReminderAt = nextReminderAt;
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

    public void update(
            String medicationName,
            String dose,
            LocalTime reminderTime,
            String timeZone,
            LocalDate startDate,
            LocalDate endDate,
            Instant nextReminderAt) {
        this.medicationName = medicationName;
        this.dose = dose;
        this.reminderTime = reminderTime;
        this.timeZone = timeZone;
        this.startDate = startDate;
        this.endDate = endDate;
        this.nextReminderAt = nextReminderAt;
        this.status = "ACTIVE";
    }

    public void stop() {
        status = "STOPPED";
    }

    public void advanceReminder(Instant now) {
        ZoneId zone = ZoneId.of(timeZone);
        LocalDate nextDate = now.atZone(zone).toLocalDate();
        Instant candidate = nextDate.atTime(reminderTime).atZone(zone).toInstant();
        if (!candidate.isAfter(now)) {
            nextDate = nextDate.plusDays(1);
            candidate = nextDate.atTime(reminderTime).atZone(zone).toInstant();
        }
        nextReminderAt = candidate;
        if (endDate != null && nextDate.isAfter(endDate)) {
            status = "STOPPED";
        }
    }

    public String getId() {
        return id;
    }

    public String getPatientAccountId() {
        return patientAccountId;
    }

    public String getMedicationName() {
        return medicationName;
    }

    public String getDose() {
        return dose;
    }

    public LocalTime getReminderTime() {
        return reminderTime;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Instant getNextReminderAt() {
        return nextReminderAt;
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

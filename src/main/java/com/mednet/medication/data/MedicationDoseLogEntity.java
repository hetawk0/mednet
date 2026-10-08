package com.mednet.medication.data;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "medication_dose_logs")
public class MedicationDoseLogEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "schedule_id", nullable = false, length = 36)
    private String scheduleId;

    @Column(name = "dose_date", nullable = false)
    private LocalDate doseDate;

    @Column(name = "taken_at", nullable = false)
    private Instant takenAt;

    protected MedicationDoseLogEntity() {
    }

    public MedicationDoseLogEntity(String id, String scheduleId, LocalDate doseDate, Instant takenAt) {
        this.id = id;
        this.scheduleId = scheduleId;
        this.doseDate = doseDate;
        this.takenAt = takenAt;
    }

    @PrePersist
    void setTakenAt() {
        if (takenAt == null) {
            takenAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public LocalDate getDoseDate() {
        return doseDate;
    }

    public Instant getTakenAt() {
        return takenAt;
    }
}

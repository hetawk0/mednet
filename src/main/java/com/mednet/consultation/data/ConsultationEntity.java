package com.mednet.consultation.data;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "consultations")
public class ConsultationEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "appointment_id", nullable = false, unique = true, length = 36)
    private String appointmentId;

    @Column(nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected ConsultationEntity() {
    }

    public ConsultationEntity(String id, String appointmentId) {
        this.id = id;
        this.appointmentId = appointmentId;
    }

    @PrePersist
    void setOpenedAt() {
        if (openedAt == null) {
            openedAt = Instant.now();
        }
    }

    public void end() {
        status = "ENDED";
        endedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getAppointmentId() {
        return appointmentId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}

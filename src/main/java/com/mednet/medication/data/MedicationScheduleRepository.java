package com.mednet.medication.data;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface MedicationScheduleRepository extends JpaRepository<MedicationScheduleEntity, String> {
    List<MedicationScheduleEntity> findByPatientAccountIdOrderByCreatedAtDesc(String patientAccountId);

    Optional<MedicationScheduleEntity> findByIdAndPatientAccountId(String id, String patientAccountId);

    List<MedicationScheduleEntity> findByStatusAndNextReminderAtLessThanEqual(String status, Instant now);

    boolean existsByPatientAccountId(String patientAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select schedule from MedicationScheduleEntity schedule where schedule.id = :id")
    Optional<MedicationScheduleEntity> findByIdForUpdate(@Param("id") String id);
}

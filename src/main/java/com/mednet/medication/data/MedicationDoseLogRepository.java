package com.mednet.medication.data;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationDoseLogRepository extends JpaRepository<MedicationDoseLogEntity, String> {
    Optional<MedicationDoseLogEntity> findByScheduleIdAndDoseDate(String scheduleId, LocalDate doseDate);

    List<MedicationDoseLogEntity> findByScheduleIdOrderByDoseDateDesc(String scheduleId);
}

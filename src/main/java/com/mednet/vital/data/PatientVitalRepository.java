package com.mednet.vital.data;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientVitalRepository extends JpaRepository<PatientVitalEntity, String> {
    Page<PatientVitalEntity> findByPatientAccountIdOrderByRecordedAtDescCreatedAtDesc(
            String patientAccountId, Pageable pageable);

    List<PatientVitalEntity> findByPatientAccountIdOrderByRecordedAtDesc(String patientAccountId);

    boolean existsByPatientAccountId(String patientAccountId);
}

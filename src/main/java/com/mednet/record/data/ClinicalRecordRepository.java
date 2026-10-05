package com.mednet.record.data;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalRecordRepository extends JpaRepository<ClinicalRecordEntity, String> {

    Page<ClinicalRecordEntity> findByPatientAccountIdOrderByEffectiveAtDescCreatedAtDesc(
            String patientAccountId, Pageable pageable);

    Optional<ClinicalRecordEntity> findByIdAndPatientAccountId(String id, String patientAccountId);

    boolean existsByPatientAccountIdOrAuthorAccountId(String patientAccountId, String authorAccountId);
}

package com.mednet.laboratory.data;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface LaboratoryResultRepository extends JpaRepository<LaboratoryResultEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select result from LaboratoryResultEntity result where result.id = :id")
    Optional<LaboratoryResultEntity> findByIdForUpdate(@Param("id") String id);

    List<LaboratoryResultEntity> findTop100ByPatientAccountIdAndStatusOrderByCreatedAtDesc(
            String patientAccountId, String status);

    List<LaboratoryResultEntity> findTop100ByPatientAccountIdAndStatusOrderByCreatedAtAsc(
            String patientAccountId, String status);

    Optional<LaboratoryResultEntity> findByIdAndPatientAccountIdAndStatus(
            String id, String patientAccountId, String status);

    boolean existsByRequestIdAndStatus(String requestId, String status);
}

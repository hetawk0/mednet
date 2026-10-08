package com.mednet.service.data;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PatientServiceRequestRepository extends JpaRepository<PatientServiceRequestEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from PatientServiceRequestEntity request where request.id = :id")
    Optional<PatientServiceRequestEntity> findByIdForUpdate(@Param("id") String id);

    List<PatientServiceRequestEntity> findByPatientAccountIdAndRequestTypeOrderByCreatedAtDesc(
            String patientAccountId, String requestType);

    List<PatientServiceRequestEntity> findTop100ByRequestTypeOrderByCreatedAtDesc(String requestType);

    List<PatientServiceRequestEntity> findByAssignedStaffAccountIdAndRequestTypeOrderByCreatedAtDesc(
            String assignedStaffAccountId, String requestType);

    long countByRequestTypeAndStatusIn(String requestType, List<String> statuses);

    boolean existsByPatientAccountId(String patientAccountId);

    boolean existsByAssignedStaffAccountId(String assignedStaffAccountId);
}

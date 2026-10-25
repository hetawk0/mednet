package com.mednet.service.data;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Page<PatientServiceRequestEntity> findByPatientAccountIdAndRequestTypeOrderByCreatedAtDesc(
            String patientAccountId, String requestType, Pageable pageable);

    List<PatientServiceRequestEntity> findTop100ByRequestTypeOrderByCreatedAtDesc(String requestType);

    List<PatientServiceRequestEntity> findByAssignedStaffAccountIdAndRequestTypeOrderByCreatedAtDesc(
            String assignedStaffAccountId, String requestType);

    Page<PatientServiceRequestEntity> findByAssignedStaffAccountIdAndRequestTypeOrderByCreatedAtDesc(
            String assignedStaffAccountId, String requestType, Pageable pageable);

    long countByRequestTypeAndStatusIn(String requestType, List<String> statuses);

    boolean existsByPatientAccountId(String patientAccountId);

    boolean existsByAssignedStaffAccountId(String assignedStaffAccountId);

    Page<PatientServiceRequestEntity> findByRequestTypeOrderByCreatedAtDesc(String requestType, Pageable pageable);

    Page<PatientServiceRequestEntity> findByRequestTypeAndStatusInOrderByCreatedAtDesc(
            String requestType, java.util.Collection<String> statuses, Pageable pageable);
}

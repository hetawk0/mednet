package com.mednet.service.data;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientServiceRequestRepository extends JpaRepository<PatientServiceRequestEntity, String> {
    List<PatientServiceRequestEntity> findByPatientAccountIdAndRequestTypeOrderByCreatedAtDesc(
            String patientAccountId, String requestType);

    Optional<PatientServiceRequestEntity> findByIdAndPatientAccountIdAndRequestType(
            String id, String patientAccountId, String requestType);

    Optional<PatientServiceRequestEntity> findByIdAndAssignedStaffAccountIdAndRequestType(
            String id, String assignedStaffAccountId, String requestType);

    List<PatientServiceRequestEntity> findTop100ByRequestTypeOrderByCreatedAtDesc(String requestType);

    List<PatientServiceRequestEntity> findByAssignedStaffAccountIdAndRequestTypeOrderByCreatedAtDesc(
            String assignedStaffAccountId, String requestType);

    long countByRequestTypeAndStatusIn(String requestType, List<String> statuses);

    boolean existsByPatientAccountId(String patientAccountId);

    boolean existsByAssignedStaffAccountId(String assignedStaffAccountId);
}

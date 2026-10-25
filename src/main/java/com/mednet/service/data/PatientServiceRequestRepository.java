package com.mednet.service.data;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PatientServiceRequestRepository extends JpaRepository<PatientServiceRequestEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from PatientServiceRequestEntity request where request.id = :id")
    Optional<PatientServiceRequestEntity> findByIdForUpdate(@Param("id") String id);

    List<PatientServiceRequestEntity> findByPatientAccountIdAndRequestTypeOrderByCreatedAtDescIdDesc(
            String patientAccountId, String requestType);

    @Query("""
            select request from PatientServiceRequestEntity request
            where request.patientAccountId = :patientAccountId
              and request.requestType = :requestType
              and (:status is null or request.status = :status)
              and (:search is null
                   or lower(request.requestedService) like :search escape '!'
                   or lower(coalesce(request.locationDescription, '')) like :search escape '!')
            """)
    List<PatientServiceRequestEntity> searchPatientHistory(
            @Param("patientAccountId") String patientAccountId,
            @Param("requestType") String requestType,
            @Param("status") String status,
            @Param("search") String search,
            Sort sort);

    @Query("""
            select request from PatientServiceRequestEntity request
            where request.patientAccountId = :patientAccountId
              and request.requestType = :requestType
              and (:status is null or request.status = :status)
              and (:search is null
                   or lower(request.requestedService) like :search escape '!'
                   or lower(coalesce(request.locationDescription, '')) like :search escape '!')
            """)
    List<PatientServiceRequestEntity> searchPatientHistoryPage(
            @Param("patientAccountId") String patientAccountId,
            @Param("requestType") String requestType,
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            select count(request) from PatientServiceRequestEntity request
            where request.patientAccountId = :patientAccountId
              and request.requestType = :requestType
              and (:status is null or request.status = :status)
              and (:search is null
                   or lower(request.requestedService) like :search escape '!'
                   or lower(coalesce(request.locationDescription, '')) like :search escape '!')
            """)
    long countPatientHistory(
            @Param("patientAccountId") String patientAccountId,
            @Param("requestType") String requestType,
            @Param("status") String status,
            @Param("search") String search);

    List<PatientServiceRequestEntity> findByAssignedStaffAccountIdAndRequestTypeOrderByCreatedAtDescIdDesc(
            String assignedStaffAccountId, String requestType);

    List<PatientServiceRequestEntity> findByAssignedStaffAccountIdAndRequestTypeOrderByCreatedAtDesc(
            String assignedStaffAccountId, String requestType);

    @Query("""
            select request from PatientServiceRequestEntity request
            where request.assignedStaffAccountId = :assignedStaffAccountId
              and request.requestType = :requestType
              and (:status is null or request.status = :status)
              and (:search is null
                   or lower(request.requestedService) like :search escape '!'
                   or lower(coalesce(request.locationDescription, '')) like :search escape '!')
            """)
    List<PatientServiceRequestEntity> searchAssignedRequests(
            @Param("assignedStaffAccountId") String assignedStaffAccountId,
            @Param("requestType") String requestType,
            @Param("status") String status,
            @Param("search") String search,
            Sort sort);

    @Query("""
            select request from PatientServiceRequestEntity request
            where request.assignedStaffAccountId = :assignedStaffAccountId
              and request.requestType = :requestType
              and (:status is null or request.status = :status)
              and (:search is null
                   or lower(request.requestedService) like :search escape '!'
                   or lower(coalesce(request.locationDescription, '')) like :search escape '!')
            """)
    List<PatientServiceRequestEntity> searchAssignedRequestsPage(
            @Param("assignedStaffAccountId") String assignedStaffAccountId,
            @Param("requestType") String requestType,
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            select count(request) from PatientServiceRequestEntity request
            where request.assignedStaffAccountId = :assignedStaffAccountId
              and request.requestType = :requestType
              and (:status is null or request.status = :status)
              and (:search is null
                   or lower(request.requestedService) like :search escape '!'
                   or lower(coalesce(request.locationDescription, '')) like :search escape '!')
            """)
    long countAssignedRequests(
            @Param("assignedStaffAccountId") String assignedStaffAccountId,
            @Param("requestType") String requestType,
            @Param("status") String status,
            @Param("search") String search);

    Page<PatientServiceRequestEntity> findByPatientAccountIdAndRequestTypeOrderByCreatedAtDescIdDesc(
            String patientAccountId, String requestType, Pageable pageable);

    List<PatientServiceRequestEntity> findTop100ByRequestTypeOrderByCreatedAtDesc(String requestType);

    Page<PatientServiceRequestEntity> findByAssignedStaffAccountIdAndRequestTypeOrderByCreatedAtDescIdDesc(
            String assignedStaffAccountId, String requestType, Pageable pageable);

    long countByRequestTypeAndStatusIn(String requestType, List<String> statuses);

    boolean existsByPatientAccountId(String patientAccountId);

    boolean existsByAssignedStaffAccountId(String assignedStaffAccountId);

    Page<PatientServiceRequestEntity> findByRequestTypeOrderByCreatedAtDesc(String requestType, Pageable pageable);

    Page<PatientServiceRequestEntity> findByRequestTypeAndStatusInOrderByCreatedAtDesc(
            String requestType, java.util.Collection<String> statuses, Pageable pageable);
}

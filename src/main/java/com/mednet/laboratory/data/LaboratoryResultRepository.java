package com.mednet.laboratory.data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Page<LaboratoryResultEntity> findByPatientAccountIdAndStatusOrderByCreatedAtAscIdAsc(
            String patientAccountId, String status, Pageable pageable);

    Optional<LaboratoryResultEntity> findByIdAndPatientAccountIdAndStatus(
            String id, String patientAccountId, String status);

    boolean existsByRequestIdAndStatus(String requestId, String status);

    List<LaboratoryResultEntity> findTop100ByAuthorAccountIdOrderByCreatedAtDesc(String authorAccountId);

    Page<LaboratoryResultEntity> findByPatientAccountIdAndStatusOrderByCreatedAtDesc(
            String patientAccountId, String status, Pageable pageable);

    /** A laboratory's own submissions on requests still assigned to it, optionally one status. */
    @Query("""
            select result from LaboratoryResultEntity result
            where result.authorAccountId = :authorId
              and (:status = '' or result.status = :status)
              and exists (
                  select request.id from PatientServiceRequestEntity request
                  where request.id = result.requestId
                    and request.assignedStaffAccountId = :authorId
                    and request.requestType = 'LABORATORY')
            order by result.createdAt desc
            """)
    Page<LaboratoryResultEntity> findPartnerResults(
            @Param("authorId") String authorId, @Param("status") String status, Pageable pageable);

    @Query(value = """
            select patient.id as patientAccountId,
                   case when consent.id is null then false else true end as recordAccess,
                   count(distinct case when consent.id is not null then result.id end) as pendingResults,
                   min(case when consent.id is not null then result.created_at end) as oldestPendingAt
            from platform_accounts patient
            join appointments appointment
              on appointment.patient_account_id = patient.id
             and appointment.provider_application_id = :providerId
             and appointment.status in (:statuses)
            left join patient_provider_record_consents consent
              on consent.patient_account_id = patient.id
             and consent.provider_application_id = :providerId
             and consent.status = 'GRANTED'
            left join laboratory_results result
              on result.patient_account_id = patient.id
             and result.status = 'PENDING_REVIEW'
            where patient.account_type = 'PATIENT'
            group by patient.id, consent.id
            having consent.id is null
                or count(distinct case when consent.id is not null then result.id end) > 0
            order by case when consent.id is null then 1 else 0 end,
                     min(case when consent.id is not null then result.created_at end),
                     patient.id
            """,
            countQuery = """
                    select count(distinct patient.id)
                    from platform_accounts patient
                    where patient.account_type = 'PATIENT'
                      and exists (
                          select 1 from appointments appointment
                          where appointment.patient_account_id = patient.id
                            and appointment.provider_application_id = :providerId
                            and appointment.status in (:statuses)
                      )
                      and (
                          not exists (
                              select 1 from patient_provider_record_consents consent
                              where consent.patient_account_id = patient.id
                                and consent.provider_application_id = :providerId
                                and consent.status = 'GRANTED'
                          )
                          or exists (
                              select 1 from laboratory_results result
                              where result.patient_account_id = patient.id
                                and result.status = 'PENDING_REVIEW'
                          )
                      )
                    """,
            nativeQuery = true)
    Page<ReviewQueueRow> findReviewQueue(
            @Param("providerId") String providerId,
            @Param("statuses") List<String> statuses,
            Pageable pageable);

    @Query("""
            select count(distinct result.id) from LaboratoryResultEntity result
            where result.status = 'PENDING_REVIEW'
              and exists (
                  select appointment.id from AppointmentEntity appointment
                  where appointment.patientAccountId = result.patientAccountId
                    and appointment.providerApplicationId = :providerId
                    and appointment.status in :statuses
              )
              and exists (
                  select consent.id from PatientProviderRecordConsentEntity consent
                  where consent.patientAccountId = result.patientAccountId
                    and consent.providerApplicationId = :providerId
                    and consent.status = 'GRANTED'
              )
            """)
    long countPendingForProvider(
            @Param("providerId") String providerId, @Param("statuses") List<String> statuses);

    /** Every result on the given requests, newest first (to find each request's latest). */
    List<LaboratoryResultEntity> findByRequestIdInOrderByCreatedAtDesc(java.util.Collection<String> requestIds);

    @Query("""
            select count(distinct appointment.patientAccountId) from AppointmentEntity appointment
            where appointment.providerApplicationId = :providerId
              and appointment.status in :statuses
              and not exists (
                  select consent.id from PatientProviderRecordConsentEntity consent
                  where consent.patientAccountId = appointment.patientAccountId
                    and consent.providerApplicationId = :providerId
                    and consent.status = 'GRANTED'
              )
            """)
    long countPatientsWithoutRecordAccess(
            @Param("providerId") String providerId, @Param("statuses") List<String> statuses);

    interface ReviewQueueRow {
        String getPatientAccountId();

        Boolean getRecordAccess();

        long getPendingResults();

        OffsetDateTime getOldestPendingAt();
    }
}

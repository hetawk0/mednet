package com.mednet.patient.data;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientProfileRepository extends JpaRepository<PatientProfileEntity, String> {
    Optional<PatientProfileEntity> findFirstByAccountId(String accountId);

    boolean existsByAccountId(String accountId);

    java.util.List<PatientProfileEntity> findByAccountIdIn(java.util.Collection<String> accountIds);

    @Query(value = """
            select profile.accountId as patientAccountId, profile.fullName as name
            from PatientProfileEntity profile
            where exists (
                select account.id from PlatformAccountEntity account
                where account.id = profile.accountId
                  and account.accountType = 'PATIENT'
                  and account.status = 'ACTIVE'
            )
              and exists (
                select appointment.id from AppointmentEntity appointment
                where appointment.patientAccountId = profile.accountId
                  and appointment.providerApplicationId = :providerId
                  and appointment.status in :careStatuses
            )
            """,
            countQuery = """
            select count(profile) from PatientProfileEntity profile
            where exists (
                select account.id from PlatformAccountEntity account
                where account.id = profile.accountId
                  and account.accountType = 'PATIENT'
                  and account.status = 'ACTIVE'
            )
              and exists (
                select appointment.id from AppointmentEntity appointment
                where appointment.patientAccountId = profile.accountId
                  and appointment.providerApplicationId = :providerId
                  and appointment.status in :careStatuses
            )
            """)
    Page<ProviderPatientEntry> findProviderCarePatients(
            @Param("providerId") String providerId,
            @Param("careStatuses") List<String> careStatuses,
            Pageable pageable);

    @Query("""
            select profile.accountId as patientAccountId, profile.fullName as name
            from PatientProfileEntity profile
            where profile.accountId = :patientAccountId
              and exists (
                select account.id from PlatformAccountEntity account
                where account.id = profile.accountId
                  and account.accountType = 'PATIENT'
                  and account.status = 'ACTIVE'
            )
              and exists (
                select appointment.id from AppointmentEntity appointment
                where appointment.patientAccountId = profile.accountId
                  and appointment.providerApplicationId = :providerId
                  and appointment.status in :careStatuses
            )
            """)
    Optional<ProviderPatientEntry> findProviderCarePatient(
            @Param("patientAccountId") String patientAccountId,
            @Param("providerId") String providerId,
            @Param("careStatuses") List<String> careStatuses);

    interface ProviderPatientEntry {
        String getPatientAccountId();

        String getName();
    }
}

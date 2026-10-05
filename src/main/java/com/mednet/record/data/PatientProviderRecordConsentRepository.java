package com.mednet.record.data;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PatientProviderRecordConsentRepository
        extends JpaRepository<PatientProviderRecordConsentEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select consent from PatientProviderRecordConsentEntity consent
            where consent.patientAccountId = :patientId
              and consent.providerApplicationId = :providerId
            """)
    Optional<PatientProviderRecordConsentEntity> findForUpdate(
            @Param("patientId") String patientId,
            @Param("providerId") String providerId);

    List<PatientProviderRecordConsentEntity> findByPatientAccountIdOrderByUpdatedAtDesc(
            String patientAccountId);

    boolean existsByPatientAccountId(String patientAccountId);

    boolean existsByProviderApplicationId(String providerApplicationId);
}

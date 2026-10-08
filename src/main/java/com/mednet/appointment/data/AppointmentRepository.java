package com.mednet.appointment.data;

import java.util.Optional;
import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AppointmentRepository extends JpaRepository<AppointmentEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select appointment from AppointmentEntity appointment where appointment.id = :id")
    Optional<AppointmentEntity> findByIdForUpdate(@Param("id") String id);

    Page<AppointmentEntity> findByPatientAccountIdOrderByCreatedAtDesc(
            String patientAccountId, Pageable pageable);

    Page<AppointmentEntity> findByProviderApplicationIdOrderByCreatedAtDesc(
            String providerApplicationId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppointmentEntity> findFirstByPatientAccountIdAndProviderApplicationIdAndStatusInOrderByCreatedAtDesc(
            String patientAccountId, String providerApplicationId, Collection<String> statuses);

    boolean existsByPatientAccountId(String patientAccountId);

    boolean existsByProviderApplicationId(String providerApplicationId);
}

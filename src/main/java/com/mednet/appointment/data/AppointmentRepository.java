package com.mednet.appointment.data;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import com.mednet.provider.data.ProviderAvailabilitySlotEntity;

public interface AppointmentRepository extends JpaRepository<AppointmentEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select appointment from AppointmentEntity appointment where appointment.id = :id")
    Optional<AppointmentEntity> findByIdForUpdate(@Param("id") String id);

    Page<AppointmentEntity> findByPatientAccountId(String patientAccountId, Pageable pageable);

    Page<AppointmentEntity> findByProviderApplicationId(String providerApplicationId, Pageable pageable);

    Page<AppointmentEntity> findByProviderApplicationIdAndPatientAccountId(
            String providerApplicationId, String patientAccountId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppointmentEntity> findFirstByPatientAccountIdAndProviderApplicationIdAndStatusInOrderByCreatedAtDesc(
            String patientAccountId, String providerApplicationId, Collection<String> statuses);

    boolean existsByPatientAccountIdAndProviderApplicationIdAndStatusIn(
            String patientAccountId, String providerApplicationId, Collection<String> statuses);

    boolean existsByPatientAccountId(String patientAccountId);

    boolean existsByProviderApplicationId(String providerApplicationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select appointment from AppointmentEntity appointment,
                 ProviderAvailabilitySlotEntity slot
            where appointment.status = 'CONFIRMED'
              and appointment.availabilitySlotId = slot.id
              and slot.startsAt > :after
              and slot.startsAt <= :before
            order by slot.startsAt asc
            """)
    List<AppointmentEntity> findConfirmedAppointmentsStartingBetween(
            @Param("after") Instant after, @Param("before") Instant before);
}

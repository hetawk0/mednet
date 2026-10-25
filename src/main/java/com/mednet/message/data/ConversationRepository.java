package com.mednet.message.data;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {
    Optional<ConversationEntity> findByPatientAccountIdAndProviderApplicationId(
            String patientAccountId, String providerApplicationId);

    List<ConversationEntity> findByPatientAccountIdOrderByUpdatedAtDesc(String patientAccountId);

    List<ConversationEntity> findByProviderApplicationIdOrderByUpdatedAtDesc(String providerApplicationId);

    @Query("""
            select conversation from ConversationEntity conversation
            where conversation.patientAccountId = :patientId
              and exists (
                  select appointment.id from AppointmentEntity appointment
                  where appointment.patientAccountId = conversation.patientAccountId
                    and appointment.providerApplicationId = conversation.providerApplicationId
                    and appointment.status in :statuses
              )
            """)
    Page<ConversationEntity> findEligibleByPatient(
            @Param("patientId") String patientId,
            @Param("statuses") List<String> statuses,
            Pageable pageable);

    @Query("""
            select conversation from ConversationEntity conversation
            where conversation.providerApplicationId = :providerId
              and exists (
                  select appointment.id from AppointmentEntity appointment
                  where appointment.patientAccountId = conversation.patientAccountId
                    and appointment.providerApplicationId = conversation.providerApplicationId
                    and appointment.status in :statuses
              )
            """)
    Page<ConversationEntity> findEligibleByProvider(
            @Param("providerId") String providerId,
            @Param("statuses") List<String> statuses,
            Pageable pageable);

    boolean existsByPatientAccountId(String patientAccountId);

    boolean existsByProviderApplicationId(String providerApplicationId);
}

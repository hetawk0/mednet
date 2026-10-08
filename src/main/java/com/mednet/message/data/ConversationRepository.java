package com.mednet.message.data;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {
    Optional<ConversationEntity> findByPatientAccountIdAndProviderApplicationId(
            String patientAccountId, String providerApplicationId);

    List<ConversationEntity> findByPatientAccountIdOrderByUpdatedAtDesc(String patientAccountId);

    List<ConversationEntity> findByProviderApplicationIdOrderByUpdatedAtDesc(String providerApplicationId);

    boolean existsByPatientAccountId(String patientAccountId);

    boolean existsByProviderApplicationId(String providerApplicationId);
}

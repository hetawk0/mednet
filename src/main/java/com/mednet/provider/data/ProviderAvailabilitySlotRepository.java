package com.mednet.provider.data;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ProviderAvailabilitySlotRepository extends JpaRepository<ProviderAvailabilitySlotEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select slot from ProviderAvailabilitySlotEntity slot where slot.id = :id")
    Optional<ProviderAvailabilitySlotEntity> findByIdForUpdate(@Param("id") String id);

    boolean existsByProviderApplicationIdAndStatusNotAndStartsAtLessThanAndEndsAtGreaterThan(
            String providerApplicationId, String excludedStatus, Instant endsAt, Instant startsAt);

    Page<ProviderAvailabilitySlotEntity> findByProviderApplicationIdAndStatusAndStartsAtAfter(
            String providerApplicationId, String status, Instant startsAt, Pageable pageable);

    List<ProviderAvailabilitySlotEntity> findByProviderApplicationIdAndStatusAndStartsAtAfterOrderByStartsAtAsc(
            String providerApplicationId, String status, Instant startsAt);
}

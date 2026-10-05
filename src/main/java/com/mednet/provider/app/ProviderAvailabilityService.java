package com.mednet.provider.app;

import java.time.Instant;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mednet.admin.data.AdminAuditEventEntity;
import com.mednet.admin.data.AdminAuditEventRepository;
import com.mednet.provider.data.ProviderApplicationEntity;
import com.mednet.provider.data.ProviderApplicationRepository;
import com.mednet.provider.data.ProviderAvailabilitySlotEntity;
import com.mednet.provider.data.ProviderAvailabilitySlotRepository;

@Service
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class ProviderAvailabilityService {

    private final ProviderApplicationRepository applications;
    private final ProviderAvailabilitySlotRepository slots;
    private final AdminAuditEventRepository auditEvents;

    public ProviderAvailabilityService(
            ProviderApplicationRepository applications,
            ProviderAvailabilitySlotRepository slots,
            AdminAuditEventRepository auditEvents) {
        this.applications = applications;
        this.slots = slots;
        this.auditEvents = auditEvents;
    }

    @Transactional
    public AvailabilitySlot create(String providerEmail, Instant startsAt, Instant endsAt) {
        if (!startsAt.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Availability must start in the future");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Availability end must follow its start");
        }
        ProviderApplicationEntity provider = requireApprovedProviderByEmail(providerEmail);
        ProviderApplicationEntity lockedProvider = applications.findByIdForUpdate(provider.getId())
                .orElseThrow(() -> notFound());
        if (!lockedProvider.getEmail().equalsIgnoreCase(providerEmail)) {
            throw notFound();
        }
        if (slots.existsByProviderApplicationIdAndStatusNotAndStartsAtLessThanAndEndsAtGreaterThan(
                lockedProvider.getId(), "CLOSED", endsAt, startsAt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Availability slots may not overlap");
        }
        ProviderAvailabilitySlotEntity saved = slots.save(new ProviderAvailabilitySlotEntity(
                UUID.randomUUID().toString(), lockedProvider.getId(), startsAt, endsAt));
        auditEvents.save(new AdminAuditEventEntity(
                UUID.randomUUID().toString(),
                lockedProvider.getEmail(),
                "provider.availability_created",
                "availability_slot",
                saved.getId()));
        return toModel(saved);
    }

    @Transactional(readOnly = true)
    public Page<AvailabilitySlot> available(String providerId, int page, int size) {
        requireApprovedProviderById(providerId);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return slots.findByProviderApplicationIdAndStatusAndStartsAtAfter(
                        providerId,
                        "OPEN",
                        Instant.now(),
                        PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "startsAt")))
                .map(ProviderAvailabilityService::toModel);
    }

    @Transactional
    public void close(String providerEmail, String slotId) {
        ProviderAvailabilitySlotEntity slot = slots.findById(slotId).orElseThrow(ProviderAvailabilityService::notFound);
        ProviderApplicationEntity provider = applications.findByIdForUpdate(slot.getProviderApplicationId())
                .orElseThrow(ProviderAvailabilityService::notFound);
        if (!provider.getEmail().equalsIgnoreCase(providerEmail) || !"APPROVED".equals(provider.getStatus())) {
            throw notFound();
        }
        ProviderAvailabilitySlotEntity lockedSlot = slots.findByIdForUpdate(slotId)
                .orElseThrow(ProviderAvailabilityService::notFound);
        if (!"OPEN".equals(lockedSlot.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserved availability cannot be closed");
        }
        lockedSlot.close();
        auditEvents.save(new AdminAuditEventEntity(
                UUID.randomUUID().toString(),
                provider.getEmail(),
                "provider.availability_closed",
                "availability_slot",
                lockedSlot.getId()));
    }

    @Transactional(readOnly = true)
    public ProviderApplicationEntity requireApprovedProviderByEmail(String email) {
        return applications.findFirstByEmailIgnoreCaseAndStatusOrderByReviewedAtDesc(email, "APPROVED")
                .orElseThrow(ProviderAvailabilityService::notFound);
    }

    private void requireApprovedProviderById(String providerId) {
        ProviderApplicationEntity provider = applications.findById(providerId)
                .orElseThrow(ProviderAvailabilityService::notFound);
        if (!"APPROVED".equals(provider.getStatus())) {
            throw notFound();
        }
    }

    private static AvailabilitySlot toModel(ProviderAvailabilitySlotEntity slot) {
        return new AvailabilitySlot(slot.getId(), slot.getStartsAt(), slot.getEndsAt());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Approved provider or availability not found");
    }

    public record AvailabilitySlot(String id, Instant startsAt, Instant endsAt) {
    }
}

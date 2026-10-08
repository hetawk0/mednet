package com.mednet.provider.api;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mednet.provider.app.ProviderAvailabilityService;
import com.mednet.provider.app.ProviderAvailabilityService.AvailabilitySlot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api/v1/providers")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class ProviderAvailabilityController {

    private final ProviderAvailabilityService service;

    public ProviderAvailabilityController(ProviderAvailabilityService service) {
        this.service = service;
    }

    @GetMapping("/{providerId}/availability")
    public Page<AvailabilitySlot> availability(
            @PathVariable String providerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.available(providerId, page, size);
    }

    @PostMapping("/me/availability")
    @PreAuthorize("hasRole('PROVIDER')")
    public AvailabilitySlot create(
            @Valid @RequestBody CreateAvailabilityRequest request,
            Authentication actor) {
        return service.create(
                actor.getName(), request.startsAt(), request.endsAt(), request.consultationMode());
    }

    @DeleteMapping("/me/availability/{slotId}")
    @PreAuthorize("hasRole('PROVIDER')")
    public org.springframework.http.ResponseEntity<Void> close(
            @PathVariable String slotId,
            Authentication actor) {
        service.close(actor.getName(), slotId);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    public record CreateAvailabilityRequest(
            @NotNull @Future Instant startsAt,
            @NotNull @Future Instant endsAt,
            @Pattern(regexp = "(?i)IN_PERSON|TEXT") String consultationMode) {
    }
}

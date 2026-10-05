package com.mednet.appointment.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mednet.appointment.app.AppointmentService;
import com.mednet.appointment.app.AppointmentService.AppointmentDetails;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/appointments")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@PreAuthorize("hasAnyRole('PATIENT', 'PROVIDER')")
public class AppointmentController {

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @GetMapping
    public Page<AppointmentDetails> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication actor) {
        return service.list(actor.getName(), role(actor), page, size);
    }

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public AppointmentDetails request(
            @Valid @RequestBody CreateAppointmentRequest request,
            Authentication actor) {
        return service.request(actor.getName(), request.availabilitySlotId());
    }

    @GetMapping("/{id}")
    public AppointmentDetails get(@PathVariable String id, Authentication actor) {
        return service.get(actor.getName(), role(actor), id);
    }

    @PatchMapping("/{id}")
    public AppointmentDetails act(
            @PathVariable String id,
            @Valid @RequestBody AppointmentActionRequest request,
            Authentication actor) {
        return service.act(
                actor.getName(),
                role(actor),
                id,
                request.action(),
                request.proposedAvailabilitySlotId());
    }

    private static String role(Authentication actor) {
        boolean provider = actor.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> authority.equals("ROLE_PROVIDER"));
        return provider ? "PROVIDER" : "PATIENT";
    }

    public record CreateAppointmentRequest(
            @NotBlank @Size(max = 36) String availabilitySlotId) {
    }

    public record AppointmentActionRequest(
            @NotBlank @Pattern(regexp = "(?i)ACCEPT|DECLINE|CANCEL|REQUEST_RESCHEDULE|ACCEPT_RESCHEDULE|DECLINE_RESCHEDULE|COMPLETE")
            String action,
            @Size(max = 36) String proposedAvailabilitySlotId) {
    }
}

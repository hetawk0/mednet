package com.mednet.consultation.api;

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

import com.mednet.consultation.app.ConsultationService;
import com.mednet.consultation.app.ConsultationService.ConsultationDetails;
import com.mednet.consultation.app.ConsultationService.ConsultationMessageDetails;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@PreAuthorize("hasAnyRole('PATIENT', 'PROVIDER')")
public class ConsultationController {

    private final ConsultationService consultations;

    public ConsultationController(ConsultationService consultations) {
        this.consultations = consultations;
    }

    @PostMapping("/appointments/{appointmentId}/consultation")
    public ConsultationDetails open(
            @PathVariable @Size(max = 36) String appointmentId,
            Authentication actor) {
        return consultations.open(actor.getName(), role(actor), appointmentId);
    }

    @GetMapping("/consultations/{id}")
    public ConsultationDetails get(@PathVariable @Size(max = 36) String id, Authentication actor) {
        return consultations.get(actor.getName(), role(actor), id);
    }

    @GetMapping("/consultations/{id}/messages")
    public Page<ConsultationMessageDetails> messages(
            @PathVariable @Size(max = 36) String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication actor) {
        return consultations.messages(actor.getName(), role(actor), id, page, size);
    }

    @PostMapping("/consultations/{id}/messages")
    public ConsultationMessageDetails send(
            @PathVariable @Size(max = 36) String id,
            @Valid @RequestBody MessageRequest request,
            Authentication actor) {
        return consultations.send(actor.getName(), role(actor), id, request.body());
    }

    @PatchMapping("/consultations/{id}/end")
    @PreAuthorize("hasRole('PROVIDER')")
    public ConsultationDetails end(@PathVariable @Size(max = 36) String id, Authentication actor) {
        return consultations.end(actor.getName(), id);
    }

    private static String role(Authentication actor) {
        return actor.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse("");
    }

    public record MessageRequest(@NotBlank @Size(max = 4000) String body) {
    }
}

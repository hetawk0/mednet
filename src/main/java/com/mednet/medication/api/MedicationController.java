package com.mednet.medication.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mednet.medication.app.MedicationService;
import com.mednet.medication.app.MedicationService.DoseLogDetails;
import com.mednet.medication.app.MedicationService.MedicationDetails;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/patients/me/medications")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@PreAuthorize("hasRole('PATIENT')")
public class MedicationController {

    private final MedicationService service;

    public MedicationController(MedicationService service) {
        this.service = service;
    }

    @GetMapping
    public List<MedicationDetails> list(Authentication actor) {
        return service.list(actor.getName());
    }

    @PostMapping
    public MedicationDetails create(@Valid @RequestBody MedicationRequest request, Authentication actor) {
        return service.create(
                actor.getName(),
                request.medicationName(),
                request.dose(),
                request.reminderTime(),
                request.timeZone(),
                request.startDate(),
                request.endDate());
    }

    @PutMapping("/{id}")
    public MedicationDetails update(
            @PathVariable @Size(max = 36) String id,
            @Valid @RequestBody MedicationRequest request,
            Authentication actor) {
        return service.update(
                actor.getName(),
                id,
                request.medicationName(),
                request.dose(),
                request.reminderTime(),
                request.timeZone(),
                request.startDate(),
                request.endDate());
    }

    @PatchMapping("/{id}/stop")
    public MedicationDetails stop(@PathVariable @Size(max = 36) String id, Authentication actor) {
        return service.stop(actor.getName(), id);
    }

    @GetMapping("/{id}/dose-logs")
    public List<DoseLogDetails> doseLogs(
            @PathVariable @Size(max = 36) String id,
            Authentication actor) {
        return service.doseLogs(actor.getName(), id);
    }

    @PostMapping("/{id}/dose-logs")
    public DoseLogDetails logDose(
            @PathVariable @Size(max = 36) String id,
            Authentication actor) {
        return service.logDose(actor.getName(), id);
    }

    public record MedicationRequest(
            @NotBlank @Size(max = 160) String medicationName,
            @NotBlank @Size(max = 120) String dose,
            @NotNull LocalTime reminderTime,
            @NotBlank @Size(max = 80) String timeZone,
            @NotNull LocalDate startDate,
            LocalDate endDate) {
    }
}

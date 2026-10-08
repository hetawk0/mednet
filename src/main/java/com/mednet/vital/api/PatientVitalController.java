package com.mednet.vital.api;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mednet.vital.app.PatientVitalService;
import com.mednet.vital.app.PatientVitalService.PatientVitalDetails;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class PatientVitalController {

    private final PatientVitalService service;

    public PatientVitalController(PatientVitalService service) {
        this.service = service;
    }

    @GetMapping("/patients/me/vitals")
    @PreAuthorize("hasRole('PATIENT')")
    public Page<PatientVitalDetails> ownVitals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication actor) {
        return service.patientVitals(actor.getName(), page, size);
    }

    @PostMapping("/patients/me/vitals")
    @PreAuthorize("hasRole('PATIENT')")
    public PatientVitalDetails record(
            @Valid @RequestBody CreateVitalRequest request,
            Authentication actor) {
        return service.create(
                actor.getName(),
                request.metric(),
                request.value(),
                request.unit(),
                request.recordedAt());
    }

    @GetMapping("/patients/{patientAccountId}/vitals")
    @PreAuthorize("hasRole('PROVIDER')")
    public Page<PatientVitalDetails> providerVitals(
            @PathVariable @Size(max = 36) String patientAccountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication actor) {
        return service.providerVitals(actor.getName(), patientAccountId, page, size);
    }

    public record CreateVitalRequest(
            @NotBlank @Size(max = 80) String metric,
            @NotNull @DecimalMin("0.0") @Digits(integer = 9, fraction = 3) BigDecimal value,
            @NotBlank @Size(max = 40) String unit,
            @NotNull Instant recordedAt) {
    }
}

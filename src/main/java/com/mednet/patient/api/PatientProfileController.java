package com.mednet.patient.api;

import java.time.LocalDate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mednet.patient.app.PatientProfileService;
import com.mednet.patient.app.PatientProfileService.PatientProfile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/patients/me/profile")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@PreAuthorize("hasRole('PATIENT')")
public class PatientProfileController {

    private final PatientProfileService service;

    public PatientProfileController(PatientProfileService service) {
        this.service = service;
    }

    @GetMapping
    public PatientProfile getProfile(Authentication actor) {
        return service.get(actor.getName());
    }

    @PutMapping
    public PatientProfile updateProfile(
            @Valid @RequestBody UpdatePatientProfileRequest request,
            Authentication actor) {
        return service.put(
                actor.getName(),
                request.fullName(),
                request.dateOfBirth(),
                request.phoneNumber(),
                request.address());
    }

    public record UpdatePatientProfileRequest(
            @NotBlank @Size(max = 160) String fullName,
            @NotNull @PastOrPresent LocalDate dateOfBirth,
            @NotBlank @Size(max = 32) String phoneNumber,
            @NotBlank @Size(max = 500) String address) {
    }
}

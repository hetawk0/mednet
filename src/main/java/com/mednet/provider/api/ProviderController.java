package com.mednet.provider.api;

import java.util.List;

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

import com.mednet.appointment.app.AppointmentService;
import com.mednet.appointment.app.AppointmentService.AppointmentDetails;
import com.mednet.provider.app.ProviderService;
import com.mednet.provider.app.ProviderService.ProviderApplication;
import com.mednet.provider.app.ProviderService.ProviderDirectoryEntry;
import com.mednet.provider.app.ProviderService.ProviderPatientSummary;
import com.mednet.provider.app.ProviderService.SpecialtyCount;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/providers")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class ProviderController {

    private final ProviderService service;
    private final AppointmentService appointments;

    public ProviderController(ProviderService service, AppointmentService appointments) {
        this.service = service;
        this.appointments = appointments;
    }

    @GetMapping
    public Page<ProviderDirectoryEntry> directory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) String search) {
        return service.directory(page, size, specialty, search);
    }

    /** Approved providers per specialty across the whole directory (public). */
    @GetMapping("/specialties")
    public List<SpecialtyCount> specialties() {
        return service.specialties();
    }

    /** One approved provider's directory entry (public); 404 for anyone else. */
    @GetMapping("/{providerId}")
    public ProviderDirectoryEntry directoryEntry(@PathVariable @Size(max = 36) String providerId) {
        return service.directoryEntry(providerId);
    }

    @PostMapping("/applications")
    @PreAuthorize("hasAnyRole('PATIENT', 'PROVIDER')")
    public ProviderApplication apply(
            @Valid @RequestBody ProviderApplicationRequest request,
            Authentication actor) {
        return service.apply(
                actor.getName(),
                request.displayName(),
                request.specialty(),
                request.credentialReference());
    }

    @GetMapping("/me/application")
    @PreAuthorize("hasAnyRole('PATIENT', 'PROVIDER')")
    public ProviderApplication myApplication(Authentication actor) {
        return service.myApplication(actor.getName());
    }

    /** Active care patients only; the authenticated provider and relationship are resolved server-side. */
    @GetMapping("/me/patients")
    @PreAuthorize("hasRole('PROVIDER')")
    public Page<ProviderPatientSummary> patients(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication actor) {
        return service.patients(actor.getName(), page, size);
    }

    @GetMapping("/me/patients/{patientAccountId}")
    @PreAuthorize("hasRole('PROVIDER')")
    public ProviderPatientSummary patient(
            @PathVariable @Size(max = 36) String patientAccountId,
            Authentication actor) {
        return service.patient(actor.getName(), patientAccountId);
    }

    @GetMapping("/me/patients/{patientAccountId}/appointments")
    @PreAuthorize("hasRole('PROVIDER')")
    public Page<AppointmentDetails> patientAppointments(
            @PathVariable @Size(max = 36) String patientAccountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication actor) {
        return appointments.listProviderPatient(actor.getName(), patientAccountId, page, size);
    }

    public record ProviderApplicationRequest(
            @NotBlank @Size(max = 160) String displayName,
            @NotBlank @Size(max = 120) String specialty,
            @NotBlank @Size(max = 120) String credentialReference) {
    }
}

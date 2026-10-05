package com.mednet.record.api;

import java.time.Instant;
import java.util.List;

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

import com.mednet.record.app.PatientRecordService;
import com.mednet.record.app.PatientRecordService.RecordConsent;
import com.mednet.record.app.PatientRecordService.RecordDetails;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/patients")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class PatientRecordController {

    private final PatientRecordService service;

    public PatientRecordController(PatientRecordService service) {
        this.service = service;
    }

    @GetMapping("/me/record-consents")
    @PreAuthorize("hasRole('PATIENT')")
    public List<RecordConsent> consents(Authentication actor) {
        return service.consents(actor.getName());
    }

    @PostMapping("/me/record-consents")
    @PreAuthorize("hasRole('PATIENT')")
    public RecordConsent grantConsent(
            @Valid @RequestBody RecordConsentRequest request,
            Authentication actor) {
        return service.grantConsent(actor.getName(), request.providerId());
    }

    @DeleteMapping("/me/record-consents/{providerId}")
    @PreAuthorize("hasRole('PATIENT')")
    public RecordConsent revokeConsent(
            @PathVariable @Size(max = 36) String providerId,
            Authentication actor) {
        return service.revokeConsent(actor.getName(), providerId);
    }

    @GetMapping("/me/records")
    @PreAuthorize("hasRole('PATIENT')")
    public Page<RecordDetails> ownRecords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication actor) {
        return service.patientRecords(actor.getName(), page, size);
    }

    @GetMapping("/{patientAccountId}/records")
    @PreAuthorize("hasRole('PROVIDER')")
    public Page<RecordDetails> providerRecords(
            @PathVariable @Size(max = 36) String patientAccountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication actor) {
        return service.providerRecords(actor.getName(), patientAccountId, page, size);
    }

    @PostMapping("/{patientAccountId}/records")
    @PreAuthorize("hasRole('PROVIDER')")
    public RecordDetails addRecord(
            @PathVariable @Size(max = 36) String patientAccountId,
            @Valid @RequestBody CreateRecordRequest request,
            Authentication actor) {
        return service.addRecord(
                actor.getName(),
                patientAccountId,
                request.category(),
                request.title(),
                request.clinicalCode(),
                request.summary(),
                request.effectiveAt(),
                request.amendsRecordId());
    }

    public record RecordConsentRequest(
            @NotBlank @Size(max = 36) String providerId) {
    }

    public record CreateRecordRequest(
            @NotBlank @Pattern(regexp = "(?i)ENCOUNTER_SUMMARY|DIAGNOSIS|PRESCRIPTION|ALLERGY|MEDICAL_HISTORY|DOCUMENT")
            String category,
            @NotBlank @Size(max = 160) String title,
            @Size(max = 80) String clinicalCode,
            @NotBlank @Size(max = 10000) String summary,
            @NotNull Instant effectiveAt,
            @Size(max = 36) String amendsRecordId) {
    }
}

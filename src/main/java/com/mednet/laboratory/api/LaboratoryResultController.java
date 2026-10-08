package com.mednet.laboratory.api;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mednet.laboratory.app.LaboratoryResultService;
import com.mednet.laboratory.app.LaboratoryResultService.LaboratoryResultDetails;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class LaboratoryResultController {

    private final LaboratoryResultService service;

    public LaboratoryResultController(LaboratoryResultService service) {
        this.service = service;
    }

    @PostMapping("/partner/service-requests/{requestId}/lab-result")
    @PreAuthorize("hasRole('LABORATORY')")
    public LaboratoryResultDetails submit(
            @PathVariable @Size(max = 36) String requestId,
            @Valid @RequestBody SubmitResultRequest request,
            Authentication actor) {
        return service.submit(actor.getName(), requestId, request.summary());
    }

    @GetMapping("/patients/me/lab-results")
    @PreAuthorize("hasRole('PATIENT')")
    public List<LaboratoryResultDetails> patientResults(Authentication actor) {
        return service.patientResults(actor.getName());
    }

    @GetMapping("/patients/me/lab-results/{resultId}")
    @PreAuthorize("hasRole('PATIENT')")
    public LaboratoryResultDetails patientResult(
            @PathVariable @Size(max = 36) String resultId,
            Authentication actor) {
        return service.patientResult(actor.getName(), resultId);
    }

    @GetMapping("/patients/{patientAccountId}/lab-results/pending")
    @PreAuthorize("hasRole('PROVIDER')")
    public List<LaboratoryResultDetails> pendingForProvider(
            @PathVariable @Size(max = 36) String patientAccountId,
            Authentication actor) {
        return service.pendingForProvider(actor.getName(), patientAccountId);
    }

    @PatchMapping("/lab-results/{resultId}/review")
    @PreAuthorize("hasRole('PROVIDER')")
    public LaboratoryResultDetails review(
            @PathVariable @Size(max = 36) String resultId,
            @Valid @RequestBody ReviewResultRequest request,
            Authentication actor) {
        return service.review(actor.getName(), resultId, request.action());
    }

    public record SubmitResultRequest(@NotBlank @Size(max = 9700) String summary) {
    }

    public record ReviewResultRequest(@NotBlank @Pattern(regexp = "(?i)RELEASE|RETURN") String action) {
    }
}

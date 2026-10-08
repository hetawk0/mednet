package com.mednet.service.api;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mednet.service.app.PatientServiceRequestService;
import com.mednet.service.app.PatientServiceRequestService.PartnerRequestDetails;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/partner/service-requests")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@PreAuthorize("hasAnyRole('LABORATORY', 'HOME_CARE')")
public class PartnerServiceRequestController {

    private final PatientServiceRequestService service;

    public PartnerServiceRequestController(PatientServiceRequestService service) {
        this.service = service;
    }

    @GetMapping
    public List<PartnerRequestDetails> assignedRequests(
            @RequestParam String type,
            Authentication actor) {
        return service.listAssigned(actor.getName(), type);
    }

    @PatchMapping("/{id}/status")
    public PartnerRequestDetails updateStatus(
            @PathVariable @Size(max = 36) String id,
            @Valid @RequestBody StatusRequest request,
            Authentication actor) {
        return service.updateAssignedStatus(actor.getName(), id, request.status());
    }

    public record StatusRequest(
            @NotBlank @Pattern(regexp = "(?i)IN_PROGRESS|RESOLVED") String status) {
    }
}

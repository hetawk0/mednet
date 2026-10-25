package com.mednet.service.api;

import java.util.List;

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

import com.mednet.service.app.PatientServiceRequestService;
import com.mednet.service.app.PatientServiceRequestService.PatientServiceRequestDetails;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@PreAuthorize("hasRole('PATIENT')")
public class PatientServiceRequestController {

    private final PatientServiceRequestService service;

    public PatientServiceRequestController(PatientServiceRequestService service) {
        this.service = service;
    }

    @GetMapping("/home-care-requests")
    public List<PatientServiceRequestDetails> homeCareRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Authentication actor) {
        return service.listPatient(actor.getName(), "HOME_CARE", status, search);
    }

    @GetMapping(value = "/home-care-requests", params = "page")
    public Page<PatientServiceRequestDetails> homeCareRequestsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Authentication actor) {
        return service.pagePatient(actor.getName(), "HOME_CARE", status, search, page, size);
    }

    @PostMapping("/home-care-requests")
    public PatientServiceRequestDetails requestHomeCare(
            @Valid @RequestBody HomeCareRequest request,
            Authentication actor) {
        return service.create(actor.getName(), "HOME_CARE", request.serviceDescription(), request.locationDescription());
    }

    @PatchMapping("/home-care-requests/{id}/cancel")
    public PatientServiceRequestDetails cancelHomeCare(
            @PathVariable @Size(max = 36) String id,
            Authentication actor) {
        return service.cancel(actor.getName(), "HOME_CARE", id);
    }

    @GetMapping("/lab-requests")
    public List<PatientServiceRequestDetails> labRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Authentication actor) {
        return service.listPatient(actor.getName(), "LABORATORY", status, search);
    }

    @GetMapping(value = "/lab-requests", params = "page")
    public Page<PatientServiceRequestDetails> labRequestsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Authentication actor) {
        return service.pagePatient(actor.getName(), "LABORATORY", status, search, page, size);
    }

    @PostMapping("/lab-requests")
    public PatientServiceRequestDetails requestLab(
            @Valid @RequestBody LabRequest request,
            Authentication actor) {
        return service.create(actor.getName(), "LABORATORY", request.testDescription(), null);
    }

    @PatchMapping("/lab-requests/{id}/cancel")
    public PatientServiceRequestDetails cancelLab(
            @PathVariable @Size(max = 36) String id,
            Authentication actor) {
        return service.cancel(actor.getName(), "LABORATORY", id);
    }

    public record HomeCareRequest(
            @NotBlank @Size(max = 240) String serviceDescription,
            @NotBlank @Size(max = 500) String locationDescription) {
    }

    public record LabRequest(@NotBlank @Size(max = 240) String testDescription) {
    }
}

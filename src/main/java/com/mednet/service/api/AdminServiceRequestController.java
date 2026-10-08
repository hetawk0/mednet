package com.mednet.service.api;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mednet.service.app.PatientServiceRequestService;
import com.mednet.service.app.PatientServiceRequestService.AdminRequestSummary;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api/v1/admin/service-requests")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class AdminServiceRequestController {

    private final PatientServiceRequestService service;

    public AdminServiceRequestController(PatientServiceRequestService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminRequestSummary> requests(@RequestParam String type, Authentication actor) {
        return service.listForAdministrator(type, actorEmail(actor));
    }

    @PatchMapping("/{id}/status")
    public AdminRequestSummary updateStatus(
            @PathVariable String id,
            @Valid @RequestBody StatusRequest request,
            Authentication actor) {
        return service.updateStatus(id, request.status(), actorEmail(actor));
    }

    @PatchMapping("/{id}/assignment")
    public PatientServiceRequestService.PartnerAssignment assignStaff(
            @PathVariable String id,
            @Valid @RequestBody AssignmentRequest request,
            Authentication actor) {
        return service.assignStaff(id, request.staffAccountId(), actorEmail(actor));
    }

    private static String actorEmail(Authentication actor) {
        if (actor.getPrincipal() instanceof OAuth2User user) {
            String email = user.getAttribute("email");
            if (email != null && !email.isBlank()) {
                return email;
            }
        }
        return actor.getName();
    }

    public record StatusRequest(
            @NotBlank @Pattern(regexp = "(?i)IN_PROGRESS|CANCELLED") String status) {
    }

    public record AssignmentRequest(@NotBlank @org.hibernate.validator.constraints.UUID String staffAccountId) {
    }
}

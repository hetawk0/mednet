package com.mednet.admin.api;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.mednet.admin.app.AdminWorkflowService;
import com.mednet.admin.data.AdminWorkflowModels.AdminAuditEvent;
import com.mednet.admin.data.AdminWorkflowModels.PlatformAccount;
import com.mednet.admin.data.AdminWorkflowModels.ProviderApplication;
import com.mednet.admin.data.AdminWorkflowModels.ServiceRequest;

@RestController
@RequestMapping("/api/v1/admin")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class AdminWorkflowController {

    private final AdminWorkflowService service;

    public AdminWorkflowController(AdminWorkflowService service) {
        this.service = service;
    }

    @GetMapping("/providers")
    public List<ProviderApplication> providers(@RequestParam(required = false) String status) {
        return service.providers(status);
    }

    @PostMapping("/providers")
    public ProviderApplication createProvider(
            @Valid @RequestBody CreateProviderRequest request, Authentication actor) {
        return service.createProvider(
                request.displayName(), request.email(), request.specialty(), request.credentialReference(),
                actor.getName());
    }

    @PatchMapping("/providers/{id}/status")
    public ProviderApplication reviewProvider(
            @PathVariable String id, @Valid @RequestBody StatusRequest request, Authentication actor) {
        return service.reviewProvider(id, request.status(), actor.getName());
    }

    @GetMapping("/accounts")
    public List<PlatformAccount> accounts() {
        return service.accounts();
    }

    @PostMapping("/accounts")
    public PlatformAccount createAccount(@Valid @RequestBody CreateAccountRequest request, Authentication actor) {
        return service.createAccount(request.email(), request.accountType(), actor.getName());
    }

    @PatchMapping("/accounts/{id}/status")
    public PlatformAccount changeAccountStatus(
            @PathVariable String id, @Valid @RequestBody StatusRequest request, Authentication actor) {
        return service.changeAccountStatus(id, request.status(), actor.getName());
    }

    @GetMapping("/requests")
    public List<ServiceRequest> requests(@RequestParam(required = false) String status) {
        return service.requests(status);
    }

    @PostMapping("/requests")
    public ServiceRequest createRequest(@Valid @RequestBody CreateRequest request, Authentication actor) {
        return service.createRequest(
                request.referenceId(), request.requestType(), request.requesterEmail(), actor.getName());
    }

    @PatchMapping("/requests/{id}/status")
    public ServiceRequest changeRequestStatus(
            @PathVariable String id, @Valid @RequestBody StatusRequest request, Authentication actor) {
        return service.changeRequestStatus(id, request.status(), actor.getName());
    }

    @GetMapping("/audit")
    public List<AdminAuditEvent> auditEvents() {
        return service.audit();
    }

    public record CreateProviderRequest(
            @NotBlank @Size(max = 160) String displayName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 120) String specialty,
            @NotBlank @Size(max = 120) String credentialReference) {
    }

    public record CreateAccountRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank String accountType) {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 80) String referenceId,
            @NotBlank String requestType,
            @NotBlank @Email @Size(max = 254) String requesterEmail) {
    }

    public record StatusRequest(@NotBlank String status) {
    }
}
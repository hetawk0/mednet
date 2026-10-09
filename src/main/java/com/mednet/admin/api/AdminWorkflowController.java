package com.mednet.admin.api;

import java.util.List;
import org.springframework.data.domain.Page;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.mednet.admin.app.AdminWorkflowService;
import com.mednet.admin.data.AdminWorkflowModels.AdminAuditEvent;
import com.mednet.admin.data.AdminWorkflowModels.PlatformAccount;
import com.mednet.admin.data.AdminWorkflowModels.ProviderApplication;
import com.mednet.admin.data.AdminWorkflowModels.ServiceRequest;
import com.mednet.appointment.app.AppointmentService;
import com.mednet.appointment.app.AppointmentService.AdminAppointmentSummary;

@RestController
@RequestMapping("/api/v1/admin")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class AdminWorkflowController {

    private final AdminWorkflowService service;
    private final AppointmentService appointments;

    public AdminWorkflowController(AdminWorkflowService service, AppointmentService appointments) {
        this.service = service;
        this.appointments = appointments;
    }

    @GetMapping("/appointments")
    public Page<AdminAppointmentSummary> appointments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication actor) {
        return appointments.adminList(actorEmail(actor), page, size);
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
                actorEmail(actor));
    }

    @PatchMapping("/providers/{id}/status")
    public ProviderApplication reviewProvider(
            @PathVariable String id, @Valid @RequestBody StatusRequest request, Authentication actor) {
        return service.reviewProvider(id, request.status(), actorEmail(actor));
    }

    @GetMapping("/accounts")
    public Page<PlatformAccount> accounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) @Size(max = 254) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String accountType,
            @RequestParam(required = false) Boolean emailVerified) {
        return service.accounts(page, size, search, status, accountType, emailVerified);
    }

    @PostMapping("/accounts")
    public PlatformAccount createAccount(
            @Valid @RequestBody CreateAccountRequest request, Authentication actor) {
        return service.createAccount(
                request.displayName(),
                request.email(),
                request.accountType(),
                request.password(),
                actorEmail(actor));
    }

    @PutMapping("/accounts/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPER_ADMIN')")
    public PlatformAccount updateAccount(
            @PathVariable String id, @Valid @RequestBody UpdateAccountRequest request, Authentication actor) {
        return service.updateAccount(
                id, request.displayName(), request.email(), request.accountType(), request.password(),
                actorEmail(actor));
    }

    @PatchMapping("/accounts/{id}/status")
    public PlatformAccount changeAccountStatus(
            @PathVariable String id, @Valid @RequestBody StatusRequest request, Authentication actor) {
        return service.changeAccountStatus(id, request.status(), actorEmail(actor));
    }

    @PatchMapping("/accounts/{id}/role")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPER_ADMIN')")
    public PlatformAccount changeAccountRole(
            @PathVariable String id, @Valid @RequestBody RoleRequest request, Authentication actor) {
        return service.changeAccountType(id, request.accountType(), actorEmail(actor));
    }

    @PatchMapping("/accounts/{id}/verification")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPER_ADMIN')")
    public PlatformAccount changeAccountVerification(
            @PathVariable String id, @Valid @RequestBody VerificationRequest request, Authentication actor) {
        return service.changeAccountVerification(id, request.emailVerified(), actorEmail(actor));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/accounts/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('SUPER_ADMIN')")
    public org.springframework.http.ResponseEntity<Void> deleteAccount(
            @PathVariable String id, Authentication actor) {
        service.deleteAccount(id, actorEmail(actor));
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @GetMapping("/requests")
    public List<ServiceRequest> requests(@RequestParam(required = false) String status) {
        return service.requests(status);
    }

    @PostMapping("/requests")
    public ServiceRequest createRequest(@Valid @RequestBody CreateRequest request, Authentication actor) {
        return service.createRequest(
                request.referenceId(), request.requestType(), request.requesterEmail(), actorEmail(actor));
    }

    @PatchMapping("/requests/{id}/status")
    public ServiceRequest changeRequestStatus(
            @PathVariable String id, @Valid @RequestBody StatusRequest request, Authentication actor) {
        return service.changeRequestStatus(id, request.status(), actorEmail(actor));
    }

    @GetMapping("/audit")
    public List<AdminAuditEvent> auditEvents() {
        return service.audit();
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

    public record CreateProviderRequest(
            @NotBlank @Size(max = 160) String displayName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 120) String specialty,
            @NotBlank @Size(max = 120) String credentialReference) {
    }

    public record CreateAccountRequest(
            @NotBlank @Size(max = 160) String displayName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank String accountType,
            @NotBlank @Size(min = 12, max = 72) String password) {
    }

    public record UpdateAccountRequest(
            @NotBlank @Size(max = 160) String displayName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank String accountType,
            @Size(min = 12, max = 72) String password) {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 80) String referenceId,
            @NotBlank String requestType,
            @NotBlank @Email @Size(max = 254) String requesterEmail) {
    }

    public record StatusRequest(@NotBlank String status) {
    }

    public record RoleRequest(@NotBlank String accountType) {
    }

    public record VerificationRequest(@NotNull Boolean emailVerified) {
    }
}
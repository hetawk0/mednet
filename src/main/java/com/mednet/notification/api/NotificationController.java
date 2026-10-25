package com.mednet.notification.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.mednet.notification.app.NotificationService;
import com.mednet.notification.app.NotificationService.NotificationDetails;
import com.mednet.notification.app.NotificationService.NotificationPreferences;

@RestController
@RequestMapping("/api/v1/notifications")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@PreAuthorize("hasAnyRole('PATIENT', 'PROVIDER', 'ADMIN', 'SUPER_ADMIN', 'HOME_CARE', 'LABORATORY')")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public Page<NotificationDetails> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication actor) {
        return service.list(actor.getName(), page, size);
    }

    @GetMapping("/unread-count")
    public long unreadCount(Authentication actor) {
        return service.unreadCount(actor.getName());
    }

    @PatchMapping("/{id}/read")
    public NotificationDetails markRead(@PathVariable String id, Authentication actor) {
        return service.markRead(actor.getName(), id);
    }

    @GetMapping("/preferences")
    public NotificationPreferences preferences(Authentication actor) {
        return service.preferences(actor.getName());
    }

    @org.springframework.web.bind.annotation.PatchMapping("/preferences")
    public NotificationPreferences updatePreferences(
            @Valid @RequestBody NotificationPreferencesRequest request,
            Authentication actor) {
        return service.updateEmailPreference(actor.getName(), request.emailEnabled());
    }

    public record NotificationPreferencesRequest(@NotNull Boolean emailEnabled) {
    }
}

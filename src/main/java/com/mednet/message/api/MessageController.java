package com.mednet.message.api;

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

import com.mednet.message.app.MessageService;
import com.mednet.message.app.MessageService.ConversationDetails;
import com.mednet.message.app.MessageService.MessageDetails;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@PreAuthorize("hasAnyRole('PATIENT', 'PROVIDER')")
public class MessageController {

    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    @GetMapping("/conversations")
    public List<ConversationDetails> conversations(Authentication actor) {
        return service.list(actor.getName(), role(actor));
    }

    @PostMapping("/conversations")
    @PreAuthorize("hasRole('PATIENT')")
    public ConversationDetails open(
            @Valid @RequestBody OpenConversationRequest request,
            Authentication actor) {
        return service.open(actor.getName(), request.providerId());
    }

    @GetMapping("/conversations/{id}/messages")
    public Page<MessageDetails> messages(
            @PathVariable @Size(max = 36) String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication actor) {
        return service.messages(actor.getName(), role(actor), id, page, size);
    }

    @PostMapping("/conversations/{id}/messages")
    public MessageDetails send(
            @PathVariable @Size(max = 36) String id,
            @Valid @RequestBody SendMessageRequest request,
            Authentication actor) {
        return service.send(actor.getName(), role(actor), id, request.body());
    }

    private static String role(Authentication actor) {
        return actor.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_PROVIDER".equals(authority.getAuthority()))
                        ? "PROVIDER"
                        : "PATIENT";
    }

    public record OpenConversationRequest(@NotBlank @Size(max = 36) String providerId) {
    }

    public record SendMessageRequest(@NotBlank @Size(max = 4000) String body) {
    }
}

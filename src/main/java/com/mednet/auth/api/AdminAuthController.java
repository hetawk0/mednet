package com.mednet.auth.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AdminAuthController {

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    @GetMapping("/admin/session")
    public AdminSession session(Authentication authentication) {
        return new AdminSession(authentication.getName(), "ADMIN");
    }

    public record CsrfResponse(String headerName, String token) {}

    public record AdminSession(String email, String role) {}
}
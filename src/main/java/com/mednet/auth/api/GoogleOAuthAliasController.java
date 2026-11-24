package com.mednet.auth.api;

import java.io.IOException;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Bridges documented/proxied OAuth URLs under {@code /api/v1/auth/oauth2/…} to Spring Security's
 * default endpoints. Google may register the callback on the API prefix when the reverse proxy
 * routes {@code /api/*} to this service.
 */
@Controller
public class GoogleOAuthAliasController {

    @GetMapping("/api/v1/auth/oauth2/callback/google")
    public void legacyCallback(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String query = request.getQueryString();
        String target = "/login/oauth2/code/google";
        if (query != null && !query.isBlank()) {
            target += "?" + query;
        }
        response.sendRedirect(target);
    }

    @GetMapping("/api/v1/auth/oauth2/authorization/google")
    public void legacyAuthorization(HttpServletResponse response) throws IOException {
        response.sendRedirect("/oauth2/authorization/google");
    }
}
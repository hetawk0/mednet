package com.mednet.auth.api;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;

@RestController
@RequestMapping("/api/v1/auth")
public class AdminAuthController {

    private final String googleClientId;
    private final String googleClientSecret;
    private final String datasourceUrl;
    private final ObjectProvider<PlatformAccountRepository> accounts;

    public AdminAuthController(
            @Value("${GOOGLE_CLIENT_ID:}") String googleClientId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String googleClientSecret,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            ObjectProvider<PlatformAccountRepository> accounts) {
        this.googleClientId = googleClientId;
        this.googleClientSecret = googleClientSecret;
        this.datasourceUrl = datasourceUrl;
        this.accounts = accounts;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    @GetMapping("/google/status")
    public GoogleStatus googleStatus() {
        return new GoogleStatus(
                !googleClientId.isBlank() && !googleClientSecret.isBlank() && !datasourceUrl.isBlank());
    }

    @GetMapping("/admin/session")
    public AdminSession session(Authentication authentication) {
        return new AdminSession(authentication.getName(), role(authentication));
    }

    /**
     * The signed-in user's email and effective role. {@code accountType} is the stored account type
     * (null when no account row exists or the API runs without a database). A PROVIDER account
     * type without the PROVIDER role is an applicant awaiting approval; clients may use it to route to
     * onboarding, but only {@code role} grants access.
     */
    @GetMapping("/session")
    public UserSession userSession(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign-in required");
        }
        PlatformAccountRepository repository = accounts.getIfAvailable();
        String accountType = repository == null
                ? null
                : repository.findFirstByEmailIgnoreCase(authentication.getName())
                        .map(PlatformAccountEntity::getAccountType)
                        .orElse(null);
        return new UserSession(authentication.getName(), role(authentication), accountType);
    }

    private static String role(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .filter(authority -> !authority.equals("USER"))
                .findFirst()
                .orElse("USER");
    }

    public record CsrfResponse(String headerName, String token) {
    }

    public record GoogleStatus(boolean enabled) {
    }

    public record AdminSession(String email, String role) {
    }

    public record UserSession(String email, String role, String accountType) {
    }

}
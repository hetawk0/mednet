package com.mednet.auth.security;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.provider.data.ProviderApplicationRepository;

public class GoogleOAuth2UserService {

    private final PlatformAccountRepository accounts;
    private final ProviderApplicationRepository providers;
    private final String adminEmail;

    public GoogleOAuth2UserService(
            PlatformAccountRepository accounts,
            ProviderApplicationRepository providers,
            String adminEmail) {
        this.accounts = accounts;
        this.providers = providers;
        this.adminEmail = normalize(adminEmail);
    }

    @Transactional
    public OidcUser applyMedNetRole(OidcUser googleUser) {
        String email = googleUser.getAttribute("email");
        String subject = googleUser.getSubject();
        Boolean emailVerified = googleUser.getAttribute("email_verified");
        if (!StringUtils.hasText(email) || !StringUtils.hasText(subject) || !Boolean.TRUE.equals(emailVerified)) {
            throw authenticationError("Google must provide a verified email address");
        }

        String normalizedEmail = normalize(email);
        PlatformAccountEntity account = accounts.findFirstByGoogleSubject(subject).orElse(null);
        if (account != null && !account.getEmail().equalsIgnoreCase(normalizedEmail)) {
            throw authenticationError("This Google identity is linked to a different MedNet email address");
        }
        if (account == null) {
            account = accounts.findFirstByEmailIgnoreCase(normalizedEmail).orElse(null);
            if (account == null) {
                if (!adminEmail.isBlank() && adminEmail.equals(normalizedEmail)) {
                    return withRole(googleUser, "SUPER_ADMIN", normalizedEmail);
                }
                throw authenticationError(
                        "No MedNet account is linked to this Google email. Create an account first.");
            }
        }
        if ("SUSPENDED".equals(account.getStatus())) {
            throw authenticationError("This MedNet account is suspended");
        }
        if (StringUtils.hasText(account.getGoogleSubject())
                && !account.getGoogleSubject().equals(subject)) {
            throw authenticationError("This MedNet account is linked to another Google identity");
        }

        if (!account.isEmailVerified()) {
            if (account.getPasswordHash() != null) {
                account.setPasswordHash(null);
            }
            account.verifyEmail();
        }
        account.linkGoogleSubject(subject);
        try {
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            throw authenticationError("This Google identity could not be linked to the MedNet account");
        }

        String role = resolveRole(account, normalizedEmail);
        return withRole(googleUser, role, normalizedEmail);
    }

    private static OidcUser withRole(OidcUser googleUser, String role, String normalizedEmail) {
        Set<GrantedAuthority> authorities = new HashSet<>(googleUser.getAuthorities());
        authorities.removeIf(authority -> authority.getAuthority().equals("ROLE_USER"));
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        Map<String, Object> userInfoClaims = new HashMap<>(googleUser.getUserInfo().getClaims());
        userInfoClaims.put("mednet_email", normalizedEmail);
        return new DefaultOidcUser(
                authorities,
                googleUser.getIdToken(),
                new OidcUserInfo(userInfoClaims),
                "mednet_email");
    }

    private String resolveRole(PlatformAccountEntity account, String email) {
        if (!adminEmail.isBlank() && adminEmail.equals(email)) {
            return "SUPER_ADMIN";
        }
        return switch (account.getAccountType()) {
            case "ADMIN", "SUPER_ADMIN", "LABORATORY", "HOME_CARE" -> account.getAccountType();
            case "PATIENT", "PROVIDER" -> providers
                            .findFirstByEmailIgnoreCaseAndStatusOrderByReviewedAtDesc(email, "APPROVED")
                            .isPresent()
                    ? "PROVIDER"
                    : "PATIENT";
            default -> throw authenticationError("This MedNet account cannot sign in with Google");
        };
    }

    private static OAuth2AuthenticationException authenticationError(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("mednet_authentication_error"), message);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}

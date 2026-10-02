package com.mednet.auth.security;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.admin.data.ProviderApplicationRepository;

public class GoogleOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final PlatformAccountRepository accounts;
    private final ProviderApplicationRepository providers;
    private final String adminEmail;

    public GoogleOAuth2UserService(
            PlatformAccountRepository accounts,
            ProviderApplicationRepository providers,
            @Value("${mednet.admin.email:}") String adminEmail) {
        this.accounts = accounts;
        this.providers = providers;
        this.adminEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User googleUser = delegate.loadUser(request);
        String email = googleUser.getAttribute("email");
        String subject = googleUser.getAttribute("sub");
        Boolean emailVerified = googleUser.getAttribute("email_verified");
        if (!StringUtils.hasText(email) || !StringUtils.hasText(subject) || !Boolean.TRUE.equals(emailVerified)) {
            throw authenticationError("Google must provide a verified email address");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String role;
        if (!adminEmail.isBlank() && adminEmail.equals(normalizedEmail)) {
            role = "ADMIN";
        } else {
            role = providers.findFirstByEmailIgnoreCaseAndStatusOrderByReviewedAtDesc(
                    normalizedEmail, "APPROVED").isPresent() ? "PROVIDER" : "PATIENT";
            linkAccount(normalizedEmail, subject, role);
        }

        Set<GrantedAuthority> authorities = new HashSet<>(googleUser.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        return new DefaultOAuth2User(authorities, googleUser.getAttributes(), "sub");
    }

    private void linkAccount(String email, String subject, String role) {
        PlatformAccountEntity subjectAccount = accounts.findFirstByGoogleSubject(subject).orElse(null);
        if (subjectAccount != null && !subjectAccount.getEmail().equalsIgnoreCase(email)) {
            throw authenticationError("Google identity is already linked to another account");
        }

        PlatformAccountEntity account = accounts.findFirstByEmailIgnoreCase(email)
                .orElseGet(() -> new PlatformAccountEntity(UUID.randomUUID().toString(), email, role));
        if ("SUSPENDED".equals(account.getStatus())) {
            throw authenticationError("This MedNet account is suspended");
        }
        if (StringUtils.hasText(account.getGoogleSubject()) && !account.getGoogleSubject().equals(subject)) {
            throw authenticationError("This MedNet account is linked to another Google identity");
        }

        account.linkGoogleSubject(subject);
        account.changeAccountType(role);
        try {
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            throw authenticationError("Google identity could not be linked to this account");
        }
    }

    private static OAuth2AuthenticationException authenticationError(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("mednet_authentication_error"), message);
    }
}
package com.mednet.auth.security;

import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.provider.data.ProviderApplicationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleOAuth2UserServiceTests {

    @Test
    void linksVerifiedGoogleIdentityToExistingAccountAndUsesItsMedNetRole() {
        PlatformAccountRepository accounts = mock(PlatformAccountRepository.class);
        ProviderApplicationRepository providers = mock(ProviderApplicationRepository.class);
        PlatformAccountEntity account = new PlatformAccountEntity("account-id", "patient@example.test", "PATIENT");
        account.setEmailVerified(true);
        when(accounts.findFirstByGoogleSubject("google-subject")).thenReturn(Optional.empty());
        when(accounts.findFirstByEmailIgnoreCase("patient@example.test")).thenReturn(Optional.of(account));
        when(accounts.saveAndFlush(account)).thenReturn(account);
        when(providers.findFirstByEmailIgnoreCaseAndStatusOrderByReviewedAtDesc(
                "patient@example.test", "APPROVED")).thenReturn(Optional.empty());

        OidcUser linked = service(accounts, providers).applyMedNetRole(googleUser(
                "patient@example.test", "google-subject", true));

        assertThat(linked.getName()).isEqualTo("patient@example.test");
        assertThat(linked.getAuthorities())
                .extracting("authority")
                .contains("ROLE_PATIENT")
                .doesNotContain("ROLE_USER");
        assertThat(account.getGoogleSubject()).isEqualTo("google-subject");
        verify(accounts).saveAndFlush(account);
    }

    @Test
    void normalizesGooglePrincipalNameForSharedSessionInvalidation() {
        PlatformAccountRepository accounts = mock(PlatformAccountRepository.class);
        ProviderApplicationRepository providers = mock(ProviderApplicationRepository.class);
        PlatformAccountEntity account = new PlatformAccountEntity("account-id", "patient@example.test", "PATIENT");
        account.setEmailVerified(true);
        when(accounts.findFirstByGoogleSubject("google-subject")).thenReturn(Optional.empty());
        when(accounts.findFirstByEmailIgnoreCase("patient@example.test")).thenReturn(Optional.of(account));
        when(accounts.saveAndFlush(account)).thenReturn(account);
        when(providers.findFirstByEmailIgnoreCaseAndStatusOrderByReviewedAtDesc(
                "patient@example.test", "APPROVED")).thenReturn(Optional.empty());

        OidcUser linked = service(accounts, providers).applyMedNetRole(googleUser(
                "Patient@Example.TEST", "google-subject", true));

        assertThat(linked.getName()).isEqualTo("patient@example.test");
    }

    @Test
    void verifiedGoogleEmailConfirmsPendingAccountAndRemovesUnconfirmedPassword() {
        PlatformAccountRepository accounts = mock(PlatformAccountRepository.class);
        ProviderApplicationRepository providers = mock(ProviderApplicationRepository.class);
        PlatformAccountEntity account = new PlatformAccountEntity("account-id", "patient@example.test", "PATIENT");
        account.setPasswordHash("unconfirmed-password-hash");
        account.setVerificationToken("verification-hash", java.time.Instant.now().plusSeconds(600));
        when(accounts.findFirstByGoogleSubject("google-subject")).thenReturn(Optional.empty());
        when(accounts.findFirstByEmailIgnoreCase("patient@example.test")).thenReturn(Optional.of(account));
        when(accounts.saveAndFlush(account)).thenReturn(account);
        when(providers.findFirstByEmailIgnoreCaseAndStatusOrderByReviewedAtDesc(
                "patient@example.test", "APPROVED")).thenReturn(Optional.empty());

        service(accounts, providers).applyMedNetRole(googleUser(
                "patient@example.test", "google-subject", true));

        assertThat(account.isEmailVerified()).isTrue();
        assertThat(account.getPasswordHash()).isNull();
        assertThat(account.getVerificationTokenHash()).isNull();
        verify(accounts).saveAndFlush(account);
    }

    @Test
    void rejectsUnverifiedGoogleEmailsAndUnknownAccounts() {
        PlatformAccountRepository accounts = mock(PlatformAccountRepository.class);
        ProviderApplicationRepository providers = mock(ProviderApplicationRepository.class);
        when(accounts.findFirstByGoogleSubject("google-subject")).thenReturn(Optional.empty());
        when(accounts.findFirstByEmailIgnoreCase("patient@example.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(accounts, providers)
                .applyMedNetRole(googleUser("patient@example.test", "google-subject", false)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("verified email");
        assertThatThrownBy(() -> service(accounts, providers)
                .applyMedNetRole(googleUser("patient@example.test", "google-subject", true)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Create an account first");
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSuspendedAccountsAndGoogleIdentityEmailMismatches() {
        PlatformAccountRepository accounts = mock(PlatformAccountRepository.class);
        ProviderApplicationRepository providers = mock(ProviderApplicationRepository.class);
        PlatformAccountEntity suspended = new PlatformAccountEntity("account-id", "patient@example.test", "PATIENT");
        suspended.changeStatus("SUSPENDED");
        when(accounts.findFirstByGoogleSubject("google-subject")).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> service(accounts, providers)
                .applyMedNetRole(googleUser("patient@example.test", "google-subject", true)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("suspended");
        assertThatThrownBy(() -> service(accounts, providers)
                .applyMedNetRole(googleUser("different@example.test", "google-subject", true)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("different MedNet email");
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void mapsConfiguredAdministratorEmailToSuperAdminWithoutProvisioningAnAccount() {
        PlatformAccountRepository accounts = mock(PlatformAccountRepository.class);
        ProviderApplicationRepository providers = mock(ProviderApplicationRepository.class);
        when(accounts.findFirstByGoogleSubject("google-subject")).thenReturn(Optional.empty());
        when(accounts.findFirstByEmailIgnoreCase("admin@example.test")).thenReturn(Optional.empty());

        OidcUser admin = service(accounts, providers, "admin@example.test")
                .applyMedNetRole(googleUser("admin@example.test", "google-subject", true));

        assertThat(admin.getAuthorities()).extracting("authority").contains("ROLE_SUPER_ADMIN");
        verify(accounts, never()).saveAndFlush(any());
    }

    private static GoogleOAuth2UserService service(
            PlatformAccountRepository accounts, ProviderApplicationRepository providers) {
        return service(accounts, providers, "");
    }

    private static GoogleOAuth2UserService service(
            PlatformAccountRepository accounts, ProviderApplicationRepository providers, String adminEmail) {
        return new GoogleOAuth2UserService(accounts, providers, adminEmail);
    }

    private static OidcUser googleUser(String email, String subject, boolean verified) {
        OidcUser user = mock(OidcUser.class);
        Map<String, Object> claims = Map.of(
                "iss", "https://accounts.google.com",
                "sub", subject,
                "email", email,
                "email_verified", verified);
        when(user.getAttribute("email")).thenReturn(email);
        when(user.getAttribute("email_verified")).thenReturn(verified);
        when(user.getSubject()).thenReturn(subject);
        doReturn(Set.of(new SimpleGrantedAuthority("ROLE_USER"))).when(user).getAuthorities();
        when(user.getIdToken()).thenReturn(new OidcIdToken(
                "token", Instant.now(), Instant.now().plusSeconds(300), claims));
        when(user.getUserInfo()).thenReturn(new OidcUserInfo(claims));
        return user;
    }
}

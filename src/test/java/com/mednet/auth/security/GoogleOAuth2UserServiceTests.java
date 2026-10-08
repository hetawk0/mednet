package com.mednet.auth.security;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.provider.data.ProviderApplicationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleOAuth2UserServiceTests {

    @ParameterizedTest
    @ValueSource(strings = { "LABORATORY", "HOME_CARE" })
    void googleSignInPreservesPartnerRoles(String partnerRole) {
        PlatformAccountRepository accounts = mock(PlatformAccountRepository.class);
        ProviderApplicationRepository providers = mock(ProviderApplicationRepository.class);
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = mock(OAuth2UserService.class);
        OAuth2UserRequest request = mock(OAuth2UserRequest.class);
        String email = "staff@example.test";
        PlatformAccountEntity account = new PlatformAccountEntity("account-1", email, partnerRole);
        when(delegate.loadUser(request)).thenReturn(googleUser(email));
        when(providers.findFirstByEmailIgnoreCaseAndStatusOrderByReviewedAtDesc(email, "APPROVED"))
                .thenReturn(Optional.empty());
        when(accounts.findFirstByGoogleSubject("google-subject")).thenReturn(Optional.empty());
        when(accounts.findFirstByEmailIgnoreCase(email)).thenReturn(Optional.of(account));
        when(accounts.saveAndFlush(account)).thenReturn(account);

        OAuth2UserService<OAuth2UserRequest, OAuth2User> service =
                new GoogleOAuth2UserService(accounts, providers, "root@example.test", delegate);

        OAuth2User authenticatedUser = service.loadUser(request);

        assertThat(authenticatedUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList())
                .contains("ROLE_" + partnerRole);
        assertThat(account.getAccountType()).isEqualTo(partnerRole);
    }

    @Test
    void googleSignInDoesNotDowngradeAnAdministratorRole() {
        PlatformAccountRepository accounts = mock(PlatformAccountRepository.class);
        ProviderApplicationRepository providers = mock(ProviderApplicationRepository.class);
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = mock(OAuth2UserService.class);
        OAuth2UserRequest request = mock(OAuth2UserRequest.class);
        PlatformAccountEntity account = new PlatformAccountEntity("account-1", "admin@example.test", "SUPER_ADMIN");
        when(delegate.loadUser(request)).thenReturn(googleUser("admin@example.test"));
        when(providers.findFirstByEmailIgnoreCaseAndStatusOrderByReviewedAtDesc("admin@example.test", "APPROVED"))
                .thenReturn(Optional.empty());
        when(accounts.findFirstByGoogleSubject("google-subject")).thenReturn(Optional.empty());
        when(accounts.findFirstByEmailIgnoreCase("admin@example.test")).thenReturn(Optional.of(account));
        when(accounts.saveAndFlush(account)).thenReturn(account);

        OAuth2UserService<OAuth2UserRequest, OAuth2User> service =
                new GoogleOAuth2UserService(accounts, providers, "root@example.test", delegate);

        OAuth2User authenticatedUser = service.loadUser(request);

        assertThat(authenticatedUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList())
                .contains("ROLE_SUPER_ADMIN");
        assertThat(account.getAccountType()).isEqualTo("SUPER_ADMIN");
        verify(accounts).saveAndFlush(account);
    }

    @Test
    void configuredAdministratorGetsSuperAdminAuthority() {
        PlatformAccountRepository accounts = mock(PlatformAccountRepository.class);
        ProviderApplicationRepository providers = mock(ProviderApplicationRepository.class);
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = mock(OAuth2UserService.class);
        OAuth2UserRequest request = mock(OAuth2UserRequest.class);
        when(delegate.loadUser(request)).thenReturn(googleUser("root@example.test"));

        OAuth2UserService<OAuth2UserRequest, OAuth2User> service =
                new GoogleOAuth2UserService(accounts, providers, "ROOT@example.test", delegate);

        OAuth2User authenticatedUser = service.loadUser(request);

        assertThat(authenticatedUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList())
                .contains("ROLE_SUPER_ADMIN");
    }

    private static OAuth2User googleUser(String email) {
        return new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "google-subject", "email", email, "email_verified", true),
                "sub");
    }
}

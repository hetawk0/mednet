package com.mednet.auth.security;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleOidcUserServiceTests {

    @Test
    void oidcLoginPassesGoogleIdentityThroughMedNetRoleLinking() {
        GoogleOAuth2UserService accountRoleService = mock(GoogleOAuth2UserService.class);
        @SuppressWarnings("unchecked")
        OAuth2UserService<OidcUserRequest, OidcUser> delegate = mock(OAuth2UserService.class);
        OidcUserRequest request = mock(OidcUserRequest.class);
        OidcIdToken idToken = new OidcIdToken(
                "id-token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("sub", "google-subject"));
        OidcUser googleUser = new DefaultOidcUser(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                idToken);
        when(delegate.loadUser(request)).thenReturn(googleUser);
        when(accountRoleService.applyMedNetRole(googleUser)).thenReturn(
                new DefaultOidcUser(Set.of(new SimpleGrantedAuthority("ROLE_PATIENT")), idToken));

        GoogleOidcUserService service = new GoogleOidcUserService(accountRoleService, delegate);

        OidcUser authenticatedUser = service.loadUser(request);

        assertThat(authenticatedUser.getName()).isEqualTo("google-subject");
        assertThat(authenticatedUser.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_PATIENT");
        verify(accountRoleService).applyMedNetRole(googleUser);
    }
}

package com.mednet.auth.security;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class GoogleOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final GoogleOAuth2UserService accountRoleService;
    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;

    public GoogleOidcUserService(GoogleOAuth2UserService accountRoleService) {
        this(accountRoleService, new OidcUserService());
    }

    GoogleOidcUserService(
            GoogleOAuth2UserService accountRoleService,
            OAuth2UserService<OidcUserRequest, OidcUser> delegate) {
        this.accountRoleService = accountRoleService;
        this.delegate = delegate;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser googleUser = delegate.loadUser(request);
        OAuth2User medNetUser = accountRoleService.applyMedNetRole(googleUser);
        return new DefaultOidcUser(
                medNetUser.getAuthorities(),
                googleUser.getIdToken(),
                googleUser.getUserInfo(),
                "sub");
    }
}

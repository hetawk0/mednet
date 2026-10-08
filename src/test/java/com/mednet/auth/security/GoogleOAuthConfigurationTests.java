package com.mednet.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleOAuthConfigurationTests {

    @Test
    void googleRegistrationUsesGoogleOpenIdSigningKeys() {
        ClientRegistrationRepository registrations = new GoogleOAuthConfiguration()
                .googleClientRegistrationRepository(
                        "client-id",
                        "client-secret",
                        "https://mednet.example.test/api/v1/auth/oauth2/callback/google");

        ClientRegistration google = registrations.findByRegistrationId("google");

        assertThat(google.getProviderDetails().getJwkSetUri())
                .isEqualTo("https://www.googleapis.com/oauth2/v3/certs");
    }
}

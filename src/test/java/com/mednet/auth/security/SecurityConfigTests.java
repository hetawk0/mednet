package com.mednet.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.api.ApiRequestIdFilter;
import com.mednet.provider.data.ProviderApplicationRepository;
import tools.jackson.databind.ObjectMapper;

class SecurityConfigTests {

    @Test
    void configuredAdministratorAuthenticationDoesNotAccessDatabaseDuringStartup() {
        PlatformAccountRepository accounts = mock(PlatformAccountRepository.class);
        ProviderApplicationRepository providers = mock(ProviderApplicationRepository.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("accounts", accounts);
        beanFactory.addBean("providers", providers);
        UserDetailsService service = new SecurityConfig().userDetailsService(
                "admin@example.test",
                "admin-password",
                new BCryptPasswordEncoder(),
                beanFactory.getBeanProvider(PlatformAccountRepository.class),
                beanFactory.getBeanProvider(ProviderApplicationRepository.class));

        UserDetails administrator = service.loadUserByUsername("admin@example.test");

        assertThat(administrator.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_SUPER_ADMIN");
        verifyNoInteractions(accounts, providers);
    }

    @Test
    void databaseBackedSignInWithoutAccountRepositoryIsReportedAsUnavailable() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        UserDetailsService service = new SecurityConfig().userDetailsService(
                "",
                "",
                new BCryptPasswordEncoder(),
                beanFactory.getBeanProvider(PlatformAccountRepository.class),
                beanFactory.getBeanProvider(ProviderApplicationRepository.class));

        assertThatThrownBy(() -> service.loadUserByUsername("patient@example.test"))
                .isInstanceOf(InternalAuthenticationServiceException.class);
    }

    @Test
    void internalAuthenticationFailuresAreNotReportedAsInvalidCredentials() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiRequestIdFilter.ATTRIBUTE_NAME, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityConfig.loginFailureHandler(new ObjectMapper())
                .onAuthenticationFailure(
                        request, response, new InternalAuthenticationServiceException("database unavailable"));

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("\"SERVICE_UNAVAILABLE\"");
    }

    @Test
    void invalidCredentialsRemainGenericUnauthorizedResponses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityConfig.loginFailureHandler(new ObjectMapper())
                .onAuthenticationFailure(request, response, new BadCredentialsException("bad credentials"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"Invalid email or password\"");
    }
}

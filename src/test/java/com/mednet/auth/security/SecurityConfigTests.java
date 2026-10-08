package com.mednet.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.provider.data.ProviderApplicationRepository;

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
}

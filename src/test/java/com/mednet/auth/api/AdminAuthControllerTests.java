package com.mednet.auth.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.authentication.TestingAuthenticationToken;

import com.mednet.admin.data.PlatformAccountRepository;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuthControllerTests {

    @Test
    void sessionPrefersMedNetRoleOverGenericSpringUserAuthority() {
        AdminAuthController controller = new AdminAuthController(
                "", "", "", new StaticListableBeanFactory().getBeanProvider(PlatformAccountRepository.class));
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                "patient@example.test",
                null,
                "ROLE_USER",
                "ROLE_PATIENT");

        AdminAuthController.UserSession session = controller.userSession(authentication);

        assertThat(session.role()).isEqualTo("PATIENT");
        // Without a database there is no account row to report.
        assertThat(session.accountType()).isNull();
    }

}

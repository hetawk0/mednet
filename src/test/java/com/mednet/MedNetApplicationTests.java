package com.mednet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "mednet.admin.email=admin@mednet.test",
    "mednet.admin.password=local-test-password-123"
})
@AutoConfigureMockMvc
class MedNetApplicationTests {

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReportsApplicationUp() {
        assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void adminOverviewRequiresAdministratorAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administratorCanLogInAndReadTheOperationalOverview() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/admin/login")
                        .with(csrf())
                        .param("email", "admin@mednet.test")
                        .param("password", "local-test-password-123"))
                .andExpect(status().isNoContent())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/v1/admin/overview").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiStatus").value("UP"))
                .andExpect(jsonPath("$.modules[0].status").value("NOT_IMPLEMENTED"));
    }
}
package com.mednet;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "mednet.google.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:mednet;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class MedNetApplicationTests {

    private static final String ADMIN_EMAIL = "admin@mednet.test";
    private static final String ADMIN_PASSWORD = UUID.randomUUID().toString();
    private static final String GOOGLE_CLIENT_ID = "test-client-" + UUID.randomUUID();
    private static final String GOOGLE_CLIENT_SECRET = UUID.randomUUID().toString();
    private static final String GOOGLE_REDIRECT_URI = "http://localhost:8080/api/v1/auth/oauth2/callback/google";

    @DynamicPropertySource
    static void registerAdministratorProperties(DynamicPropertyRegistry registry) {
        registry.add("mednet.admin.email", () -> ADMIN_EMAIL);
        registry.add("mednet.admin.password", () -> ADMIN_PASSWORD);
        registry.add("GOOGLE_CLIENT_ID", () -> GOOGLE_CLIENT_ID);
        registry.add("GOOGLE_CLIENT_SECRET", () -> GOOGLE_CLIENT_SECRET);
        registry.add("GOOGLE_REDIRECT_URI", () -> GOOGLE_REDIRECT_URI);
    }

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthEndpointReportsApplicationUp() {
        assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void localEnvironmentValuesMayBeQuoted() {
        assertThat(MedNetApplication.normalizeEnvironmentValue("\"client-secret\"")).isEqualTo("client-secret");
        assertThat(MedNetApplication.normalizeEnvironmentValue("'local-password'")).isEqualTo("local-password");
        assertThat(MedNetApplication.normalizeEnvironmentValue("plain-value")).isEqualTo("plain-value");
    }

    @Test
    void adminOverviewRequiresAdministratorAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleSignInIsAvailableWhenConfigured() throws Exception {
        mockMvc.perform(get("/api/v1/auth/google/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void googleAuthorizationStartsWithConfiguredRedirect() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl())
                .startsWith("https://accounts.google.com/o/oauth2/v2/auth?")
                .contains("redirect_uri=" + GOOGLE_REDIRECT_URI);
    }

    @Test
    void administratorCanLogInAndReadTheOperationalOverview() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .param("email", ADMIN_EMAIL)
                .param("password", ADMIN_PASSWORD))
                .andExpect(status().isNoContent())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/v1/admin/overview").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiStatus").value("UP"))
                .andExpect(jsonPath("$.modules[0].status").value("CONNECTED"));
    }

    @Test
    void adminWorkflowsPersistRecordsAndAuditStatusChanges() throws Exception {
        MvcResult providerResult = mockMvc.perform(post("/api/v1/admin/providers")
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                                {"displayName":"Provider One","email":"provider@example.test","specialty":"Family medicine","credentialReference":"LIC-TEST-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String providerId = objectMapper.readTree(providerResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(patch("/api/v1/admin/providers/{id}/status", providerId)
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewedBy").value(ADMIN_EMAIL));

        MvcResult accountResult = mockMvc.perform(post("/api/v1/admin/accounts")
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"patient@example.test\",\"accountType\":\"PATIENT\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String accountId = objectMapper.readTree(accountResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(patch("/api/v1/admin/accounts/{id}/status", accountId)
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        MvcResult requestResult = mockMvc.perform(post("/api/v1/admin/requests")
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"referenceId\":\"REQ-TEST-1\",\"requestType\":\"LABORATORY\",\"requesterEmail\":\"patient@example.test\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String requestId = objectMapper.readTree(requestResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(patch("/api/v1/admin/requests/{id}/status", requestId)
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        MvcResult auditResult = mockMvc.perform(get("/api/v1/admin/audit")
                .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();
        String auditJson = auditResult.getResponse().getContentAsString();
        assertThat(auditJson).contains(providerId, accountId, requestId, ADMIN_EMAIL);
    }

}
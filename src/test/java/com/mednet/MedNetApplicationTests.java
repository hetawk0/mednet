package com.mednet;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;
import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.admin.data.AdminAuditEventRepository;
import com.mednet.auth.email.EkdSendEmailService;
import com.mednet.patient.data.PatientProfileRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

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

    @Autowired
    private PlatformAccountRepository accounts;

    @Autowired
    private PatientProfileRepository patientProfiles;

    @Autowired
    private AdminAuditEventRepository auditEvents;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EkdSendEmailService emailService;

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
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    void requestIdsAreReturnedAndIncludedInValidationErrors() throws Exception {
        mockMvc.perform(get("/api/v1/auth/google/status")
                .header("X-Request-Id", "mednet-test-42"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "mednet-test-42"));

        mockMvc.perform(post("/api/v1/auth/register")
                .header("X-Request-Id", "validation-check")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"invalid","password":"short"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "validation-check"))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.meta.requestId").value("validation-check"))
                .andExpect(jsonPath("$.error.details[0].field").exists());
    }

    @Test
    void requestIdsAreGeneratedWhenClientValueIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/auth/google/status")
                .header("X-Request-Id", "invalid request id"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void patientCanCreateAndUpdateOnlyTheirOwnProfile() throws Exception {
        String email = "profile-" + UUID.randomUUID() + "@mednet.test";
        PlatformAccountEntity account = new PlatformAccountEntity(
                UUID.randomUUID().toString(), email, "PATIENT");
        account.verifyEmail();
        accounts.save(account);

        long auditsBefore = auditEvents.count();
        String profileJson = """
                {
                  "fullName":"Patient Example",
                  "dateOfBirth":"1990-04-12",
                  "phoneNumber":"+231 770 123 456",
                  "address":"Monrovia, Liberia"
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                "/api/v1/patients/me/profile")
                .with(user(email).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Patient Example"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-04-12"));

        String profileId = patientProfiles.findFirstByAccountId(account.getId()).orElseThrow().getId();
        mockMvc.perform(get("/api/v1/patients/me/profile")
                .with(user(email).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("+231 770 123 456"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                "/api/v1/patients/me/profile")
                .with(user(email).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileJson.replace("Patient Example", "Updated Patient")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Patient"));

        assertThat(patientProfiles.findFirstByAccountId(account.getId()).orElseThrow().getId())
                .isEqualTo(profileId);
        assertThat(auditEvents.count()).isEqualTo(auditsBefore + 2);

        mockMvc.perform(get("/api/v1/patients/{id}/profile", profileId)
                .with(user(email).roles("PATIENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonPatientCannotAccessPatientProfileAndInvalidBirthDateIsRejected() throws Exception {
        String providerEmail = "profile-provider-" + UUID.randomUUID() + "@mednet.test";
        accounts.save(new PlatformAccountEntity(UUID.randomUUID().toString(), providerEmail, "PROVIDER"));

        mockMvc.perform(get("/api/v1/patients/me/profile")
                .with(user(providerEmail).roles("PROVIDER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        String patientEmail = "invalid-profile-" + UUID.randomUUID() + "@mednet.test";
        accounts.save(new PlatformAccountEntity(UUID.randomUUID().toString(), patientEmail, "PATIENT"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                "/api/v1/patients/me/profile")
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "fullName":"Patient Example",
                          "dateOfBirth":"2999-01-01",
                          "phoneNumber":"123",
                          "address":"Monrovia"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void openApiSpecificationIsAvailableOnlyToAdministrators() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api-docs")
                .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("MedNet API"))
                .andExpect(jsonPath("$.openapi").exists());
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
    void authenticationEndpointsRejectInvalidEmailAndResetCodeShapes() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"not-an-email","password":"LongEnoughPassword123!"}
                        """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"not-an-email"}
                        """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/reset-password/verify")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"person@example.test","code":"123"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordResetRequiresVerifiedEmailCodeAndConsumesTicket() throws Exception {
        String resetEmail = "reset-" + UUID.randomUUID() + "@mednet.test";
        String previousPassword = "PreviousSecurePassword123!";
        PlatformAccountEntity account = new PlatformAccountEntity(UUID.randomUUID().toString(), resetEmail, "PATIENT");
        account.setPasswordHash(passwordEncoder.encode(previousPassword));
        account.verifyEmail();
        accounts.save(account);

        AtomicReference<String> emailText = new AtomicReference<>();
        doAnswer(invocation -> {
            emailText.set(invocation.getArgument(3));
            return true;
        }).when(emailService).send(anyString(), anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + resetEmail + "\"}"))
                .andExpect(status().isAccepted());

        Matcher codeMatcher = Pattern.compile("reset code is: (\\d{6})").matcher(emailText.get());
        assertThat(codeMatcher.find()).isTrue();
        String oneTimeCode = codeMatcher.group(1);
        assertThat(emailText.get()).contains("/reset?email=").doesNotContain("token=");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + resetEmail + "\",\"token\":\"" + oneTimeCode
                        + "\",\"password\":\"NewSecurePassword123!\"}"))
                .andExpect(status().isBadRequest());

        MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/reset-password/verify")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + resetEmail + "\",\"code\":\"" + oneTimeCode + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String resetTicket = objectMapper.readTree(verifyResult.getResponse().getContentAsString())
                .get("token").asText();

        mockMvc.perform(post("/api/v1/auth/reset-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + resetEmail + "\",\"token\":\"" + oneTimeCode
                        + "\",\"password\":\"NewSecurePassword123!\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + resetEmail + "\",\"token\":\"" + resetTicket
                        + "\",\"password\":\"NewSecurePassword123!\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + resetEmail + "\",\"token\":\"" + resetTicket
                        + "\",\"password\":\"AnotherSecurePassword123!\"}"))
                .andExpect(status().isBadRequest());

        PlatformAccountEntity updatedAccount = accounts.findFirstByEmailIgnoreCase(resetEmail).orElseThrow();
        assertThat(passwordEncoder.matches("NewSecurePassword123!", updatedAccount.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(previousPassword, updatedAccount.getPasswordHash())).isFalse();
    }

    @Test
    void passwordResetCodeIsInvalidatedAfterFiveFailedAttempts() throws Exception {
        String resetEmail = "locked-reset-" + UUID.randomUUID() + "@mednet.test";
        PlatformAccountEntity account = new PlatformAccountEntity(UUID.randomUUID().toString(), resetEmail, "PATIENT");
        account.verifyEmail();
        accounts.save(account);

        AtomicReference<String> emailText = new AtomicReference<>();
        doAnswer(invocation -> {
            emailText.set(invocation.getArgument(3));
            return true;
        }).when(emailService).send(anyString(), anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + resetEmail + "\"}"))
                .andExpect(status().isAccepted());

        Matcher codeMatcher = Pattern.compile("reset code is: (\\d{6})").matcher(emailText.get());
        assertThat(codeMatcher.find()).isTrue();
        String issuedCode = codeMatcher.group(1);
        String invalidCode = "000000".equals(issuedCode) ? "000001" : "000000";
        String invalidRequest = "{\"email\":\"" + resetEmail + "\",\"code\":\"" + invalidCode + "\"}";

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/reset-password/verify")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/v1/auth/reset-password/verify")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + resetEmail + "\",\"code\":\"" + issuedCode + "\"}"))
                .andExpect(status().isBadRequest());
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

        mockMvc.perform(get("/api/v1/auth/admin/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"));
    }

    @Test
    void administratorCannotCreateOrPromotePrivilegedAccounts() throws Exception {
        mockMvc.perform(post("/api/v1/admin/accounts")
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unexpected-admin@example.test\",\"accountType\":\"SUPER_ADMIN\"}"))
                .andExpect(status().isBadRequest());

        MvcResult accountResult = mockMvc.perform(post("/api/v1/admin/accounts")
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"role-target@example.test\",\"accountType\":\"PATIENT\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String accountId = objectMapper.readTree(accountResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(patch("/api/v1/admin/accounts/{id}/role", accountId)
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountType\":\"SUPER_ADMIN\"}"))
                .andExpect(status().isForbidden());
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
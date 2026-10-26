package com.mednet;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
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
import com.mednet.patient.data.PatientProfileEntity;
import com.mednet.provider.data.ProviderApplicationEntity;
import com.mednet.provider.data.ProviderApplicationRepository;
import com.mednet.provider.data.ProviderAvailabilitySlotEntity;
import com.mednet.provider.data.ProviderAvailabilitySlotRepository;
import com.mednet.appointment.data.AppointmentRepository;
import com.mednet.appointment.data.AppointmentEntity;
import com.mednet.consultation.data.ConsultationEntity;
import com.mednet.consultation.data.ConsultationRepository;
import com.mednet.record.data.ClinicalRecordRepository;
import com.mednet.record.data.PatientProviderRecordConsentRepository;
import com.mednet.record.data.PatientProviderRecordConsentEntity;
import com.mednet.message.data.ConversationEntity;
import com.mednet.message.data.ConversationRepository;
import com.mednet.laboratory.data.LaboratoryResultEntity;
import com.mednet.laboratory.data.LaboratoryResultRepository;
import com.mednet.service.data.PatientServiceRequestEntity;
import com.mednet.service.data.PatientServiceRequestRepository;

import jakarta.servlet.http.Cookie;
import com.mednet.appointment.app.AppointmentReminderService;
import com.mednet.medication.app.MedicationService;
import com.mednet.medication.data.MedicationScheduleEntity;
import com.mednet.medication.data.MedicationScheduleRepository;
import com.mednet.medication.data.MedicationDoseLogEntity;
import com.mednet.medication.data.MedicationDoseLogRepository;
import com.mednet.notification.app.NotificationService;
import com.mednet.notification.data.NotificationEmailOutboxRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.servlet.session.cookie.secure=false",
        "spring.datasource.url=jdbc:h2:mem:mednet;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "mednet.notifications.email.initial-delay=PT1H",
        "mednet.notifications.email.dispatch-interval=PT1H",
        "GOOGLE_CLIENT_ID=test-google-client-id",
        "GOOGLE_CLIENT_SECRET=test-google-client-secret",
        "GOOGLE_REDIRECT_URI=https://mednet.example.test/login/oauth2/code/google",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class MedNetApplicationTests {

    private static final String ADMIN_EMAIL = "admin@mednet.test";
    private static final String ADMIN_PASSWORD = UUID.randomUUID().toString();

    @DynamicPropertySource
    static void registerAdministratorProperties(DynamicPropertyRegistry registry) {
        registry.add("mednet.admin.email", () -> ADMIN_EMAIL);
        registry.add("mednet.admin.password", () -> ADMIN_PASSWORD);
    }

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Autowired
    private HikariDataSource dataSource;

    @Autowired
    private Flyway flyway;

    @LocalServerPort
    private int serverPort;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformAccountRepository accounts;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationEmailOutboxRepository notificationEmailOutbox;

    @Autowired
    private PatientProfileRepository patientProfiles;

    @Autowired
    private AdminAuditEventRepository auditEvents;

    @Autowired
    private ProviderApplicationRepository providerApplications;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private ConsultationRepository consultations;

    @Autowired
    private AppointmentReminderService appointmentReminders;

    @Autowired
    private ProviderAvailabilitySlotRepository availabilitySlots;

    @Autowired
    private ClinicalRecordRepository clinicalRecords;

    @Autowired
    private PatientProviderRecordConsentRepository recordConsents;
    @Autowired
    private ConversationRepository conversations;

    @Autowired
    private MedicationScheduleRepository medicationSchedules;

    @Autowired
    private MedicationDoseLogRepository medicationDoseLogs;

    @Autowired
    private MedicationService medicationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EkdSendEmailService emailService;

    @Autowired
    private PatientServiceRequestRepository serviceRequests;

    @Autowired
    private LaboratoryResultRepository labResults;

    @Test
    void healthEndpointReportsApplicationUp() throws Exception {
        assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.UP);
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + serverPort + "/actuator/health"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void googleOauthIsAvailableAndStartsTheAuthorizationRedirect() throws Exception {
        mockMvc.perform(get("/api/v1/auth/google/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        MvcResult result = mockMvc.perform(get("/oauth2/authorization/google")).andReturn();
        assertThat(result.getResponse().getStatus()).isBetween(300, 399);
        assertThat(result.getResponse().getRedirectedUrl())
                .startsWith("https://accounts.google.com/o/oauth2/v2/auth")
                .contains("redirect_uri=https://mednet.example.test/login/oauth2/code/google");
    }

    @Test
    void databasePoolIsBoundedAndFlywayRetriesTemporaryConnectionExhaustion() {
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(5);
        assertThat(dataSource.getMinimumIdle()).isEqualTo(1);
        assertThat(flyway.getConfiguration().getConnectRetries()).isEqualTo(10);
        assertThat(flyway.getConfiguration().getConnectRetriesInterval()).isEqualTo(2);
    }

    @Test
    void configuredAdministratorIsProvisionedAsActiveVerifiedSuperAdmin() {
        PlatformAccountEntity administrator = accounts.findFirstByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow();
        administrator.changeAccountType("PATIENT");
        administrator.changeStatus("SUSPENDED");
        accounts.saveAndFlush(administrator);

        notificationService.provisionConfiguredAdministrator();

        administrator = accounts.findFirstByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow();

        assertThat(administrator.getAccountType()).isEqualTo("SUPER_ADMIN");
        assertThat(administrator.getStatus()).isEqualTo("ACTIVE");
        assertThat(administrator.isEmailVerified()).isTrue();
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
        mockMvc.perform(get("/api/v1/auth/csrf")
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
        mockMvc.perform(get("/api/v1/auth/csrf")
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
    void providerApplicationsArePrivateUntilApprovalAndOnlyApprovedProvidersAreListed() throws Exception {
        String providerEmail = "applicant-" + UUID.randomUUID() + "@mednet.test";
        PlatformAccountEntity account = new PlatformAccountEntity(
                UUID.randomUUID().toString(), providerEmail, "PATIENT");
        account.setPasswordHash(passwordEncoder.encode("ProviderSecurePassword123!"));
        account.verifyEmail();
        accounts.save(account);

        MvcResult applicationResult = mockMvc.perform(post("/api/v1/providers/applications")
                .with(user(providerEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "displayName":"Dr Example",
                          "specialty":"Family medicine",
                          "credentialReference":"LIC-TEST-42"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String applicationId = objectMapper.readTree(applicationResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/api/v1/notifications")
                .with(user(ADMIN_EMAIL).roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.resourceId == '" + applicationId + "')]").isNotEmpty());

        assertThat(userDetailsService.loadUserByUsername(providerEmail).getAuthorities())
                .extracting("authority")
                .contains("ROLE_PATIENT");
        mockMvc.perform(get("/api/v1/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + applicationId + "')]").isEmpty());

        mockMvc.perform(get("/api/v1/providers/me/application")
                .with(user(providerEmail).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialReference").value("LIC-TEST-42"));

        mockMvc.perform(patch("/api/v1/admin/providers/{id}/status", applicationId)
                .with(user(ADMIN_EMAIL).roles("SUPER_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk());

        assertThat(userDetailsService.loadUserByUsername(providerEmail).getAuthorities())
                .extracting("authority")
                .contains("ROLE_PROVIDER");
        mockMvc.perform(get("/api/v1/notifications")
                .with(user(providerEmail).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.resourceId == '" + applicationId + "')].title")
                        .value(org.hamcrest.Matchers.hasItem("Your provider application was approved")));
        mockMvc.perform(get("/api/v1/providers")
                .param("specialty", "Family")
                .param("search", "Example"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].displayName").value("Dr Example"))
                .andExpect(jsonPath("$.content[0].specialty").value("Family medicine"))
                .andExpect(jsonPath("$.content[0].credentialReference").value("LIC-TEST-42"))
                .andExpect(jsonPath("$.content[0].email").doesNotExist());

        mockMvc.perform(patch("/api/v1/admin/providers/{id}/status", applicationId)
                .with(user(ADMIN_EMAIL).roles("SUPER_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/providers")
                .param("search", "Dr Example"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void appointmentSlotsRequireProviderConfirmationAndProtectBothParticipants() throws Exception {
        String providerEmail = "appointment-provider-" + UUID.randomUUID() + "@mednet.test";
        String patientEmail = "appointment-patient-" + UUID.randomUUID() + "@mednet.test";
        PlatformAccountEntity providerAccount = new PlatformAccountEntity(
                UUID.randomUUID().toString(), providerEmail, "PROVIDER");
        providerAccount.verifyEmail();
        accounts.save(providerAccount);
        PlatformAccountEntity patientAccount = new PlatformAccountEntity(
                UUID.randomUUID().toString(), patientEmail, "PATIENT");
        patientAccount.verifyEmail();
        accounts.save(patientAccount);
        patientProfiles.save(new PatientProfileEntity(
                UUID.randomUUID().toString(),
                patientAccount.getId(),
                "Appointment Patient",
                LocalDate.of(1990, 4, 12),
                "+231 770 123 456",
                "Monrovia"));

        ProviderApplicationEntity provider = new ProviderApplicationEntity(
                UUID.randomUUID().toString(),
                "Dr Appointment",
                providerEmail,
                "Family medicine",
                "LIC-APPT-1");
        provider.review("APPROVED", ADMIN_EMAIL);
        providerApplications.save(provider);

        Instant firstStart = Instant.now().plusSeconds(172_800).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant firstEnd = firstStart.plusSeconds(1_800);
        Instant secondStart = firstStart.plusSeconds(86_400);
        Instant secondEnd = secondStart.plusSeconds(1_800);

        MvcResult firstSlotResult = mockMvc.perform(post("/api/v1/providers/me/availability")
                .with(user(providerEmail).roles("PROVIDER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startsAt":"%s","endsAt":"%s"}
                        """.formatted(firstStart, firstEnd)))
                .andExpect(status().isOk())
                .andReturn();
        String firstSlotId = objectMapper.readTree(firstSlotResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(post("/api/v1/providers/me/availability")
                .with(user(providerEmail).roles("PROVIDER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startsAt":"%s","endsAt":"%s"}
                        """.formatted(firstStart.plusSeconds(900), firstEnd.plusSeconds(900))))
                .andExpect(status().isConflict());

        MvcResult secondSlotResult = mockMvc.perform(post("/api/v1/providers/me/availability")
                .with(user(providerEmail).roles("PROVIDER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startsAt":"%s","endsAt":"%s"}
                        """.formatted(secondStart, secondEnd)))
                .andExpect(status().isOk())
                .andReturn();
        String secondSlotId = objectMapper.readTree(secondSlotResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/api/v1/providers/{id}/availability", provider.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        MvcResult appointmentResult = mockMvc.perform(post("/api/v1/appointments")
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"availabilitySlotId\":\"" + firstSlotId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String appointmentId = objectMapper.readTree(appointmentResult.getResponse().getContentAsString())
                .get("id").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                "/api/v1/providers/me/availability/{id}", firstSlotId)
                .with(user(providerEmail).roles("PROVIDER"))
                .with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/appointments")
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"availabilitySlotId\":\"" + firstSlotId + "\"}"))
                .andExpect(status().isConflict());

        String otherPatientEmail = "other-patient-" + UUID.randomUUID() + "@mednet.test";
        accounts.save(new PlatformAccountEntity(
                UUID.randomUUID().toString(), otherPatientEmail, "PATIENT"));
        mockMvc.perform(get("/api/v1/appointments/{id}", appointmentId)
                .with(user(otherPatientEmail).roles("PATIENT")))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/appointments/{id}", appointmentId)
                .with(user(providerEmail).roles("PROVIDER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"ACCEPT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mockMvc.perform(get("/api/v1/appointments/{id}", appointmentId)
                .with(user(providerEmail).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientName").value("Appointment Patient"))
                .andExpect(jsonPath("$.patientAccountId").value(patientAccount.getId()))
                .andExpect(jsonPath("$.providerId").value(provider.getId()))
                .andExpect(jsonPath("$.providerSpecialty").value("Family medicine"))
                .andExpect(jsonPath("$.rescheduleRequestedBy").doesNotExist());

        mockMvc.perform(patch("/api/v1/appointments/{id}", appointmentId)
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"REQUEST_RESCHEDULE\",\"proposedAvailabilitySlotId\":\""
                        + secondSlotId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESCHEDULE_REQUESTED"))
                .andExpect(jsonPath("$.rescheduleRequestedBy").value("PATIENT"));
        mockMvc.perform(get("/api/v1/appointments/{id}", appointmentId)
                .with(user(providerEmail).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rescheduleRequestedBy").value("PATIENT"))
                .andExpect(jsonPath("$.providerId").value(provider.getId()));

        mockMvc.perform(patch("/api/v1/appointments/{id}", appointmentId)
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"ACCEPT_RESCHEDULE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/appointments/{id}", appointmentId)
                .with(user(providerEmail).roles("PROVIDER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"ACCEPT_RESCHEDULE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startsAt").value(secondStart.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.rescheduleRequestedBy").doesNotExist());

        mockMvc.perform(patch("/api/v1/appointments/{id}", appointmentId)
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"CANCEL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(notificationService.list(patientEmail, 0, 100).getContent())
                .filteredOn(notification -> notification.resourceId().equals(appointmentId))
                .filteredOn(notification -> notification.title().equals("An appointment was cancelled"))
                .hasSize(1);
        assertThat(notificationService.list(providerEmail, 0, 100).getContent())
                .filteredOn(notification -> notification.resourceId().equals(appointmentId))
                .filteredOn(notification -> notification.title().equals("An appointment was cancelled"))
                .hasSize(1);

        mockMvc.perform(get("/api/v1/appointments")
                .with(user(patientEmail).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].patientName").value("Appointment Patient"));
        mockMvc.perform(get("/api/v1/providers/{id}/availability", provider.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        MvcResult adminAppointments = mockMvc.perform(get("/api/v1/admin/appointments")
                .param("page", "0")
                .param("size", "100")
                .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();
        var adminAppointmentRows = objectMapper.readTree(adminAppointments.getResponse().getContentAsString())
                .get("content");
        var adminAppointment = java.util.stream.StreamSupport.stream(
                        adminAppointmentRows.spliterator(), false)
                .filter(row -> appointmentId.equals(row.get("id").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(adminAppointment.has("patientName")).isFalse();
        assertThat(adminAppointment.has("providerName")).isFalse();
        assertThat(adminAppointment.has("status")).isTrue();

        assertThat(appointments.findById(appointmentId).orElseThrow().getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void confirmedAppointmentsCreateIdempotentInAppRemindersForBothParticipants() {
        ProviderApplicationEntity provider = approvedProvider("Dr Reminder", "Family medicine");
        PlatformAccountEntity patient = verifiedAccount(
                "appointment-reminder-patient-" + UUID.randomUUID() + "@mednet.test", "PATIENT");

        AppointmentEntity dayAhead = confirmedAppointment(provider, patient, Instant.now().plusSeconds(23 * 60 * 60));
        AppointmentEntity hourAhead = confirmedAppointment(provider, patient, Instant.now().plusSeconds(30 * 60));
        ProviderAvailabilitySlotEntity pendingSlot = availabilitySlots.saveAndFlush(new ProviderAvailabilitySlotEntity(
                UUID.randomUUID().toString(),
                provider.getId(),
                Instant.now().plusSeconds(15 * 60),
                Instant.now().plusSeconds(45 * 60)));
        AppointmentEntity pending = appointments.saveAndFlush(new AppointmentEntity(
                UUID.randomUUID().toString(), patient.getId(), provider.getId(), pendingSlot.getId()));
        AppointmentEntity cancelled = confirmedAppointment(
                provider, patient, Instant.now().plusSeconds(20 * 60));
        cancelled.cancel();
        appointments.saveAndFlush(cancelled);

        appointmentReminders.createDueReminders();
        appointmentReminders.createDueReminders();

        assertThat(notificationService.list(patient.getEmail(), 0, 100).getContent())
                .filteredOn(notification -> notification.resourceId().equals(dayAhead.getId()))
                .extracting(NotificationService.NotificationDetails::title)
                .containsExactly("You have an appointment within 24 hours");
        assertThat(notificationService.list(patient.getEmail(), 0, 100).getContent())
                .filteredOn(notification -> notification.resourceId().equals(hourAhead.getId()))
                .extracting(NotificationService.NotificationDetails::title)
                .containsExactly("You have an appointment within 1 hour");
        assertThat(notificationService.list(provider.getEmail(), 0, 100).getContent())
                .filteredOn(notification -> notification.resourceId().equals(dayAhead.getId()))
                .hasSize(1);
        assertThat(notificationService.list(provider.getEmail(), 0, 100).getContent())
                .filteredOn(notification -> notification.resourceId().equals(hourAhead.getId()))
                .hasSize(1);
        assertThat(notificationService.list(patient.getEmail(), 0, 100).getContent())
                .noneMatch(notification -> notification.resourceId().equals(cancelled.getId())
                        || notification.resourceId().equals(pending.getId()));
    }

    @Test
    void patientRequestAndMedicationHistoriesSupportOptionalValidatedPagination() throws Exception {
        PlatformAccountEntity patient = verifiedAccount(
                "history-pagination-" + UUID.randomUUID() + "@mednet.test", "PATIENT");
        PlatformAccountEntity homeCareStaff = verifiedAccount(
                "partner-pagination-" + UUID.randomUUID() + "@mednet.test", "HOME_CARE");
        PlatformAccountEntity queuePatient = verifiedAccount(
                "partner-queue-patient-" + UUID.randomUUID() + "@mednet.test", "PATIENT");
        for (int index = 0; index < 27; index++) {
            serviceRequests.save(new PatientServiceRequestEntity(
                    UUID.randomUUID().toString(),
                    patient.getId(),
                    "HOME_CARE",
                    "Request " + index,
                    "Monrovia"));
            serviceRequests.save(new PatientServiceRequestEntity(
                    UUID.randomUUID().toString(),
                    patient.getId(),
                    "LABORATORY",
                    "Test " + index,
                    null));
            PatientServiceRequestEntity assigned = new PatientServiceRequestEntity(
                    UUID.randomUUID().toString(),
                    queuePatient.getId(),
                    "HOME_CARE",
                    "Assigned request " + index,
                    "Monrovia");
            assigned.assignTo(homeCareStaff.getId());
            serviceRequests.save(assigned);
        }
        MedicationScheduleEntity schedule = medicationSchedules.saveAndFlush(new MedicationScheduleEntity(
                UUID.randomUUID().toString(),
                patient.getId(),
                "Patient-entered medicine",
                "1 tablet",
                LocalTime.of(9, 0),
                "Africa/Monrovia",
                LocalDate.now().minusDays(30),
                null,
                Instant.now().plusSeconds(86_400)));
        for (int index = 0; index < 27; index++) {
            medicationDoseLogs.save(new MedicationDoseLogEntity(
                    UUID.randomUUID().toString(),
                    schedule.getId(),
                    LocalDate.now().minusDays(index),
                    Instant.now().minusSeconds(index * 60L)));
        }
        serviceRequests.flush();
        medicationSchedules.flush();
        medicationDoseLogs.flush();

        mockMvc.perform(get("/api/v1/home-care-requests").param("page", "0").param("size", "10")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(27))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(10));
        mockMvc.perform(get("/api/v1/home-care-requests")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(27));
        mockMvc.perform(get("/api/v1/lab-requests").param("page", "0").param("size", "10")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(27))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(10));
        mockMvc.perform(get("/api/v1/partner/service-requests")
                        .param("type", "HOME_CARE")
                        .param("page", "0")
                        .param("size", "10")
                        .with(user(homeCareStaff.getEmail()).roles("HOME_CARE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(27))
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.content[0].patientAccountId").doesNotExist());
        mockMvc.perform(get("/api/v1/partner/service-requests")
                        .param("type", "HOME_CARE")
                        .with(user(homeCareStaff.getEmail()).roles("HOME_CARE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(27));
        mockMvc.perform(get("/api/v1/home-care-requests").param("page", "0").param("size", "101")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/patients/me/medications").param("page", "0").param("size", "10")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(get("/api/v1/patients/me/medications/{id}/dose-logs", schedule.getId())
                        .param("page", "0")
                        .param("size", "10")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(27))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(10));
        mockMvc.perform(get("/api/v1/patients/me/medications/{id}/dose-logs", schedule.getId())
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(27));
        mockMvc.perform(get("/api/v1/patients/me/medications/{id}/dose-logs", schedule.getId())
                        .param("page", "0")
                        .param("size", "101")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patientHistoriesAndPartnerQueuesFilterBeforePagingAndStayOwnerScoped() throws Exception {
        String suffix = UUID.randomUUID().toString();
        PlatformAccountEntity patient = verifiedAccount("filtered-patient-" + suffix + "@mednet.test", "PATIENT");
        PlatformAccountEntity otherPatient = verifiedAccount("filtered-other-" + suffix + "@mednet.test", "PATIENT");
        PlatformAccountEntity queuePatient = verifiedAccount("filtered-queue-patient-" + suffix + "@mednet.test", "PATIENT");
        PlatformAccountEntity staff = verifiedAccount("filtered-staff-" + suffix + "@mednet.test", "HOME_CARE");
        PlatformAccountEntity otherStaff = verifiedAccount("filtered-other-staff-" + suffix + "@mednet.test", "HOME_CARE");
        Instant newest = Instant.now().minusSeconds(60);

        saveServiceRequestAt(patient, "HOME_CARE", "Routine visit", "East Ward", "OPEN", null, newest);
        PatientServiceRequestEntity patientMatchFirst = saveServiceRequestAt(
                patient, "HOME_CARE", "Nursing visit", "North Harbor", "OPEN", null, newest.minusSeconds(1));
        PatientServiceRequestEntity patientMatchSecond = saveServiceRequestAt(
                patient, "HOME_CARE", "Meal support", "North Harbor", "OPEN", null, newest.minusSeconds(2));
        saveServiceRequestAt(
                patient, "HOME_CARE", "Nursing visit", "North Harbor", "CANCELLED", null, newest.minusSeconds(3));
        saveServiceRequestAt(
                otherPatient, "HOME_CARE", "Nursing visit", "North Harbor", "OPEN", null, newest.minusSeconds(4));

        assertThat(serviceRequests.countPatientHistory(patient.getId(), "HOME_CARE", "OPEN", "%north harbor%"))
                .isEqualTo(2);

        saveServiceRequestAt(queuePatient, "HOME_CARE", "Routine visit", "East Ward", "OPEN", staff, newest);
        PatientServiceRequestEntity partnerMatchFirst = saveServiceRequestAt(
                queuePatient, "HOME_CARE", "Nursing visit", "North Harbor", "OPEN", staff, newest.minusSeconds(1));
        PatientServiceRequestEntity partnerMatchSecond = saveServiceRequestAt(
                queuePatient, "HOME_CARE", "Meal support", "North Harbor", "OPEN", staff, newest.minusSeconds(2));
        saveServiceRequestAt(
                queuePatient, "HOME_CARE", "Nursing visit", "North Harbor", "CANCELLED", staff, newest.minusSeconds(3));
        saveServiceRequestAt(
                otherPatient, "HOME_CARE", "Nursing visit", "North Harbor", "OPEN", otherStaff, newest.minusSeconds(4));
        saveServiceRequestAt(
                queuePatient, "LABORATORY", "Nursing visit", "North Harbor", "OPEN", staff, newest.minusSeconds(5));

        String search = "  NORTH HARBOR  ";
        mockMvc.perform(get("/api/v1/home-care-requests")
                        .param("page", "0").param("size", "1").param("status", " open ").param("search", search)
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(patientMatchFirst.getId()));
        mockMvc.perform(get("/api/v1/home-care-requests")
                        .param("page", "1").param("size", "1").param("status", "OPEN").param("search", search)
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(patientMatchSecond.getId()));
        mockMvc.perform(get("/api/v1/home-care-requests")
                        .param("status", "OPEN").param("search", "north harbor")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/v1/partner/service-requests")
                        .param("type", "HOME_CARE").param("page", "0").param("size", "1")
                        .param("status", "open").param("search", search)
                        .with(user(staff.getEmail()).roles("HOME_CARE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(partnerMatchFirst.getId()))
                .andExpect(jsonPath("$.content[0].patientAccountId").doesNotExist());
        mockMvc.perform(get("/api/v1/partner/service-requests")
                        .param("type", "HOME_CARE").param("page", "1").param("size", "1")
                        .param("status", "OPEN").param("search", search)
                        .with(user(staff.getEmail()).roles("HOME_CARE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(partnerMatchSecond.getId()));
        mockMvc.perform(get("/api/v1/partner/service-requests")
                        .param("type", "HOME_CARE").param("status", "OPEN").param("search", "north harbor")
                        .with(user(staff.getEmail()).roles("HOME_CARE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/v1/home-care-requests")
                        .param("page", "0").param("status", "WAITING")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/home-care-requests")
                        .param("page", "0").param("search", "x".repeat(121))
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/partner/service-requests")
                        .param("type", "HOME_CARE").param("page", "0").param("status", "WAITING")
                        .with(user(staff.getEmail()).roles("HOME_CARE")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/partner/service-requests")
                        .param("type", "HOME_CARE").param("page", "0").param("search", "x".repeat(121))
                        .with(user(staff.getEmail()).roles("HOME_CARE")))
                .andExpect(status().isBadRequest());

    }

    @Test
    void conversationAndConsentHistoriesSupportOptionalValidatedPagination() throws Exception {
        PlatformAccountEntity patient = verifiedAccount(
                "conversation-pagination-" + UUID.randomUUID() + "@mednet.test", "PATIENT");
        java.util.List<ProviderApplicationEntity> providers = java.util.stream.IntStream.range(0, 3)
                .mapToObj(index -> approvedProvider("Dr Paging " + index, "General medicine"))
                .toList();
        for (ProviderApplicationEntity provider : providers) {
            confirmedAppointment(provider, patient, Instant.now().plusSeconds(86_400));
            conversations.save(new ConversationEntity(
                    UUID.randomUUID().toString(), patient.getId(), provider.getId()));
            recordConsents.save(new PatientProviderRecordConsentEntity(
                    UUID.randomUUID().toString(), patient.getId(), provider.getId()));
        }
        ProviderApplicationEntity unrelatedProvider = approvedProvider("Dr Unrelated", "General medicine");
        conversations.saveAndFlush(new ConversationEntity(
                UUID.randomUUID().toString(), patient.getId(), unrelatedProvider.getId()));
        conversations.flush();
        recordConsents.flush();

        mockMvc.perform(get("/api/v1/conversations").param("page", "0").param("size", "2")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));
        mockMvc.perform(get("/api/v1/conversations")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get("/api/v1/conversations").param("page", "0").param("size", "1")
                        .with(user(providers.getFirst().getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(get("/api/v1/patients/me/record-consents")
                        .param("page", "0").param("size", "2")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].providerName").exists());
        mockMvc.perform(get("/api/v1/patients/me/record-consents")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get("/api/v1/conversations").param("page", "-1")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/patients/me/record-consents")
                        .param("page", "0").param("size", "101")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void medicalRecordsRequireCareRelationshipAndRevocablePatientConsent() throws Exception {
        String patientEmail = "records-patient-" + UUID.randomUUID() + "@mednet.test";
        String providerEmail = "records-provider-" + UUID.randomUUID() + "@mednet.test";
        PlatformAccountEntity patient = new PlatformAccountEntity(
                UUID.randomUUID().toString(), patientEmail, "PATIENT");
        patient.verifyEmail();
        accounts.save(patient);
        PlatformAccountEntity providerAccount = new PlatformAccountEntity(
                UUID.randomUUID().toString(), providerEmail, "PROVIDER");
        providerAccount.verifyEmail();
        accounts.save(providerAccount);

        ProviderApplicationEntity provider = new ProviderApplicationEntity(
                UUID.randomUUID().toString(), "Records Provider", providerEmail, "Family Medicine", "LIC-REC-01");
        provider.review("APPROVED", ADMIN_EMAIL);
        providerApplications.save(provider);
        String unrelatedProviderEmail = "unrelated-provider-" + UUID.randomUUID() + "@mednet.test";
        PlatformAccountEntity unrelatedProviderAccount = new PlatformAccountEntity(
                UUID.randomUUID().toString(), unrelatedProviderEmail, "PROVIDER");
        unrelatedProviderAccount.verifyEmail();
        accounts.save(unrelatedProviderAccount);
        ProviderApplicationEntity unrelatedProvider = new ProviderApplicationEntity(
                UUID.randomUUID().toString(),
                "Unrelated Provider",
                unrelatedProviderEmail,
                "Pediatrics",
                "LIC-REC-02");
        unrelatedProvider.review("APPROVED", ADMIN_EMAIL);
        providerApplications.save(unrelatedProvider);
        ProviderAvailabilitySlotEntity slot = availabilitySlots.save(new ProviderAvailabilitySlotEntity(
                UUID.randomUUID().toString(),
                provider.getId(),
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(5400)));
        AppointmentEntity appointment = new AppointmentEntity(
                UUID.randomUUID().toString(), patient.getId(), provider.getId(), slot.getId());
        appointment.confirm();
        appointments.save(appointment);

        mockMvc.perform(get("/api/v1/patients/{patientId}/records", patient.getId())
                .with(user(providerEmail).roles("PROVIDER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/patients/me/record-consents")
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerId\":\"" + provider.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GRANTED"))
                .andExpect(jsonPath("$.providerName").value("Records Provider"));

        mockMvc.perform(post("/api/v1/patients/me/record-consents")
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerId\":\"" + unrelatedProvider.getId() + "\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/patients/{patientId}/records", patient.getId())
                .with(user(unrelatedProviderEmail).roles("PROVIDER")))
                .andExpect(status().isForbidden());

        MvcResult created = mockMvc.perform(post("/api/v1/patients/{patientId}/records", patient.getId())
                .with(user(providerEmail).roles("PROVIDER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "category":"DIAGNOSIS",
                          "title":"Initial diagnosis",
                          "clinicalCode":"TEST-CODE",
                          "summary":"Clinical details are private record content.",
                          "effectiveAt":"%s"
                        }
                        """.formatted(Instant.now().minusSeconds(60))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("DIAGNOSIS"))
                .andExpect(jsonPath("$.authorName").value("Records Provider"))
                .andReturn();
        String recordId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/api/v1/patients/me/records")
                .with(user(patientEmail).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].summary").value("Clinical details are private record content."));

        mockMvc.perform(post("/api/v1/patients/{patientId}/records", patient.getId())
                .with(user(providerEmail).roles("PROVIDER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "category":"DIAGNOSIS",
                          "title":"Corrected diagnosis",
                          "summary":"Correction recorded without changing the original entry.",
                          "effectiveAt":"%s",
                          "amendsRecordId":"%s"
                        }
                        """.formatted(Instant.now().minusSeconds(30), recordId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amendsRecordId").value(recordId));

        mockMvc.perform(get("/api/v1/patients/{patientId}/records", patient.getId())
                .with(user(providerEmail).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                "/api/v1/admin/accounts/{id}", patient.getId())
                .with(user(ADMIN_EMAIL).roles("SUPER_ADMIN"))
                .with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                "/api/v1/patients/me/record-consents/{providerId}", provider.getId())
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        mockMvc.perform(get("/api/v1/patients/{patientId}/records", patient.getId())
                .with(user(providerEmail).roles("PROVIDER")))
                .andExpect(status().isForbidden());
        assertThat(clinicalRecords.count()).isEqualTo(2);
        assertThat(recordConsents.count()).isEqualTo(1);
    }

    @Test
    void guardedCareWorkflowsEnforceOwnershipAndCreatePrivateNotifications() throws Exception {
        String patientEmail = "guarded-patient-" + UUID.randomUUID() + "@mednet.test";
        String providerEmail = "guarded-provider-" + UUID.randomUUID() + "@mednet.test";
        PlatformAccountEntity patient = new PlatformAccountEntity(
                UUID.randomUUID().toString(), patientEmail, "PATIENT");
        patient.verifyEmail();
        accounts.save(patient);
        patientProfiles.save(new PatientProfileEntity(
                UUID.randomUUID().toString(),
                patient.getId(),
                "Guarded Patient",
                LocalDate.of(1990, 4, 12),
                "+231 770 987 654",
                "Monrovia"));
        PlatformAccountEntity providerAccount = new PlatformAccountEntity(
                UUID.randomUUID().toString(), providerEmail, "PROVIDER");
        providerAccount.verifyEmail();
        accounts.save(providerAccount);

        ProviderApplicationEntity provider = new ProviderApplicationEntity(
                UUID.randomUUID().toString(), "Guarded Provider", providerEmail, "Family Medicine", "LIC-GUARD-1");
        provider.review("APPROVED", ADMIN_EMAIL);
        providerApplications.save(provider);
        Instant inPersonStartsAt = Instant.now().plusSeconds(10800);
        MvcResult inPersonSlotResult = mockMvc.perform(post("/api/v1/providers/me/availability")
                .with(user(providerEmail).roles("PROVIDER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startsAt":"%s","endsAt":"%s","consultationMode":"IN_PERSON"}
                        """.formatted(inPersonStartsAt, inPersonStartsAt.plusSeconds(1800))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consultationMode").value("IN_PERSON"))
                .andReturn();
        String inPersonSlotId = objectMapper.readTree(inPersonSlotResult.getResponse().getContentAsString())
                .get("id").asText();
        ProviderAvailabilitySlotEntity slot = availabilitySlots.save(new ProviderAvailabilitySlotEntity(
                UUID.randomUUID().toString(),
                provider.getId(),
                Instant.now().plusSeconds(7200),
                Instant.now().plusSeconds(9000)));
        ProviderAvailabilitySlotEntity inPersonSlot = availabilitySlots.findById(inPersonSlotId).orElseThrow();
        AppointmentEntity appointment = new AppointmentEntity(
                UUID.randomUUID().toString(), patient.getId(), provider.getId(), slot.getId());
        appointment.confirm();
        appointments.save(appointment);
        AppointmentEntity inPersonAppointment = new AppointmentEntity(
                UUID.randomUUID().toString(), patient.getId(), provider.getId(), inPersonSlot.getId());
        inPersonAppointment.confirm();
        appointments.save(inPersonAppointment);

        mockMvc.perform(get("/api/v1/appointments/{id}", inPersonAppointment.getId())
                .with(user(patientEmail).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consultationMode").value("IN_PERSON"));
        mockMvc.perform(post("/api/v1/appointments/{id}/consultation", inPersonAppointment.getId())
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf()))
                .andExpect(status().isConflict());

        MvcResult conversationResult = mockMvc.perform(post("/api/v1/conversations")
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerId\":\"" + provider.getId() + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String conversationId = objectMapper.readTree(conversationResult.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(post("/api/v1/conversations/{id}/messages", conversationId)
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Please contact me about my follow-up.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Please contact me about my follow-up."));

        mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId)
                .with(user(providerEmail).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        String unrelatedPatient = "unrelated-" + UUID.randomUUID() + "@mednet.test";
        accounts.save(new PlatformAccountEntity(UUID.randomUUID().toString(), unrelatedPatient, "PATIENT"));
        mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId)
                .with(user(unrelatedPatient).roles("PATIENT")))
                .andExpect(status().isNotFound());

        MvcResult providerNotificationResult = mockMvc.perform(get("/api/v1/notifications")
                .with(user(providerEmail).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("You have a new message"))
                .andReturn();
        String providerNotificationId = objectMapper.readTree(
                providerNotificationResult.getResponse().getContentAsString())
                .get("content").get(0).get("id").asText();
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", providerNotificationId)
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf()))
                .andExpect(status().isNotFound());

        MvcResult consultationResult = mockMvc.perform(post(
                        "/api/v1/appointments/{id}/consultation", appointment.getId())
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
        String consultationId = objectMapper.readTree(consultationResult.getResponse().getContentAsString())
                .get("id").asText();
        mockMvc.perform(post("/api/v1/consultations/{id}/messages", consultationId)
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Text consultation follow-up\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Text consultation follow-up"));
        mockMvc.perform(get("/api/v1/consultations/{id}/messages", consultationId)
                .with(user(providerEmail).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].body").value("Text consultation follow-up"));
        mockMvc.perform(get("/api/v1/consultations/{id}", consultationId)
                .with(user(unrelatedPatient).roles("PATIENT")))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/consultations/{id}/end", consultationId)
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/consultations/{id}/end", consultationId)
                .with(user(providerEmail).roles("PROVIDER"))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"));
        mockMvc.perform(post("/api/v1/consultations/{id}/messages", consultationId)
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"A message after consultation end\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/patients/{patientId}/vitals", patient.getId())
                .with(user(providerEmail).roles("PROVIDER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/consultations/{id}/outcome", consultationId)
                .with(user(providerEmail).roles("PROVIDER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"summary\":\"Consultation summary.\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/patients/me/record-consents")
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerId\":\"" + provider.getId() + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/consultations/{id}/outcome", consultationId)
                .with(user(providerEmail).roles("PROVIDER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"summary\":\"Consultation summary.\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/consultations/{id}", consultationId)
                .with(user(providerEmail).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcomeRecorded").value(true));
        mockMvc.perform(put("/api/v1/consultations/{id}/outcome", consultationId)
                .with(user(providerEmail).roles("PROVIDER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"summary\":\"Duplicate summary.\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/patients/me/records")
                .with(user(patientEmail).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].category").value("ENCOUNTER_SUMMARY"))
                .andExpect(jsonPath("$.content[0].summary").value("Consultation summary."));

        mockMvc.perform(post("/api/v1/patients/me/vitals")
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"metric":"temperature","value":37.1,"unit":"C","recordedAt":"%s"}
                        """.formatted(Instant.now().minusSeconds(10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("PATIENT_REPORTED"));
        mockMvc.perform(get("/api/v1/patients/{patientId}/vitals", patient.getId())
                .with(user(providerEmail).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].metric").value("temperature"));
        mockMvc.perform(get("/api/v1/patients/me/records")
                .with(user(patientEmail).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].category").value("ENCOUNTER_SUMMARY"))
                .andExpect(jsonPath("$.content[1].category").value("VITAL"))
                .andExpect(jsonPath("$.content[1].authorName").value("Patient"));

        LocalDate startDate = LocalDate.now(ZoneId.of("Africa/Monrovia"));
        MvcResult medicationResult = mockMvc.perform(post("/api/v1/patients/me/medications")
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "medicationName":"Patient-entered medicine",
                          "dose":"1 tablet",
                          "reminderTime":"09:00:00",
                          "timeZone":"Africa/Monrovia",
                          "startDate":"%s",
                          "endDate":null
                        }
                        """.formatted(startDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.timeZone").value("Africa/Monrovia"))
                .andReturn();
        String medicationId = objectMapper.readTree(medicationResult.getResponse().getContentAsString())
                .get("id").asText();
        mockMvc.perform(post("/api/v1/patients/me/medications/{id}/dose-logs", medicationId)
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/patients/me/medications/{id}/dose-logs", medicationId)
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/patients/me/medications/{id}/dose-logs", medicationId)
                .with(user(patientEmail).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].doseDate").exists());

        MedicationScheduleEntity scheduled = medicationSchedules.findById(medicationId).orElseThrow();
        scheduled.update(
                scheduled.getMedicationName(),
                scheduled.getDose(),
                scheduled.getReminderTime(),
                scheduled.getTimeZone(),
                scheduled.getStartDate(),
                scheduled.getEndDate(),
                Instant.now().minusSeconds(30));
        medicationSchedules.saveAndFlush(scheduled);
        medicationService.createDueReminders();
        medicationService.createDueReminders();
        mockMvc.perform(get("/api/v1/notifications")
                .with(user(patientEmail).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("A medication reminder is due"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(patch("/api/v1/patients/me/medications/{id}/stop", medicationId)
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"));

        MvcResult homeCareResult = mockMvc.perform(post("/api/v1/home-care-requests")
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"serviceDescription":"Nursing visit","locationDescription":"Monrovia, Sinkor"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
        String requestId = objectMapper.readTree(homeCareResult.getResponse().getContentAsString())
                .get("id").asText();
        mockMvc.perform(get("/api/v1/notifications")
                .with(user(ADMIN_EMAIL).roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.resourceId == '" + requestId + "')].title")
                        .value(org.hamcrest.Matchers.hasItem("A new service request is awaiting assignment")));
        mockMvc.perform(post("/api/v1/admin/accounts")
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"Home Care Staff","email":"homecare-staff@mednet.test","accountType":"HOME_CARE","password":"HomeCare-Secure-Password-2026"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("HOME_CARE"))
                .andExpect(jsonPath("$.publicId").value(org.hamcrest.Matchers.startsWith("HC-")));
        AtomicReference<String> verificationEmailText = new AtomicReference<>();
        doAnswer(invocation -> {
            verificationEmailText.set(invocation.getArgument(3));
            return true;
        }).when(emailService).send(anyString(), anyString(), anyString(), anyString());
        mockMvc.perform(post("/api/v1/auth/resend-verification")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"homecare-staff@mednet.test"}
                        """))
                .andExpect(status().isAccepted());
        Matcher verificationCodeMatcher = Pattern.compile("six-digit MedNet code is: (\\d{6})")
                .matcher(verificationEmailText.get());
        assertThat(verificationCodeMatcher.find()).isTrue();
        mockMvc.perform(post("/api/v1/auth/verify-code")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"homecare-staff@mednet.test","code":"%s"}
                        """.formatted(verificationCodeMatcher.group(1))))
                .andExpect(status().isOk());
        PlatformAccountEntity homeCareStaff =
                accounts.findFirstByEmailIgnoreCase("homecare-staff@mednet.test").orElseThrow();
        assertThat(userDetailsService.loadUserByUsername("homecare-staff@mednet.test").getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_HOME_CARE");
        mockMvc.perform(get("/api/v1/admin/service-requests")
                .param("type", "HOME_CARE")
                .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requestId))
                .andExpect(jsonPath("$[0].requestedService").doesNotExist())
                .andExpect(jsonPath("$[0].assignedStaffPublicId").doesNotExist());
        mockMvc.perform(patch("/api/v1/admin/service-requests/{id}/assignment", requestId)
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"staffAccountId":"%s"}
                        """.formatted(homeCareStaff.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedStaffAccountId").value(homeCareStaff.getId()));
        // The queue now shows who holds the request (public ID only, no name, email or internal ID).
        mockMvc.perform(get("/api/v1/admin/service-requests")
                .param("type", "HOME_CARE")
                .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requestId))
                .andExpect(jsonPath("$[0].assignedStaffPublicId").value(homeCareStaff.getPublicId()))
                .andExpect(jsonPath("$[0].assignedStaffAccountId").doesNotExist())
                .andExpect(jsonPath("$[0].requestedService").doesNotExist())
                .andExpect(jsonPath("$[0].locationDescription").doesNotExist());
        mockMvc.perform(get("/api/v1/notifications")
                .with(user("homecare-staff@mednet.test").roles("HOME_CARE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("A service request has been assigned to you"));
        mockMvc.perform(patch("/api/v1/admin/service-requests/{id}/assignment", requestId)
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"staffAccountId":"%s"}
                        """.formatted(homeCareStaff.getId())))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/partner/service-requests")
                .param("type", "HOME_CARE")
                .with(user("homecare-staff@mednet.test").roles("HOME_CARE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requestId))
                .andExpect(jsonPath("$[0].requestedService").value("Nursing visit"))
                .andExpect(jsonPath("$[0].locationDescription").value("Monrovia, Sinkor"))
                .andExpect(jsonPath("$[0].patientAccountId").doesNotExist());
        mockMvc.perform(patch("/api/v1/partner/service-requests/{id}/status", requestId)
                .with(user("homecare-staff@mednet.test").roles("HOME_CARE"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isConflict());
        Instant homeCareScheduledAt = Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        mockMvc.perform(patch("/api/v1/partner/service-requests/{id}/schedule", requestId)
                .with(user("homecare-staff@mednet.test").roles("HOME_CARE"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"scheduledAt":"%s"}
                        """.formatted(homeCareScheduledAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledAt").value(homeCareScheduledAt.toString()));
        mockMvc.perform(patch("/api/v1/admin/service-requests/{id}/status", requestId)
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/admin/service-requests/{id}/status", requestId)
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/partner/service-requests/{id}/status", requestId)
                .with(user("homecare-staff@mednet.test").roles("HOME_CARE"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mockMvc.perform(patch("/api/v1/partner/service-requests/{id}/status", requestId)
                .with(user("homecare-staff@mednet.test").roles("HOME_CARE"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        mockMvc.perform(get("/api/v1/home-care-requests")
                .with(user(patientEmail).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$[0].scheduledAt").value(homeCareScheduledAt.toString()));

        MvcResult labResult = mockMvc.perform(post("/api/v1/lab-requests")
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"testDescription\":\"Complete blood count\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
        String labRequestId = objectMapper.readTree(labResult.getResponse().getContentAsString())
                .get("id").asText();
        MvcResult labAccountResult = mockMvc.perform(post("/api/v1/admin/accounts")
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"Laboratory Staff","email":"lab-staff@mednet.test","accountType":"LABORATORY","password":"Laboratory-Secure-Password-2026"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("LABORATORY"))
                .andExpect(jsonPath("$.publicId").value(org.hamcrest.Matchers.startsWith("LB-")))
                .andReturn();
        String labStaffId = objectMapper.readTree(labAccountResult.getResponse().getContentAsString())
                .get("id").asText();
        PlatformAccountEntity labStaff = accounts.findById(labStaffId).orElseThrow();
        labStaff.verifyEmail();
        labStaff.setPasswordHash(passwordEncoder.encode("Laboratory-Secure-Password-2026"));
        accounts.saveAndFlush(labStaff);
        assertThat(userDetailsService.loadUserByUsername("lab-staff@mednet.test").getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_LABORATORY");
        mockMvc.perform(get("/api/v1/admin/service-requests")
                .param("type", "LABORATORY")
                .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(labRequestId))
                .andExpect(jsonPath("$[0].requestedService").doesNotExist());
        mockMvc.perform(patch("/api/v1/admin/service-requests/{id}/assignment", labRequestId)
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"staffAccountId":"%s"}
                        """.formatted(labStaff.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/partner/service-requests")
                .param("type", "LABORATORY")
                .with(user("lab-staff@mednet.test").roles("LABORATORY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(labRequestId))
                .andExpect(jsonPath("$[0].requestedService").value("Complete blood count"));
        mockMvc.perform(patch("/api/v1/partner/service-requests/{id}/status", labRequestId)
                .with(user("lab-staff@mednet.test").roles("LABORATORY"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        MvcResult firstResult = mockMvc.perform(post(
                        "/api/v1/partner/service-requests/{id}/lab-result", labRequestId)
                .with(user("lab-staff@mednet.test").roles("LABORATORY"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"summary\":\"Reported result: 4.1 units\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andReturn();
        String firstResultId = objectMapper.readTree(firstResult.getResponse().getContentAsString())
                .get("id").asText();
        // The assigned laboratory can read back its own submission (status only changes through review).
        mockMvc.perform(get("/api/v1/partner/lab-results")
                .with(user("lab-staff@mednet.test").roles("LABORATORY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(firstResultId))
                .andExpect(jsonPath("$[0].requestId").value(labRequestId))
                .andExpect(jsonPath("$[0].status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$[0].patientAccountId").doesNotExist())
                .andExpect(jsonPath("$[0].reviewerAccountId").doesNotExist());
        PlatformAccountEntity otherLab = new PlatformAccountEntity(
                UUID.randomUUID().toString(), "other-lab@mednet.test", "LABORATORY");
        otherLab.verifyEmail();
        accounts.saveAndFlush(otherLab);
        mockMvc.perform(get("/api/v1/partner/lab-results")
                .with(user("other-lab@mednet.test").roles("LABORATORY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        for (var outsider : java.util.List.of(
                user(patientEmail).roles("PATIENT"),
                user(providerEmail).roles("PROVIDER"),
                user(ADMIN_EMAIL).roles("SUPER_ADMIN"),
                user("homecare-staff@mednet.test").roles("HOME_CARE"))) {
            mockMvc.perform(get("/api/v1/partner/lab-results").with(outsider))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/partner/lab-results"))
                .andExpect(status().isUnauthorized());
        // There is no way for a laboratory to change a review decision through this resource.
        mockMvc.perform(patch("/api/v1/partner/lab-results")
                .with(user("lab-staff@mednet.test").roles("LABORATORY"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RELEASED\"}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(patch("/api/v1/lab-results/{id}/review", firstResultId)
                .with(user("lab-staff@mednet.test").roles("LABORATORY"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"RELEASE\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/patients/me/lab-results")
                .with(user(patientEmail).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/patients/me/lab-results/{id}", firstResultId)
                .with(user(patientEmail).roles("PATIENT")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/patients/{patientId}/lab-results/pending", patient.getId())
                .with(user(providerEmail).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(firstResultId));
        mockMvc.perform(patch("/api/v1/lab-results/{id}/review", firstResultId)
                .with(user(providerEmail).roles("PROVIDER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"RETURN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"));
        mockMvc.perform(get("/api/v1/partner/lab-results")
                .with(user("lab-staff@mednet.test").roles("LABORATORY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(firstResultId))
                .andExpect(jsonPath("$[0].status").value("RETURNED"))
                .andExpect(jsonPath("$[0].reviewedAt").isNotEmpty());
        MvcResult finalResult = mockMvc.perform(post(
                        "/api/v1/partner/service-requests/{id}/lab-result", labRequestId)
                .with(user("lab-staff@mednet.test").roles("LABORATORY"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"summary\":\"Reported result: 4.2 units\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andReturn();
        String finalResultId = objectMapper.readTree(finalResult.getResponse().getContentAsString())
                .get("id").asText();
        mockMvc.perform(patch("/api/v1/lab-results/{id}/review", finalResultId)
                .with(user(providerEmail).roles("PROVIDER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"RELEASE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
        mockMvc.perform(get("/api/v1/patients/me/lab-results/{id}", finalResultId)
                .with(user(patientEmail).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Reported result: 4.2 units"));
        mockMvc.perform(get("/api/v1/partner/lab-results")
                .with(user("lab-staff@mednet.test").roles("LABORATORY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(finalResultId))
                .andExpect(jsonPath("$[0].status").value("RELEASED"))
                .andExpect(jsonPath("$[1].id").value(firstResultId))
                .andExpect(jsonPath("$[1].status").value("RETURNED"));
        mockMvc.perform(get("/api/v1/patients/me/records")
                .with(user(patientEmail).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].category").value("LAB_RESULT"));
        mockMvc.perform(patch("/api/v1/partner/service-requests/{id}/status", labRequestId)
                .with(user("lab-staff@mednet.test").roles("LABORATORY"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/partner/service-requests")
                .param("type", "HOME_CARE")
                .with(user("lab-staff@mednet.test").roles("LABORATORY")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/overview")
                .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.openHomeCareRequests").isNumber())
                .andExpect(jsonPath("$.counts.openLaboratoryRequests").isNumber());
    }

    @Test
    void signedOutCallersGetUnauthorizedFromEveryAccountEndpoint() throws Exception {
        String id = UUID.randomUUID().toString();
        var requests = java.util.List.of(
                get("/api/v1/appointments"),
                get("/api/v1/appointments/" + id),
                patch("/api/v1/appointments/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/api/v1/appointments/" + id + "/consultation"),
                get("/api/v1/consultations/" + id),
                get("/api/v1/consultations/" + id + "/messages"),
                post("/api/v1/consultations/" + id + "/messages")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"hello\"}"),
                get("/api/v1/patients/me/medications"),
                post("/api/v1/patients/me/medications").contentType(MediaType.APPLICATION_JSON).content("{}"),
                patch("/api/v1/patients/me/medications/" + id + "/stop"),
                get("/api/v1/patients/me/medications/" + id + "/dose-logs"),
                get("/api/v1/conversations"),
                get("/api/v1/conversations/" + id + "/messages"),
                post("/api/v1/conversations/" + id + "/messages")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"hello\"}"),
                get("/api/v1/notifications"),
                get("/api/v1/notifications/unread-count"),
                patch("/api/v1/notifications/" + id + "/read"),
                get("/api/v1/patients/me/profile"),
                put("/api/v1/patients/me/profile").contentType(MediaType.APPLICATION_JSON).content("{}"),
                get("/api/v1/partner/service-requests"),
                patch("/api/v1/partner/service-requests/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"),
                get("/api/v1/home-care-requests"),
                post("/api/v1/home-care-requests").contentType(MediaType.APPLICATION_JSON).content("{}"),
                patch("/api/v1/home-care-requests/" + id + "/cancel"),
                get("/api/v1/lab-requests"),
                post("/api/v1/lab-requests").contentType(MediaType.APPLICATION_JSON).content("{}"),
                patch("/api/v1/lab-requests/" + id + "/cancel"),
                get("/api/v1/auth/session"),
                get("/api/v1/auth/admin/session"),
                get("/api/v1/admin/overview"),
                get("/api/v1/admin/service-requests"));
        var unexpected = new java.util.ArrayList<String>();
        for (var request : requests) {
            var response = mockMvc.perform(request.with(csrf())).andReturn().getResponse();
            if (response.getStatus() != 401) {
                var sent = request.buildRequest(new org.springframework.mock.web.MockServletContext());
                unexpected.add(sent.getMethod() + " " + sent.getRequestURI() + " -> " + response.getStatus());
            }
        }
        assertThat(unexpected).isEmpty();

        // The public directory and its open times stay available to signed-out visitors.
        mockMvc.perform(get("/api/v1/providers"))
                .andExpect(status().isOk())
                // API responses are never cached and carry the standard browser protections.
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
        mockMvc.perform(get("/api/v1/providers/" + id + "/availability"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
        mockMvc.perform(get("/api/v1/providers/me/application"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void homeCareRequestsStayWithTheAssignedStaffMember() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String patient = "hc-patient-" + suffix + "@mednet.test";
        String assigned = "hc-assigned-" + suffix + "@mednet.test";
        String colleague = "hc-colleague-" + suffix + "@mednet.test";
        String laboratory = "hc-lab-" + suffix + "@mednet.test";
        for (String[] account : new String[][] {
                { patient, "PATIENT" }, { assigned, "HOME_CARE" }, { colleague, "HOME_CARE" }, { laboratory, "LABORATORY" } }) {
            PlatformAccountEntity entity = new PlatformAccountEntity(UUID.randomUUID().toString(), account[0], account[1]);
            entity.verifyEmail();
            accounts.saveAndFlush(entity);
        }
        String requestId = objectMapper.readTree(mockMvc.perform(post("/api/v1/home-care-requests")
                        .with(user(patient).roles("PATIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceDescription\":\"Wound dressing\",\"locationDescription\":\"Paynesville, near the market\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(patch("/api/v1/admin/service-requests/{id}/assignment", requestId)
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffAccountId\":\"%s\"}".formatted(accounts.findFirstByEmailIgnoreCase(assigned).orElseThrow().getId())))
                .andExpect(status().isOk());
        String schedule = "{\"scheduledAt\":\"%s\"}".formatted(Instant.now().plusSeconds(7200));

        // Another home-care staff member neither sees nor changes it.
        mockMvc.perform(get("/api/v1/partner/service-requests").param("type", "HOME_CARE")
                .with(user(colleague).roles("HOME_CARE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + requestId + "')]").isEmpty());
        mockMvc.perform(patch("/api/v1/partner/service-requests/{id}/schedule", requestId)
                .with(user(colleague).roles("HOME_CARE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(schedule))
                .andExpect(status().isNotFound());
        // Laboratory staff cannot schedule, list home care, or act on it; home care cannot use laboratory paths.
        mockMvc.perform(patch("/api/v1/partner/service-requests/{id}/schedule", requestId)
                .with(user(laboratory).roles("LABORATORY")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(schedule))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/partner/service-requests").param("type", "HOME_CARE")
                .with(user(laboratory).roles("LABORATORY")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/partner/service-requests/{id}/status", requestId)
                .with(user(laboratory).roles("LABORATORY")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/partner/service-requests").param("type", "LABORATORY")
                .with(user(assigned).roles("HOME_CARE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/partner/lab-results").with(user(assigned).roles("HOME_CARE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/partner/service-requests/{id}/lab-result", requestId)
                .with(user(assigned).roles("HOME_CARE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"summary\":\"Not a laboratory\"}"))
                .andExpect(status().isForbidden());
        // Patients and administrators have no partner access.
        for (var outsider : java.util.List.of(user(patient).roles("PATIENT"), user(ADMIN_EMAIL).roles("ADMIN"))) {
            mockMvc.perform(get("/api/v1/partner/service-requests").param("type", "HOME_CARE").with(outsider))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/v1/partner/service-requests/{id}/schedule", requestId)
                    .with(outsider).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(schedule))
                    .andExpect(status().isForbidden());
        }
        // Home care has no patient or clinical endpoints.
        mockMvc.perform(get("/api/v1/home-care-requests").with(user(assigned).roles("HOME_CARE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/patients/me/records").with(user(assigned).roles("HOME_CARE")))
                .andExpect(status().isForbidden());

        // A past visit time is refused; a second schedule is a conflict.
        mockMvc.perform(patch("/api/v1/partner/service-requests/{id}/schedule", requestId)
                .with(user(assigned).roles("HOME_CARE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scheduledAt\":\"%s\"}".formatted(Instant.now().minusSeconds(60))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/partner/service-requests/{id}/schedule", requestId)
                .with(user(assigned).roles("HOME_CARE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(schedule))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        mockMvc.perform(patch("/api/v1/partner/service-requests/{id}/schedule", requestId)
                .with(user(assigned).roles("HOME_CARE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(schedule))
                .andExpect(status().isConflict());
        // Completing before starting is refused.
        mockMvc.perform(patch("/api/v1/partner/service-requests/{id}/status", requestId)
                .with(user(assigned).roles("HOME_CARE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isConflict());

        // The patient cancels while the request is still open; the staff member's next action is stale.
        mockMvc.perform(patch("/api/v1/home-care-requests/{id}/cancel", requestId)
                .with(user(patient).roles("PATIENT")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(patch("/api/v1/partner/service-requests/{id}/status", requestId)
                .with(user(assigned).roles("HOME_CARE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Paynesville"))));
        mockMvc.perform(get("/api/v1/partner/service-requests").param("type", "HOME_CARE")
                .with(user(assigned).roles("HOME_CARE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + requestId + "')].status").value(org.hamcrest.Matchers.hasItem("CANCELLED")))
                .andExpect(jsonPath("$[0].patientAccountId").doesNotExist());
    }

    @Test
    void signUpCannotReplaceThePasswordOfAnExistingUnverifiedAccount() throws Exception {
        String staffEmail = "pending-lab-staff@mednet.test";
        String staffPassword = "Lab-Initial-Password-2026";
        mockMvc.perform(post("/api/v1/admin/accounts")
                .with(user(ADMIN_EMAIL).roles("SUPER_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "displayName", "Pending Lab Staff",
                        "email", staffEmail,
                        "accountType", "LABORATORY",
                        "password", staffPassword))))
                .andExpect(status().isOk());
        doAnswer(invocation -> true).when(emailService).send(
                anyString(), anyString(), anyString(), anyString());

        // A pending account cannot be replaced by another registration attempt.
        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "email", staffEmail,
                        "password", "Someone-Elses-Password-2026"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(
                        "An account with this email already exists. Sign in or reset your password."));

        PlatformAccountEntity staff = accounts.findFirstByEmailIgnoreCase(staffEmail).orElseThrow();
        assertThat(staff.getAccountType()).isEqualTo("LABORATORY");
        staff.verifyEmail();
        accounts.saveAndFlush(staff);

        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .param("email", staffEmail)
                .param("password", "Someone-Elses-Password-2026"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .param("email", staffEmail)
                .param("password", staffPassword))
                .andExpect(status().isNoContent());
    }

    @Test
    void directoryEntriesCarryTheNextOpenTimeAndSingleProvidersAreReadable() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ProviderApplicationEntity open = approvedProvider("Dr. Directory Open " + suffix, "Perf specialty " + suffix);
        ProviderApplicationEntity none = approvedProvider("Dr. Directory None " + suffix, "Perf specialty " + suffix);
        Instant soon = Instant.now().plusSeconds(7200).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        availabilitySlots.saveAndFlush(new ProviderAvailabilitySlotEntity(UUID.randomUUID().toString(), open.getId(), soon.plusSeconds(3600), soon.plusSeconds(5400)));
        availabilitySlots.saveAndFlush(new ProviderAvailabilitySlotEntity(UUID.randomUUID().toString(), open.getId(), soon, soon.plusSeconds(1800)));
        ProviderApplicationEntity pending = providerApplications.saveAndFlush(new ProviderApplicationEntity(
                UUID.randomUUID().toString(), "Dr. Pending " + suffix, "pending-" + suffix + "@mednet.test", "Perf specialty " + suffix, "REF"));

        mockMvc.perform(get("/api/v1/providers").param("search", "Directory Open " + suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(open.getId()))
                .andExpect(jsonPath("$.content[0].nextAvailableAt").value(soon.toString()));
        mockMvc.perform(get("/api/v1/providers").param("search", "Directory None " + suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nextAvailableAt").doesNotExist());
        // One provider by ID, signed out: approved only.
        mockMvc.perform(get("/api/v1/providers/{id}", open.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(open.getDisplayName()))
                .andExpect(jsonPath("$.nextAvailableAt").value(soon.toString()))
                .andExpect(jsonPath("$.email").doesNotExist());
        mockMvc.perform(get("/api/v1/providers/{id}", pending.getId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/providers/{id}", UUID.randomUUID().toString())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/providers/me/application")).andExpect(status().isUnauthorized());
        // Specialty counts cover approved providers only, across the whole directory.
        mockMvc.perform(get("/api/v1/providers/specialties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.specialty == 'Perf specialty " + suffix + "')].providers").value(org.hamcrest.Matchers.hasItem(2)));
    }

    @Test
    void auditedListPagesRejectInvalidPaginationAndTieBreakById() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        PlatformAccountEntity patient = verifiedAccount("page-ties-" + suffix + "@mednet.test", "PATIENT");
        patientProfiles.saveAndFlush(new PatientProfileEntity(
                UUID.randomUUID().toString(), patient.getId(), "Pagination Patient", LocalDate.of(1990, 1, 1),
                "+231770000000", "Monrovia"));
        ProviderApplicationEntity provider = approvedProvider("Page tie provider " + suffix, "Page tie specialty");
        var ids = java.util.stream.Stream.generate(() -> UUID.randomUUID().toString())
                .limit(10)
                .sorted()
                .toList();

        ProviderApplicationEntity directoryFirst = new ProviderApplicationEntity(
                ids.get(0), "Directory tie " + suffix, "directory-first-" + suffix + "@mednet.test",
                "Page tie specialty", "REF-A");
        ProviderApplicationEntity directorySecond = new ProviderApplicationEntity(
                ids.get(1), "Directory tie " + suffix, "directory-second-" + suffix + "@mednet.test",
                "Page tie specialty", "REF-B");
        directoryFirst.review("APPROVED", ADMIN_EMAIL);
        directorySecond.review("APPROVED", ADMIN_EMAIL);
        providerApplications.saveAllAndFlush(java.util.List.of(directoryFirst, directorySecond));

        Instant future = Instant.now().plusSeconds(172_800).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        ProviderAvailabilitySlotEntity availabilityFirst = new ProviderAvailabilitySlotEntity(
                ids.get(2), provider.getId(), future, future.plusSeconds(1800));
        ProviderAvailabilitySlotEntity availabilitySecond = new ProviderAvailabilitySlotEntity(
                ids.get(3), provider.getId(), future, future.plusSeconds(1800));
        availabilitySlots.saveAllAndFlush(java.util.List.of(availabilityFirst, availabilitySecond));

        ProviderAvailabilitySlotEntity appointmentSlotFirst = availabilitySlots.saveAndFlush(
                new ProviderAvailabilitySlotEntity(ids.get(4), provider.getId(), future.plusSeconds(3600),
                        future.plusSeconds(5400)));
        ProviderAvailabilitySlotEntity appointmentSlotSecond = availabilitySlots.saveAndFlush(
                new ProviderAvailabilitySlotEntity(ids.get(5), provider.getId(), future.plusSeconds(7200),
                        future.plusSeconds(9000)));
        AppointmentEntity appointmentFirst =
                new AppointmentEntity(ids.get(6), patient.getId(), provider.getId(), appointmentSlotFirst.getId());
        AppointmentEntity appointmentSecond =
                new AppointmentEntity(ids.get(7), patient.getId(), provider.getId(), appointmentSlotSecond.getId());
        appointments.saveAllAndFlush(java.util.List.of(appointmentFirst, appointmentSecond));

        PlatformAccountEntity accountFirst = verifiedAccount("accounts-tie-first-" + suffix + "@mednet.test", "PATIENT");
        PlatformAccountEntity accountSecond = verifiedAccount("accounts-tie-second-" + suffix + "@mednet.test", "PATIENT");
        notificationService.create(patient.getId(), "pagination-tie:" + suffix + ":a", "TEST", "Tie A", "test", "a");
        notificationService.create(patient.getId(), "pagination-tie:" + suffix + ":b", "TEST", "Tie B", "test", "b");
        var tiedTimestamp = Timestamp.from(Instant.parse("2025-01-01T00:00:00Z"));
        var latestTieTimestamp = Timestamp.from(Instant.parse("2099-01-01T00:00:00Z"));
        jdbcTemplate.update("UPDATE appointments SET created_at = ? WHERE id IN (?, ?)",
                latestTieTimestamp, appointmentFirst.getId(), appointmentSecond.getId());
        jdbcTemplate.update("UPDATE platform_accounts SET created_at = ? WHERE id IN (?, ?)",
                tiedTimestamp, accountFirst.getId(), accountSecond.getId());
        jdbcTemplate.update(
                "UPDATE user_notifications SET created_at = ? WHERE event_key IN (?, ?)",
                tiedTimestamp, "pagination-tie:" + suffix + ":a", "pagination-tie:" + suffix + ":b");
        var expectedAccountIds = java.util.List.of(accountFirst.getId(), accountSecond.getId()).stream()
                .sorted()
                .toList();
        var notificationIds = jdbcTemplate.queryForList(
                "SELECT id FROM user_notifications WHERE event_key IN (?, ?) ORDER BY id",
                String.class,
                "pagination-tie:" + suffix + ":a",
                "pagination-tie:" + suffix + ":b");

        mockMvc.perform(get("/api/v1/providers").param("search", "Directory tie " + suffix)
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(directoryFirst.getId()));
        mockMvc.perform(get("/api/v1/providers").param("search", "Directory tie " + suffix)
                        .param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(directorySecond.getId()));

        mockMvc.perform(get("/api/v1/providers/{id}/availability", provider.getId())
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(availabilityFirst.getId()));
        mockMvc.perform(get("/api/v1/providers/{id}/availability", provider.getId())
                        .param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(availabilitySecond.getId()));

        mockMvc.perform(get("/api/v1/appointments").param("page", "0").param("size", "1")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(appointmentFirst.getId()));
        mockMvc.perform(get("/api/v1/appointments").param("page", "1").param("size", "1")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(appointmentSecond.getId()));
        mockMvc.perform(get("/api/v1/admin/appointments").param("page", "0").param("size", "1")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(appointmentFirst.getId()));
        mockMvc.perform(get("/api/v1/admin/appointments").param("page", "1").param("size", "1")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(appointmentSecond.getId()));

        mockMvc.perform(get("/api/v1/admin/accounts").param("search", "accounts-tie-")
                        .param("page", "0").param("size", "1")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(expectedAccountIds.get(0)));
        mockMvc.perform(get("/api/v1/admin/accounts").param("search", "accounts-tie-")
                        .param("page", "1").param("size", "1")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(expectedAccountIds.get(1)));

        mockMvc.perform(get("/api/v1/notifications").param("page", "0").param("size", "1")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(notificationIds.get(0)));
        mockMvc.perform(get("/api/v1/notifications").param("page", "1").param("size", "1")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(notificationIds.get(1)));

        mockMvc.perform(get("/api/v1/providers").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/appointments").param("size", "0")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/appointments").param("size", "0")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/accounts").param("size", "101")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/notifications").param("page", "-1")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/providers/{id}/availability", provider.getId()).param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emailNotificationsRespectPreferencesAndQueueOnlyForVerifiedActiveAccounts() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        PlatformAccountEntity patient = verifiedAccount("email-pref-" + suffix + "@mednet.test", "PATIENT");

        mockMvc.perform(get("/api/v1/notifications/preferences")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.emailVerified").value(true));

        mockMvc.perform(patch("/api/v1/notifications/preferences")
                        .with(user(patient.getEmail()).roles("PATIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(false));
        notificationService.create(
                patient.getId(), "email-pref:" + suffix + ":disabled", "MESSAGE",
                "Email preference test disabled " + suffix, "conversation", UUID.randomUUID().toString());
        var disabledNotification = notificationService.list(patient.getEmail(), 0, 25).getContent().stream()
                .filter(item -> item.title().equals("Email preference test disabled " + suffix))
                .findFirst()
                .orElseThrow();
        assertThat(notificationEmailOutbox.findByNotificationId(disabledNotification.id())).isEmpty();

        mockMvc.perform(patch("/api/v1/notifications/preferences")
                        .with(user(patient.getEmail()).roles("PATIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(true));
        notificationService.create(
                patient.getId(), "email-pref:" + suffix + ":enabled", "MESSAGE",
                "Email preference test enabled " + suffix, "conversation", UUID.randomUUID().toString());
        var enabledNotification = notificationService.list(patient.getEmail(), 0, 25).getContent().stream()
                .filter(item -> item.title().equals("Email preference test enabled " + suffix))
                .findFirst()
                .orElseThrow();
        assertThat(notificationEmailOutbox.findByNotificationId(enabledNotification.id()).orElseThrow().getStatus())
                .isEqualTo("PENDING");

        PlatformAccountEntity unverified = accounts.saveAndFlush(new PlatformAccountEntity(
                UUID.randomUUID().toString(), "unverified-" + suffix + "@mednet.test", "PATIENT"));
        notificationService.create(
                unverified.getId(), "email-pref:" + suffix + ":unverified", "APPOINTMENT",
                "Unverified email preference test " + suffix, "appointment", UUID.randomUUID().toString());
        var unverifiedNotification = notificationService.list(unverified.getEmail(), 0, 25).getContent().stream()
                .filter(item -> item.title().equals("Unverified email preference test " + suffix))
                .findFirst()
                .orElseThrow();
        assertThat(notificationEmailOutbox.findByNotificationId(unverifiedNotification.id())).isEmpty();
    }

    @Test
    void aggregateReportsAreDateBoundedAdminOnlyAndExcludeIdentifiableClinicalData() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        PlatformAccountEntity patient = verifiedAccount("report-patient-" + suffix + "@mednet.test", "PATIENT");
        PlatformAccountEntity laboratory = verifiedAccount("report-lab-" + suffix + "@mednet.test", "LABORATORY");
        ProviderApplicationEntity provider = approvedProvider("Report provider " + suffix, "General medicine");
        Instant slotStart = Instant.now().plusSeconds(10_000);
        ProviderAvailabilitySlotEntity slot = availabilitySlots.saveAndFlush(new ProviderAvailabilitySlotEntity(
                UUID.randomUUID().toString(), provider.getId(), slotStart, slotStart.plusSeconds(1_800)));
        appointments.saveAndFlush(new AppointmentEntity(
                UUID.randomUUID().toString(), patient.getId(), provider.getId(), slot.getId()));
        AppointmentEntity consultationAppointment = new AppointmentEntity(
                UUID.randomUUID().toString(), patient.getId(), provider.getId(), slot.getId());
        consultationAppointment.confirm();
        appointments.saveAndFlush(consultationAppointment);
        ConsultationEntity consultation = new ConsultationEntity(
                UUID.randomUUID().toString(), consultationAppointment.getId());
        consultation.end();
        consultations.saveAndFlush(consultation);
        PatientServiceRequestEntity request = serviceRequests.saveAndFlush(new PatientServiceRequestEntity(
                UUID.randomUUID().toString(), patient.getId(), "LABORATORY", "Blood test", "Monrovia"));
        LaboratoryResultEntity result = labResults.saveAndFlush(new LaboratoryResultEntity(
                UUID.randomUUID().toString(), request.getId(), patient.getId(), laboratory.getId(),
                "private result that must never appear in aggregate output"));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        MvcResult response = mockMvc.perform(get("/api/v1/admin/reports")
                        .param("from", today.toString())
                        .param("through", today.toString())
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();
        String reportJson = response.getResponse().getContentAsString();
        assertThat(reportJson)
                .contains("\"category\":\"PATIENT\"")
                .contains("\"category\":\"TEXT\"")
                .contains("\"category\":\"LABORATORY\"")
                .contains("\"status\":\"PENDING_REVIEW\"")
                .contains("\"totalRegisteredPatients\":")
                .contains("\"patientRegistrations\":[")
                .contains("\"providersByStatus\":[")
                .contains("\"completedVirtualConsultations\":[")
                .contains("\"awaitingAction\":[")
                .doesNotContain(patient.getEmail())
                .doesNotContain(laboratory.getEmail())
                .doesNotContain(patient.getId())
                .doesNotContain(result.getSummary());
         assertThat(reportJson)
                .contains("\"status\":\"APPROVED\"")
                .contains("\"status\":\"PENDING\"")
                .contains("\"status\":\"OPEN\"")
                .contains("\"status\":\"COMPLETED\"");

        MvcResult csv = mockMvc.perform(get("/api/v1/admin/reports/export.csv")
                        .param("from", today.toString())
                        .param("through", today.toString())
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".csv")))
                .andReturn();
        assertThat(csv.getResponse().getContentAsString())
                .contains("Period,Dataset,Category,Status,Count")
                .contains("patient_registrations")
                .contains("provider_status")
                .contains("completed_text_consultations")
                .contains("awaiting_action")
                .contains("laboratory_results")
                .doesNotContain(patient.getEmail())
                .doesNotContain(result.getSummary());

        mockMvc.perform(get("/api/v1/admin/reports").param("from", today.toString())
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/reports")
                        .param("from", today.toString())
                        .param("through", today.minusDays(1).toString())
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/reports")
                        .param("from", today.minusDays(731).toString())
                        .param("through", today.toString())
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/reports")
                        .param("from", LocalDate.MAX.toString())
                        .param("through", LocalDate.MAX.toString())
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/reports")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void appointmentHistoriesAndProviderPatientDirectoryPageAllScopedResults() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ProviderApplicationEntity provider = approvedProvider("Dr. History " + suffix, "General medicine");
        ProviderApplicationEntity otherProvider = approvedProvider("Dr. Other History " + suffix, "General medicine");
        java.util.List<PlatformAccountEntity> carePatients = new java.util.ArrayList<>();
        java.util.Set<String> allAppointmentIds = new java.util.HashSet<>();
        Instant startsAt = Instant.now().plusSeconds(86_400).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        for (int index = 0; index < 101; index++) {
            carePatients.add(carePatient(provider, String.format("History patient %03d %s", index, suffix), false));
        }

        PlatformAccountEntity firstPatient = carePatients.get(0);
        for (int index = 0; index < 100; index++) {
            ProviderAvailabilitySlotEntity slot = availabilitySlots.saveAndFlush(new ProviderAvailabilitySlotEntity(
                    UUID.randomUUID().toString(),
                    provider.getId(),
                    startsAt.plusSeconds(index * 3600L),
                    startsAt.plusSeconds(index * 3600L + 1800)));
            AppointmentEntity appointment = appointments.saveAndFlush(new AppointmentEntity(
                    UUID.randomUUID().toString(), firstPatient.getId(), provider.getId(), slot.getId()));
            allAppointmentIds.add(appointment.getId());
        }

        PlatformAccountEntity unrelatedPatient = carePatient(
                otherProvider, "Unrelated patient " + suffix, false);
        PlatformAccountEntity suspendedPatient = carePatient(
                provider, "Suspended patient " + suffix, false);
        suspendedPatient.changeStatus("SUSPENDED");
        accounts.saveAndFlush(suspendedPatient);
        PlatformAccountEntity requestOnly = verifiedAccount("request-only-" + suffix + "@mednet.test", "PATIENT");
        patientProfiles.saveAndFlush(new PatientProfileEntity(
                UUID.randomUUID().toString(), requestOnly.getId(), "Request only " + suffix,
                LocalDate.of(1990, 1, 1), "+231770000000", "Monrovia"));
        ProviderAvailabilitySlotEntity requestSlot = availabilitySlots.saveAndFlush(new ProviderAvailabilitySlotEntity(
                UUID.randomUUID().toString(), provider.getId(), startsAt.plusSeconds(500_000), startsAt.plusSeconds(501_800)));
        appointments.saveAndFlush(new AppointmentEntity(
                UUID.randomUUID().toString(), requestOnly.getId(), provider.getId(), requestSlot.getId()));

        var patientPage0 = mockMvc.perform(get("/api/v1/appointments").param("page", "0").param("size", "100")
                        .with(user(firstPatient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(101))
                .andExpect(jsonPath("$.content.length()").value(100))
                .andReturn();
        var patientPage1 = mockMvc.perform(get("/api/v1/appointments").param("page", "1").param("size", "100")
                        .with(user(firstPatient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andReturn();
        var patientIds = new java.util.HashSet<String>();
        for (var result : java.util.List.of(patientPage0, patientPage1)) {
            var contentRows = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
            contentRows.forEach(row -> patientIds.add(row.get("id").asText()));
        }
        assertThat(patientIds).hasSize(101);

        var providerPage0 = mockMvc.perform(get("/api/v1/appointments").param("page", "0").param("size", "100")
                        .with(user(provider.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(203))
                .andExpect(jsonPath("$.content.length()").value(100))
                .andReturn();
        var providerPage1 = mockMvc.perform(get("/api/v1/appointments").param("page", "1").param("size", "100")
                        .with(user(provider.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(100))
                .andReturn();
        var providerPage2 = mockMvc.perform(get("/api/v1/appointments").param("page", "2").param("size", "100")
                        .with(user(provider.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content.length()").value(3))
                .andReturn();
        for (var result : java.util.List.of(providerPage0, providerPage1, providerPage2)) {
            var contentRows = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
            contentRows.forEach(row -> allAppointmentIds.add(row.get("id").asText()));
        }
        assertThat(allAppointmentIds).hasSize(203);

        var directoryPage0 = mockMvc.perform(get("/api/v1/providers/me/patients").param("page", "0").param("size", "100")
                        .with(user(provider.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(101))
                .andExpect(jsonPath("$.content.length()").value(100))
                .andExpect(jsonPath("$.content[0].name").value("History patient 000 " + suffix))
                .andExpect(jsonPath("$.content[0].patientAccountId").exists())
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andReturn();
        var directoryPage1 = mockMvc.perform(get("/api/v1/providers/me/patients").param("page", "1").param("size", "100")
                        .with(user(provider.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andReturn();
        var directoryIds = new java.util.HashSet<String>();
        for (var result : java.util.List.of(directoryPage0, directoryPage1)) {
            var contentRows = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
            contentRows.forEach(row -> directoryIds.add(row.get("patientAccountId").asText()));
        }
        assertThat(directoryIds).hasSize(101)
                .containsAll(carePatients.stream().map(PlatformAccountEntity::getId).toList())
                .doesNotContain(unrelatedPatient.getId(), suspendedPatient.getId(), requestOnly.getId());

        mockMvc.perform(get("/api/v1/providers/me/patients/{id}", firstPatient.getId())
                        .with(user(provider.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientAccountId").value(firstPatient.getId()));
        mockMvc.perform(get("/api/v1/providers/me/patients/{id}", unrelatedPatient.getId())
                        .with(user(provider.getEmail()).roles("PROVIDER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/providers/me/patients/{id}/appointments", unrelatedPatient.getId())
                        .with(user(provider.getEmail()).roles("PROVIDER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/providers/me/patients/{id}", suspendedPatient.getId())
                        .with(user(provider.getEmail()).roles("PROVIDER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/providers/me/patients/{id}/appointments", suspendedPatient.getId())
                        .with(user(provider.getEmail()).roles("PROVIDER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/providers/me/patients/{id}/appointments", firstPatient.getId())
                        .param("page", "0").param("size", "100")
                        .with(user(provider.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(101))
                .andExpect(jsonPath("$.content.length()").value(100));
        mockMvc.perform(get("/api/v1/providers/me/patients/{id}/appointments", firstPatient.getId())
                        .param("page", "1").param("size", "100")
                        .with(user(provider.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(get("/api/v1/providers/me/patients").param("page", "-1")
                        .with(user(provider.getEmail()).roles("PROVIDER")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/providers/me/patients").param("size", "101")
                        .with(user(provider.getEmail()).roles("PROVIDER")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/providers/me/patients")
                        .with(user(firstPatient.getEmail()).roles("PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorListsCanBePagedWithoutLosingRowsAndStayAdministratorOnly() throws Exception {
        for (int i = 0; i < 3; i++) {
            auditEvents.saveAndFlush(new com.mednet.admin.data.AdminAuditEventEntity(
                    UUID.randomUUID().toString(), ADMIN_EMAIL, "paging.check", "test", "row-" + i));
        }
        long total = auditEvents.count();
        mockMvc.perform(get("/api/v1/admin/audit").param("page", "0").param("size", "2")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value((int) total));
        mockMvc.perform(get("/api/v1/admin/audit").param("page", "0").param("size", "101")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isBadRequest());
        // Filters run on the server, across every page: actor/action/record text, the action read as words,
        // label matches sent by the client, and record type.
        mockMvc.perform(get("/api/v1/admin/audit").param("page", "0").param("search", "paging check")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/api/v1/admin/audit").param("page", "0").param("search", "label words only")
                        .param("labelAction", "paging.check").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/api/v1/admin/audit").param("page", "0").param("resourceType", "test")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/api/v1/admin/audit/resource-types").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(org.hamcrest.Matchers.hasItem("test")));
        mockMvc.perform(get("/api/v1/admin/audit/resource-types").with(user("someone@mednet.test").roles("PATIENT")))
                .andExpect(status().isForbidden());
        // Without a page the original list shape is unchanged.
        mockMvc.perform(get("/api/v1/admin/audit").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        for (String path : java.util.List.of("/api/v1/admin/providers", "/api/v1/admin/requests")) {
            mockMvc.perform(get(path).param("page", "0").param("size", "5").with(user(ADMIN_EMAIL).roles("SUPER_ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").isNumber());
        }
        mockMvc.perform(get("/api/v1/admin/service-requests").param("type", "LABORATORY").param("page", "0").param("size", "5")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].requestedService").doesNotExist());
        ProviderApplicationEntity searchable = approvedProvider("Dr. Searchable " + UUID.randomUUID().toString().substring(0, 8), "Paging medicine");
        mockMvc.perform(get("/api/v1/admin/providers").param("page", "0").param("status", "APPROVED")
                        .param("search", searchable.getDisplayName().toLowerCase()).with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(searchable.getId()));
        mockMvc.perform(get("/api/v1/admin/providers").param("page", "0").param("status", "PENDING")
                        .param("search", searchable.getDisplayName()).with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/admin/service-requests").param("type", "HOME_CARE").param("page", "0")
                        .param("status", "OPEN").param("status", "IN_PROGRESS").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.status == 'RESOLVED' || @.status == 'CANCELLED')]").isEmpty());
        mockMvc.perform(get("/api/v1/admin/service-requests").param("type", "HOME_CARE").param("page", "0")
                        .param("status", "DELETED").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isBadRequest());
        for (var request : java.util.List.of(
                get("/api/v1/admin/audit").param("page", "0"),
                get("/api/v1/admin/providers").param("page", "0"),
                get("/api/v1/admin/requests").param("page", "0"),
                get("/api/v1/admin/service-requests").param("type", "HOME_CARE").param("page", "0"))) {
            mockMvc.perform(request.with(user("someone@mednet.test").roles("PATIENT"))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/admin/audit").param("page", "0")).andExpect(status().isUnauthorized());
    }

    @Test
    void theReviewQueueListsOnlyThisProvidersCarePatientsAndNoResultText() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ProviderApplicationEntity doctor = approvedProvider("Dr. Queue " + suffix, "General medicine");
        ProviderApplicationEntity otherDoctor = approvedProvider("Dr. Other Queue " + suffix, "General medicine");
        PlatformAccountEntity lab = verifiedAccount("queue-lab-" + suffix + "@mednet.test", "LABORATORY");
        PlatformAccountEntity consented = carePatient(doctor, "Consented " + suffix, true);
        PlatformAccountEntity notShared = carePatient(doctor, "Not Shared " + suffix, false);
        PlatformAccountEntity nothingPending = carePatient(doctor, "Nothing Pending " + suffix, true);
        PlatformAccountEntity otherDoctorsPatient = carePatient(otherDoctor, "Elsewhere " + suffix, true);
        pendingResult(consented, lab, "Synthetic queue result A " + suffix);
        pendingResult(consented, lab, "Synthetic queue result B " + suffix);
        pendingResult(notShared, lab, "Synthetic hidden result " + suffix);
        pendingResult(otherDoctorsPatient, lab, "Synthetic other result " + suffix);

        String body = mockMvc.perform(get("/api/v1/providers/me/lab-review-queue")
                        .with(user(doctor.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPendingResults").value(2))
                .andExpect(jsonPath("$.patientsWithoutRecordAccess").value(1))
                .andExpect(jsonPath("$.content[0].patientAccountId").value(consented.getId()))
                .andExpect(jsonPath("$.content[0].patientName").value("Consented " + suffix))
                .andExpect(jsonPath("$.content[0].recordAccess").value(true))
                .andExpect(jsonPath("$.content[0].pendingResults").value(2))
                .andExpect(jsonPath("$.content[1].patientAccountId").value(notShared.getId()))
                .andExpect(jsonPath("$.content[1].recordAccess").value(false))
                .andExpect(jsonPath("$.content[1].pendingResults").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Synthetic").doesNotContain(nothingPending.getId()).doesNotContain(otherDoctorsPatient.getId());
        mockMvc.perform(get("/api/v1/patients/{patientId}/lab-results/pending", consented.getId())
                        .param("page", "0").param("size", "1")
                        .with(user(doctor.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(get("/api/v1/patients/{patientId}/lab-results/pending", consented.getId())
                        .with(user(doctor.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/v1/patients/{patientId}/lab-results/pending", notShared.getId())
                        .param("page", "0")
                        .with(user(doctor.getEmail()).roles("PROVIDER")))
                .andExpect(status().isForbidden());
        // Paging keeps the totals.
        mockMvc.perform(get("/api/v1/providers/me/lab-review-queue").param("page", "1").param("size", "1")
                        .with(user(doctor.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].patientAccountId").value(notShared.getId()))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalPendingResults").value(2));
        // Revoking consent moves the patient out of the results list straight away.
        mockMvc.perform(delete("/api/v1/patients/me/record-consents/{providerId}", doctor.getId())
                        .with(user(consented.getEmail()).roles("PATIENT")).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/providers/me/lab-review-queue").with(user(doctor.getEmail()).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPendingResults").value(0))
                .andExpect(jsonPath("$.patientsWithoutRecordAccess").value(2));
        for (var outsider : java.util.List.of(
                user(consented.getEmail()).roles("PATIENT"), user(lab.getEmail()).roles("LABORATORY"), user(ADMIN_EMAIL).roles("ADMIN"))) {
            mockMvc.perform(get("/api/v1/providers/me/lab-review-queue").with(outsider)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/providers/me/lab-review-queue")).andExpect(status().isUnauthorized());
    }

    @Test
    void laboratoryAndPatientResultListsCanBePagedWithinTheirOwnRecords() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        PlatformAccountEntity lab = verifiedAccount("paged-lab-" + suffix + "@mednet.test", "LABORATORY");
        PlatformAccountEntity otherLab = verifiedAccount("paged-lab2-" + suffix + "@mednet.test", "LABORATORY");
        PlatformAccountEntity patient = verifiedAccount("paged-patient-" + suffix + "@mednet.test", "PATIENT");
        for (int i = 0; i < 3; i++) {
            pendingResult(patient, lab, "Synthetic paged result " + i);
        }
        LaboratoryResultEntity released = pendingResult(patient, lab, "Synthetic released result");
        released.review("RELEASED", null);
        labResults.saveAndFlush(released);

        mockMvc.perform(get("/api/v1/partner/lab-results").param("page", "0").param("size", "2")
                        .with(user(lab.getEmail()).roles("LABORATORY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content.length()").value(2));
        mockMvc.perform(get("/api/v1/partner/lab-results").param("page", "0").param("status", "RELEASED")
                        .with(user(lab.getEmail()).roles("LABORATORY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/partner/lab-results").param("page", "0").param("status", "DELETED")
                        .with(user(lab.getEmail()).roles("LABORATORY")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/partner/lab-results").param("page", "0")
                        .with(user(otherLab.getEmail()).roles("LABORATORY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        // The laboratory's order list carries each order's latest review state, never the result text.
        mockMvc.perform(get("/api/v1/partner/service-requests").param("type", "LABORATORY")
                        .with(user(lab.getEmail()).roles("LABORATORY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + released.getRequestId() + "')].latestResult.status").value(org.hamcrest.Matchers.hasItem("RELEASED")))
                .andExpect(jsonPath("$[0].latestResult.summary").doesNotExist());
        // Patients see released results only, paged.
        mockMvc.perform(get("/api/v1/patients/me/lab-results").param("page", "0").param("size", "10")
                        .with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].summary").value("Synthetic released result"));
        mockMvc.perform(get("/api/v1/partner/lab-results").param("page", "0").with(user(patient.getEmail()).roles("PATIENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/patients/me/lab-results").param("page", "0").with(user(lab.getEmail()).roles("LABORATORY")))
                .andExpect(status().isForbidden());
    }

    private ProviderApplicationEntity approvedProvider(String name, String specialty) {
        String email = "provider-" + UUID.randomUUID() + "@mednet.test";
        verifiedAccount(email, "PROVIDER");
        ProviderApplicationEntity provider = new ProviderApplicationEntity(
                UUID.randomUUID().toString(), name, email, specialty, "REF-" + UUID.randomUUID().toString().substring(0, 6));
        provider.review("APPROVED", ADMIN_EMAIL);
        return providerApplications.saveAndFlush(provider);
    }

    private PlatformAccountEntity verifiedAccount(String email, String type) {
        PlatformAccountEntity account = new PlatformAccountEntity(UUID.randomUUID().toString(), email, type);
        account.verifyEmail();
        return accounts.saveAndFlush(account);
    }

    private PatientServiceRequestEntity saveServiceRequestAt(
            PlatformAccountEntity patient,
            String type,
            String requestedService,
            String location,
            String status,
            PlatformAccountEntity assignedStaff,
            Instant createdAt) {
        PatientServiceRequestEntity request = new PatientServiceRequestEntity(
                UUID.randomUUID().toString(), patient.getId(), type, requestedService, location);
        if (!"OPEN".equals(status)) {
            request.changeStatus(status);
        }
        if (assignedStaff != null) {
            request.assignTo(assignedStaff.getId());
        }
        serviceRequests.saveAndFlush(request);
        Timestamp timestamp = Timestamp.from(createdAt);
        jdbcTemplate.update(
                "UPDATE patient_service_requests SET created_at = ?, updated_at = ? WHERE id = ?",
                timestamp,
                timestamp,
                request.getId());
        return request;
    }

    private AppointmentEntity confirmedAppointment(
            ProviderApplicationEntity provider, PlatformAccountEntity patient, Instant startsAt) {
        ProviderAvailabilitySlotEntity slot = new ProviderAvailabilitySlotEntity(
                UUID.randomUUID().toString(),
                provider.getId(),
                startsAt,
                startsAt.plusSeconds(1_800));
        slot.reserve();
        availabilitySlots.saveAndFlush(slot);
        AppointmentEntity appointment = new AppointmentEntity(
                UUID.randomUUID().toString(), patient.getId(), provider.getId(), slot.getId());
        appointment.confirm();
        return appointments.saveAndFlush(appointment);
    }

    private PlatformAccountEntity carePatient(ProviderApplicationEntity provider, String name, boolean consent) {
        PlatformAccountEntity patient = verifiedAccount("care-" + UUID.randomUUID() + "@mednet.test", "PATIENT");
        patientProfiles.saveAndFlush(new PatientProfileEntity(
                UUID.randomUUID().toString(), patient.getId(), name, java.time.LocalDate.of(1990, 1, 1), "+231770000000", "Monrovia"));
        Instant start = Instant.now().plusSeconds(86_400);
        ProviderAvailabilitySlotEntity slot = availabilitySlots.saveAndFlush(new ProviderAvailabilitySlotEntity(
                UUID.randomUUID().toString(), provider.getId(), start, start.plusSeconds(1800)));
        AppointmentEntity appointment = new AppointmentEntity(UUID.randomUUID().toString(), patient.getId(), provider.getId(), slot.getId());
        appointment.confirm();
        appointments.saveAndFlush(appointment);
        if (consent) {
            recordConsents.saveAndFlush(new PatientProviderRecordConsentEntity(UUID.randomUUID().toString(), patient.getId(), provider.getId()));
        }
        return patient;
    }

    private LaboratoryResultEntity pendingResult(PlatformAccountEntity patient, PlatformAccountEntity lab, String summary) {
        PatientServiceRequestEntity request = new PatientServiceRequestEntity(
                UUID.randomUUID().toString(), patient.getId(), "LABORATORY", "Synthetic test", null);
        request.assignTo(lab.getId());
        request.changeStatus("IN_PROGRESS");
        serviceRequests.saveAndFlush(request);
        return labResults.saveAndFlush(new LaboratoryResultEntity(
                UUID.randomUUID().toString(), request.getId(), patient.getId(), lab.getId(), summary));
    }

    @Test
    void openApiSpecificationIsAvailableOnlyToAdministrators() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api-docs/"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/swagger-ui/"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api-docs")
                .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());

        mockMvc.perform(get("/api-docs/")
                .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("MedNet API"))
                .andExpect(jsonPath("$.openapi").exists());

        mockMvc.perform(get("/swagger-ui/")
                .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().is(308))
                .andExpect(header().string("Location", "/swagger-ui/index.html"));

        mockMvc.perform(get("/swagger-ui")
                .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/swagger-ui/index.html")
                .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));

        mockMvc.perform(get("/api-docs/swagger-config")
                .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/api-docs/"));
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

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"long-password@mednet.test","password":"1234567890123456789012345678901234567890123456789012345678901234567890123"}
                        """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "email", "unicode-password@mednet.test",
                        "password", "🔐".repeat(24)))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/auth/verify")
                .param("email", "not-an-email")
                .param("token", "x".repeat(43)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/auth/verify")
                .param("email", "person@mednet.test")
                .param("token", "short"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/accounts")
                .with(user(ADMIN_EMAIL).roles("SUPER_ADMIN"))
                .param("search", "a".repeat(255)))
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

        Matcher codeMatcher = Pattern.compile("six-digit MedNet code is: (\\d{6})").matcher(emailText.get());
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

        Matcher codeMatcher = Pattern.compile("six-digit MedNet code is: (\\d{6})").matcher(emailText.get());
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
    void configuredAdministratorCanSignInRepeatedly() throws Exception {
        // Regression: the configured administrator's UserDetails was a shared instance whose
        // password Spring Security erased after the first successful sign-in.
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .with(csrf())
                    .param("email", ADMIN_EMAIL)
                    .param("password", ADMIN_PASSWORD))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .param("email", ADMIN_EMAIL)
                .param("password", "not-the-administrator-password"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administratorCanLogInAndReadTheOperationalOverview() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .param("email", ADMIN_EMAIL)
                .param("password", ADMIN_PASSWORD))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie session = loginResult.getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/v1/admin/overview").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiStatus").value("UP"))
                .andExpect(jsonPath("$.modules[0].status").value("CONNECTED"))
                .andExpect(jsonPath("$.modules[1].status").value("CONNECTED"))
                .andExpect(jsonPath("$.modules[2].status").value("CONNECTED"))
                .andExpect(jsonPath("$.modules[8].status").value("CONNECTED"))
                .andExpect(jsonPath("$.modules[9].status").value("CONNECTED"))
                .andExpect(jsonPath("$.modules[10].status").value("CONNECTED"))
                .andExpect(jsonPath("$.modules[11].status").value("DISABLED"));

        mockMvc.perform(get("/api/v1/auth/admin/session").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"));

        mockMvc.perform(get("/api/v1/auth/admin/session")
                .with(user("administrator@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void administratorCannotCreateOrPromotePrivilegedAccounts() throws Exception {
        mockMvc.perform(post("/api/v1/admin/accounts")
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Unexpected Admin\",\"email\":\"unexpected-admin@example.test\",\"accountType\":\"SUPER_ADMIN\",\"password\":\"Unexpected-Admin-Password-2026\"}"))
                .andExpect(status().isBadRequest());

        MvcResult accountResult = mockMvc.perform(post("/api/v1/admin/accounts")
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Role Target\",\"email\":\"role-target@example.test\",\"accountType\":\"PATIENT\",\"password\":\"Role-Target-Password-2026\"}"))
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

        PlatformAccountEntity configuredAdmin = accounts.findFirstByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow();
        mockMvc.perform(patch("/api/v1/admin/accounts/{id}/status", configuredAdmin.getId())
                .with(user(ADMIN_EMAIL).roles("SUPER_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/admin/accounts/{id}/role", configuredAdmin.getId())
                .with(user(ADMIN_EMAIL).roles("SUPER_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountType\":\"PATIENT\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/admin/accounts/{id}", configuredAdmin.getId())
                .with(user(ADMIN_EMAIL).roles("SUPER_ADMIN"))
                .with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/admin/accounts/{id}", configuredAdmin.getId())
                .with(user("another-super-admin@example.test").roles("SUPER_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"Changed Admin","email":"%s","accountType":"SUPER_ADMIN"}
                        """.formatted(ADMIN_EMAIL)))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "MEDNET_ADMIN_EMAIL and MEDNET_ADMIN_PASSWORD")));
    }

    @Test
    void administratorCannotChangeTheStatusOfPeerOrSuperAdministratorAccounts() throws Exception {
        String peerAdminId = createAccountAs("SUPER_ADMIN", "Peer Admin", "peer-admin@example.test", "PATIENT");
        mockMvc.perform(put("/api/v1/admin/accounts/{id}", peerAdminId)
                .with(user(ADMIN_EMAIL).roles("SUPER_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Peer Admin\",\"email\":\"peer-admin@example.test\",\"accountType\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        String otherSuperId = createAccountAs("SUPER_ADMIN", "Other Super", "other-super@example.test", "PATIENT");
        mockMvc.perform(put("/api/v1/admin/accounts/{id}", otherSuperId)
                .with(user(ADMIN_EMAIL).roles("SUPER_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Other Super\",\"email\":\"other-super@example.test\",\"accountType\":\"SUPER_ADMIN\"}"))
                .andExpect(status().isOk());
        String patientId = createAccountAs("ADMIN", "Ordinary Patient", "ordinary-patient@example.test", "PATIENT");

        for (String target : java.util.List.of(peerAdminId, otherSuperId)) {
            for (String next : java.util.List.of("SUSPENDED", "ACTIVE")) {
                mockMvc.perform(patch("/api/v1/admin/accounts/{id}/status", target)
                        .with(user("administrator@example.test").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + next + "\"}"))
                        .andExpect(status().isForbidden());
            }
            PlatformAccountEntity unchanged = accounts.findById(target).orElseThrow();
            assertThat(unchanged.getStatus()).isEqualTo("ACTIVE");
        }
        assertThat(accounts.findById(peerAdminId).orElseThrow().getAccountType()).isEqualTo("ADMIN");
        assertThat(accounts.findById(otherSuperId).orElseThrow().getAccountType()).isEqualTo("SUPER_ADMIN");

        // Administrators keep their powers over non-administrative accounts.
        mockMvc.perform(patch("/api/v1/admin/accounts/{id}/status", patientId)
                .with(user("administrator@example.test").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        // Super administrators keep their powers over administrator accounts.
        mockMvc.perform(patch("/api/v1/admin/accounts/{id}/status", peerAdminId)
                .with(user("another-super-admin@example.test").roles("SUPER_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    private String createAccountAs(String actorRole, String name, String email, String type) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/accounts")
                .with(user(ADMIN_EMAIL).roles(actorRole))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"%s","email":"%s","accountType":"%s","password":"Created-Account-Password-2026"}
                        """.formatted(name, email, type)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
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
                .content("{\"displayName\":\"Test Patient\",\"email\":\"patient@example.test\",\"accountType\":\"PATIENT\",\"password\":\"Patient-Secure-Password-2026\"}"))
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

    @Test
    void superAdminCanManageAccountProfileVerificationAndSearch() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/admin/accounts")
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"Pending User","email":"pending-user@example.test","accountType":"PATIENT","password":"Pending-User-Password-2026"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Pending User"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.publicId").value(
                        org.hamcrest.Matchers.matchesRegex("PT-[0-9A-HJKMNP-TV-Z]{12}")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        String accountId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();
        String publicId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("publicId").asText();
        assertThat(accountId).hasSize(36);

        mockMvc.perform(patch("/api/v1/admin/accounts/{id}/verification", accountId)
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailVerified\":true}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/admin/accounts/{id}/verification", accountId)
                .with(user(ADMIN_EMAIL).roles("SUPER_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailVerified\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));

        mockMvc.perform(patch("/api/v1/admin/accounts/{id}/verification", accountId)
                .with(user(ADMIN_EMAIL).roles("SUPER_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailVerified\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(false));

        mockMvc.perform(put("/api/v1/admin/accounts/{id}", accountId)
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"Updated User","email":"updated-user@example.test","accountType":"PROVIDER"}
                        """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/admin/accounts/{id}", accountId)
                .with(user(ADMIN_EMAIL).roles("SUPER_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"Updated User","email":"updated-user@example.test","accountType":"PROVIDER"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated User"))
                .andExpect(jsonPath("$.email").value("updated-user@example.test"))
                .andExpect(jsonPath("$.accountType").value("PROVIDER"))
                .andExpect(jsonPath("$.publicId").value(publicId))
                .andExpect(jsonPath("$.emailVerified").value(false));

        mockMvc.perform(get("/api/v1/admin/accounts")
                .param("search", "Updated User")
                .param("accountType", "PROVIDER")
                .param("emailVerified", "false")
                .param("page", "0")
                .param("size", "50")
                .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.content[0].id").value(accountId));

        mockMvc.perform(get("/api/v1/admin/accounts")
                .param("search", "updated-user@example.test")
                .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(accountId));

        mockMvc.perform(get("/api/v1/admin/accounts")
                .param("search", publicId)
                .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(accountId));

        PlatformAccountEntity configuredAdmin = accounts.findFirstByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow();
        mockMvc.perform(patch("/api/v1/admin/accounts/{id}/verification", configuredAdmin.getId())
                .with(user(ADMIN_EMAIL).roles("SUPER_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailVerified\":false}"))
                .andExpect(status().isConflict());
    }

    @Test
    void superAdminCanEditLinkedAccountDetailsButCannotChangeItsRole() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/admin/accounts")
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"Linked Patient","email":"linked-patient@example.test","accountType":"PATIENT","password":"Linked-Patient-Password-2026"}
                        """))
                .andExpect(status().isOk())
                .andReturn();
        String accountId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();
        patientProfiles.saveAndFlush(new PatientProfileEntity(
                UUID.randomUUID().toString(),
                accountId,
                "Linked Patient",
                LocalDate.of(1990, 1, 1),
                "+231000000000",
                "Monrovia"));

        mockMvc.perform(put("/api/v1/admin/accounts/{id}", accountId)
                .with(user(ADMIN_EMAIL).roles("SUPER_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"Updated Patient","email":"linked-patient@example.test","accountType":"PATIENT"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated Patient"))
                .andExpect(jsonPath("$.accountType").value("PATIENT"));

        mockMvc.perform(put("/api/v1/admin/accounts/{id}", accountId)
                .with(user(ADMIN_EMAIL).roles("SUPER_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"Updated Patient","email":"linked-patient@example.test","accountType":"PROVIDER"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "account type cannot be changed")));
    }

}
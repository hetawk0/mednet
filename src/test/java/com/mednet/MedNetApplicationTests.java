package com.mednet;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import com.mednet.record.data.ClinicalRecordRepository;
import com.mednet.record.data.PatientProviderRecordConsentRepository;
import com.mednet.medication.app.MedicationService;
import com.mednet.medication.data.MedicationScheduleEntity;
import com.mednet.medication.data.MedicationScheduleRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
        "server.servlet.session.cookie.secure=false",
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
    private ProviderApplicationRepository providerApplications;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private ProviderAvailabilitySlotRepository availabilitySlots;

    @Autowired
    private ClinicalRecordRepository clinicalRecords;

    @Autowired
    private PatientProviderRecordConsentRepository recordConsents;

    @Autowired
    private MedicationScheduleRepository medicationSchedules;

    @Autowired
    private MedicationService medicationService;

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
                .andExpect(jsonPath("$.patientAccountId").value(patientAccount.getId()));

        mockMvc.perform(patch("/api/v1/appointments/{id}", appointmentId)
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"REQUEST_RESCHEDULE\",\"proposedAvailabilitySlotId\":\""
                        + secondSlotId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESCHEDULE_REQUESTED"));

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
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(patch("/api/v1/appointments/{id}", appointmentId)
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"CANCEL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

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
        PlatformAccountEntity providerAccount = new PlatformAccountEntity(
                UUID.randomUUID().toString(), providerEmail, "PROVIDER");
        providerAccount.verifyEmail();
        accounts.save(providerAccount);

        ProviderApplicationEntity provider = new ProviderApplicationEntity(
                UUID.randomUUID().toString(), "Guarded Provider", providerEmail, "Family Medicine", "LIC-GUARD-1");
        provider.review("APPROVED", ADMIN_EMAIL);
        providerApplications.save(provider);
        ProviderAvailabilitySlotEntity slot = availabilitySlots.save(new ProviderAvailabilitySlotEntity(
                UUID.randomUUID().toString(),
                provider.getId(),
                Instant.now().plusSeconds(7200),
                Instant.now().plusSeconds(9000)));
        AppointmentEntity appointment = new AppointmentEntity(
                UUID.randomUUID().toString(), patient.getId(), provider.getId(), slot.getId());
        appointment.confirm();
        appointments.save(appointment);

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
        mockMvc.perform(post("/api/v1/patients/me/record-consents")
                .with(user(patientEmail).roles("PATIENT"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerId\":\"" + provider.getId() + "\"}"))
                .andExpect(status().isOk());

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
                .andExpect(jsonPath("$.content[0].category").value("VITAL"))
                .andExpect(jsonPath("$.content[0].authorName").value("Patient"));

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
        MvcResult homeCareAccountResult = mockMvc.perform(post("/api/v1/admin/accounts")
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"homecare-staff@mednet.test","accountType":"HOME_CARE"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("HOME_CARE"))
                .andReturn();
        String homeCareStaffId = objectMapper.readTree(homeCareAccountResult.getResponse().getContentAsString())
                .get("id").asText();
        mockMvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"homecare-staff@mednet.test","password":"HomeCare-Secure-Password-2026"}
                        """))
                .andExpect(status().isAccepted());
        PlatformAccountEntity homeCareStaff = accounts.findById(homeCareStaffId).orElseThrow();
        homeCareStaff.verifyEmail();
        homeCareStaff.setPasswordHash(passwordEncoder.encode("HomeCare-Secure-Password-2026"));
        accounts.saveAndFlush(homeCareStaff);
        assertThat(userDetailsService.loadUserByUsername("homecare-staff@mednet.test").getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_HOME_CARE");
        mockMvc.perform(get("/api/v1/admin/service-requests")
                .param("type", "HOME_CARE")
                .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requestId))
                .andExpect(jsonPath("$[0].requestedService").doesNotExist());
        mockMvc.perform(patch("/api/v1/admin/service-requests/{id}/assignment", requestId)
                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"staffAccountId":"%s"}
                        """.formatted(homeCareStaff.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedStaffAccountId").value(homeCareStaff.getId()));
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
        Instant homeCareScheduledAt = Instant.now().plusSeconds(3600);
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
                        {"email":"lab-staff@mednet.test","accountType":"LABORATORY"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("LABORATORY"))
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
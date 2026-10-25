package com.mednet;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import jakarta.servlet.http.Cookie;
import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.auth.email.EkdSendEmailService;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Abuse protection and session control for the authentication endpoints, with the default limits.
 * Each test uses its own addresses and accounts because the limiter is shared by the context.
 */
@SpringBootTest(properties = {
        "server.servlet.session.cookie.secure=false",
        "spring.datasource.url=jdbc:h2:mem:mednet-abuse;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "mednet.admin.email=abuse-admin@mednet.test",
        "mednet.admin.password=Abuse-Test-Admin-Password-2026",
        "mednet.rate-limit.trusted-proxies=10.0.0.1"
})
@AutoConfigureMockMvc
class AuthenticationAbuseTests {

    private static final String PASSWORD = "Correct-Horse-Battery-2026";
    private static final String SUPER_ADMIN = "abuse-admin@mednet.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformAccountRepository accounts;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SessionRegistry sessionRegistry;

    @Autowired
    private MutableClock clock;

    @MockitoBean
    private EkdSendEmailService emailService;

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Test
    void repeatedFailedSignInsAreLimitedPerAccountUntilTheWindowPasses() throws Exception {
        String email = verifiedAccount("PATIENT");
        for (int attempt = 0; attempt < 10; attempt++) {
            signIn(email, "Wrong-Password-" + attempt, "198.51.100.10").andExpect(status().isUnauthorized());
        }

        // Even the right password is refused now, with retry guidance and no account detail.
        signIn(email, PASSWORD, "198.51.100.10")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.error.message").value("Too many attempts. Please wait and try again later."));

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        signIn(email, PASSWORD, "198.51.100.10").andExpect(status().isNoContent());
    }

    @Test
    void guessingOneAccountFromManyAddressesIsStillLimited() throws Exception {
        String email = verifiedAccount("PATIENT");
        for (int attempt = 0; attempt < 10; attempt++) {
            signIn(email, "Wrong-Password-" + attempt, "198.51.101." + attempt).andExpect(status().isUnauthorized());
        }
        signIn(email, PASSWORD, "198.51.101.200").andExpect(status().isTooManyRequests());
    }

    @Test
    void oneAddressIsLimitedAcrossAccountsWhileOtherAddressesAreNot() throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            signIn(randomEmail(), "Wrong-Password-2026", "198.51.102.1").andExpect(status().isUnauthorized());
        }
        String email = verifiedAccount("PATIENT");
        signIn(email, PASSWORD, "198.51.102.1").andExpect(status().isTooManyRequests());
        signIn(email, PASSWORD, "198.51.102.2").andExpect(status().isNoContent());
    }

    @Test
    void forwardingHeadersFromUntrustedClientsCannotEscapeTheLimit() throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            mockMvc.perform(withClient(post("/api/v1/auth/login"), "203.0.113.50")
                    .header("X-Forwarded-For", "192.0.2." + attempt)
                    .with(csrf())
                    .param("email", randomEmail())
                    .param("password", "Wrong-Password-2026"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(withClient(post("/api/v1/auth/login"), "203.0.113.50")
                .header("X-Forwarded-For", "192.0.2.250")
                .with(csrf())
                .param("email", randomEmail())
                .param("password", "Wrong-Password-2026"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void aTrustedProxyForwardsEachClientsOwnAllowance() throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            mockMvc.perform(withClient(post("/api/v1/auth/login"), "10.0.0.1")
                    .header("X-Forwarded-For", "192.0.2.77")
                    .with(csrf())
                    .param("email", randomEmail())
                    .param("password", "Wrong-Password-2026"))
                    .andExpect(status().isUnauthorized());
        }
        // The limited client is the forwarded one; a client prepending a fake hop does not escape.
        mockMvc.perform(withClient(post("/api/v1/auth/login"), "10.0.0.1")
                .header("X-Forwarded-For", "198.18.0.1, 192.0.2.77")
                .with(csrf())
                .param("email", randomEmail())
                .param("password", "Wrong-Password-2026"))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(withClient(post("/api/v1/auth/login"), "10.0.0.1")
                .header("X-Forwarded-For", "192.0.2.78")
                .with(csrf())
                .param("email", randomEmail())
                .param("password", "Wrong-Password-2026"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetRequestsAreLimitedAlikeForUnknownAndKnownAddresses() throws Exception {
        when(emailService.send(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        String known = verifiedAccount("PATIENT");
        String unknown = randomEmail();
        for (String email : new String[] { known, unknown }) {
            for (int attempt = 0; attempt < 5; attempt++) {
                forgotPassword(email, "198.51.103." + attempt)
                        .andExpect(status().isAccepted())
                        .andExpect(jsonPath("$.message").value(
                                "If an account exists for this email, password reset instructions will arrive shortly."));
            }
            forgotPassword(email, "198.51.103.99")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
        }
    }

    @Test
    void resetCodeGuessingIsLimitedPerAccountAcrossAddresses() throws Exception {
        when(emailService.send(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        String email = verifiedAccount("PATIENT");
        forgotPassword(email, "198.51.104.1").andExpect(status().isAccepted());
        for (int attempt = 0; attempt < 10; attempt++) {
            verifyResetCode(email, "000000", "198.51.104." + (10 + attempt)).andExpect(status().isBadRequest());
        }
        verifyResetCode(email, "000000", "198.51.104.200")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void verificationEmailsAreLimitedAcrossSignUpAndResend() throws Exception {
        when(emailService.send(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        String email = randomEmail();
        json(post("/api/v1/auth/register"), "198.51.105.1", Map.of("email", email, "password", PASSWORD))
                .andExpect(status().isAccepted());
        for (int attempt = 0; attempt < 4; attempt++) {
            json(post("/api/v1/auth/resend-verification"), "198.51.105." + (2 + attempt), Map.of("email", email))
                    .andExpect(status().isAccepted());
        }
        json(post("/api/v1/auth/resend-verification"), "198.51.105.50", Map.of("email", email))
                .andExpect(status().isTooManyRequests());
        json(post("/api/v1/auth/register"), "198.51.105.51", Map.of("email", email, "password", PASSWORD))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void verificationLinkChecksAreLimitedPerClient() throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            mockMvc.perform(withClient(get("/api/v1/auth/verify"), "198.51.106.1")
                    .param("email", randomEmail())
                    .param("token", "a".repeat(43)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(withClient(get("/api/v1/auth/verify"), "198.51.106.1")
                .param("email", randomEmail())
                .param("token", "a".repeat(43)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void signOutEndsTheSession() throws Exception {
        String email = verifiedAccount("PATIENT");
        Cookie session = signedIn(email, "198.51.107.1");
        mockMvc.perform(get("/api/v1/auth/session").cookie(session)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout").cookie(session).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/session").cookie(session)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patients/me/profile").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionsEndAfterTheAbsoluteLifetimeEvenWhenActive() throws Exception {
        String email = verifiedAccount("PATIENT");
        Cookie session = signedIn(email, "198.51.108.1");
        clock.advance(Duration.ofHours(11));
        mockMvc.perform(get("/api/v1/auth/session").cookie(session)).andExpect(status().isOk());

        clock.advance(Duration.ofHours(1).plusSeconds(1));
        mockMvc.perform(get("/api/v1/auth/session").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void jdbcSessionRepositoryUsesTheSharedSessionRegistry() {
        assertThat(sessionRegistry).isInstanceOf(SpringSessionBackedSessionRegistry.class);
    }

    @Test
    void completingAPasswordResetSignsOutExistingSessions() throws Exception {
        when(emailService.send(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        String email = verifiedAccount("PATIENT");
        Cookie session = signedIn(email, "198.51.109.1");

        forgotPassword(email, "198.51.109.2").andExpect(status().isAccepted());
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).send(eq(email), anyString(), anyString(), text.capture());
        Matcher code = Pattern.compile("(\\d{6})").matcher(text.getValue());
        assertThat(code.find()).isTrue();
        String ticket = objectMapper.readTree(verifyResetCode(email, code.group(1), "198.51.109.2")
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("token").asText();
        json(post("/api/v1/auth/reset-password"), "198.51.109.2",
                Map.of("email", email, "token", ticket, "password", "Brand-New-Password-2026"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/session").cookie(session)).andExpect(status().isUnauthorized());
        signIn(email, "Brand-New-Password-2026", "198.51.109.3").andExpect(status().isNoContent());
    }

    @Test
    void suspensionAndRoleChangesSignOutExistingSessions() throws Exception {
        String suspended = verifiedAccount("PATIENT");
        Cookie suspendedSession = signedIn(suspended, "198.51.110.1");
        String regraded = verifiedAccount("LABORATORY");
        Cookie regradedSession = signedIn(regraded, "198.51.110.2");

        mockMvc.perform(patch("/api/v1/admin/accounts/{id}/status", idOf(suspended))
                .with(user(SUPER_ADMIN).roles("SUPER_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/admin/accounts/{id}/role", idOf(regraded))
                .with(user(SUPER_ADMIN).roles("SUPER_ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountType\":\"HOME_CARE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/session").cookie(suspendedSession))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/partner/lab-results").cookie(regradedSession))
                .andExpect(status().isUnauthorized());
        signIn(suspended, PASSWORD, "198.51.110.3").andExpect(status().isUnauthorized());
    }

    private String verifiedAccount(String accountType) {
        String email = randomEmail();
        PlatformAccountEntity account = new PlatformAccountEntity(UUID.randomUUID().toString(), email, accountType);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account.verifyEmail();
        accounts.saveAndFlush(account);
        return email;
    }

    private String idOf(String email) {
        return accounts.findFirstByEmailIgnoreCase(email).orElseThrow().getId();
    }

    private Cookie signedIn(String email, String client) throws Exception {
        Cookie session = signIn(email, PASSWORD, client)
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        return session;
    }

    private org.springframework.test.web.servlet.ResultActions signIn(String email, String password, String client)
            throws Exception {
        return mockMvc.perform(withClient(post("/api/v1/auth/login"), client)
                .with(csrf())
                .param("email", email)
                .param("password", password));
    }

    private org.springframework.test.web.servlet.ResultActions forgotPassword(String email, String client)
            throws Exception {
        return json(post("/api/v1/auth/forgot-password"), client, Map.of("email", email));
    }

    private org.springframework.test.web.servlet.ResultActions verifyResetCode(String email, String code, String client)
            throws Exception {
        return json(post("/api/v1/auth/reset-password/verify"), client, Map.of("email", email, "code", code));
    }

    private org.springframework.test.web.servlet.ResultActions json(
            MockHttpServletRequestBuilder request, String client, Map<String, String> body) throws Exception {
        return mockMvc.perform(withClient(request, client)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private static MockHttpServletRequestBuilder withClient(MockHttpServletRequestBuilder request, String address) {
        RequestPostProcessor peer = mock -> {
            mock.setRemoteAddr(address);
            return mock;
        };
        return request.with(peer);
    }

    private static String randomEmail() {
        return "abuse-" + UUID.randomUUID() + "@mednet.test";
    }

    /** Real time plus an offset: sessions record real creation times, and tests jump ahead on request. */
    static final class MutableClock extends Clock {

        private Duration offset = Duration.ZERO;

        synchronized void advance(Duration duration) {
            offset = offset.plus(duration);
        }

        @Override
        public synchronized Instant instant() {
            return Instant.now().plus(offset);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}

package com.mednet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.auth.security.AccountSessions;
import com.mednet.auth.security.AuthRateLimitProperties.Limit;
import com.mednet.auth.security.AuthRateLimiter;
import com.mednet.auth.security.PostgreSqlRateLimitCounterStore;

@SpringBootTest(properties = {
        "server.servlet.session.cookie.secure=false"
})
class PostgreSqlIntegrationIT {

    @DynamicPropertySource
    static void registerPostgreSqlProperties(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformAccountRepository accounts;

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @Test
    void migrationsApplyAndValidateOnPostgreSql() {
        flyway.validate();

        assertThat(jdbcTemplate.queryForObject("SELECT version()", String.class))
                .startsWith("PostgreSQL");
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    @Transactional
    void platformAccountCanBePersistedAndReadBackFromPostgreSql() {
        String email = "postgres-" + UUID.randomUUID() + "@mednet.test";
        PlatformAccountEntity account = new PlatformAccountEntity(UUID.randomUUID().toString(), email, "PATIENT");
        account.verifyEmail();

        accounts.saveAndFlush(account);
        entityManager.clear();

        PlatformAccountEntity persisted = accounts.findFirstByEmailIgnoreCase(email).orElseThrow();
        assertThat(persisted.getEmail()).isEqualTo(email);
        assertThat(persisted.getAccountType()).isEqualTo("PATIENT");
        assertThat(persisted.isEmailVerified()).isTrue();
    }

    @Test
    void accountSessionInvalidationIsVisibleAcrossRegistryInstances() {
        String email = "u".repeat(64) + "@"
                + "a".repeat(63) + "."
                + "b".repeat(63) + "."
                + "c".repeat(61);
        assertThat(email).hasSize(254);
        var principal = User.withUsername(email).password("").roles("PATIENT").build();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        var session = saveAuthenticatedSession(sessionRepository, authentication);

        SessionRegistry firstInstanceRegistry = new SpringSessionBackedSessionRegistry<>(sessionRepository);
        SessionRegistry secondInstanceRegistry = new SpringSessionBackedSessionRegistry<>(sessionRepository);
        try {
            assertThat(firstInstanceRegistry.getAllSessions(principal, false)).hasSize(1);

            new AccountSessions(firstInstanceRegistry).endAll(email);

            assertThat(secondInstanceRegistry.getAllSessions(principal, false)).isEmpty();
        } finally {
            sessionRepository.deleteById(session.getId());
        }
    }

    private static <S extends Session> S saveAuthenticatedSession(
            FindByIndexNameSessionRepository<S> repository,
            Authentication authentication) {
        S session = repository.createSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(authentication));
        repository.save(session);
        return session;
    }

    @Test
    void rateLimitCountersAreSharedAcrossStoreInstances() {
        var firstInstance = new PostgreSqlRateLimitCounterStore(jdbcTemplate);
        var secondInstance = new PostgreSqlRateLimitCounterStore(jdbcTemplate);
        var limit = new Limit(2, Duration.ofMinutes(5));
        var now = Instant.now();
        String keyHash = AuthRateLimiter.accountKey("rate-limit-" + UUID.randomUUID() + "@mednet.test");

        assertThat(firstInstance.increment("integration", "account", keyHash, now, limit).count()).isEqualTo(1);
        assertThat(secondInstance.increment("integration", "account", keyHash, now, limit).count()).isEqualTo(2);
        assertThat(firstInstance.find("integration", "account", keyHash, now, limit).orElseThrow().count())
                .isEqualTo(2L);

        firstInstance.reset("integration", "account", keyHash);
    }
}

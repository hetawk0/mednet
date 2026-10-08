package com.mednet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;

@SpringBootTest(properties = {
        "mednet.google.enabled=false",
        "server.servlet.session.cookie.secure=false"
})
class PostgreSqlIntegrationIT {

    @DynamicPropertySource
    static void registerPostgreSqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredSetting("MEDNET_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> requiredSetting("MEDNET_TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> requiredSetting("MEDNET_TEST_DATABASE_PASSWORD"));
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformAccountRepository accounts;

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

    private static String requiredSetting(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must point to a dedicated PostgreSQL test database");
        }
        return value;
    }
}

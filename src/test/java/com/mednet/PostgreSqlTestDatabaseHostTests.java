package com.mednet;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The PostgreSQL integration profile must not reach a hosted database by accident. */
class PostgreSqlTestDatabaseHostTests {

    @Test
    void localHostsAreAccepted() {
        for (String url : new String[] {
                "jdbc:postgresql://localhost:5432/mednet_test",
                "jdbc:postgresql://localhost/mednet_test",
                "jdbc:postgresql://127.0.0.1:5432/mednet_test?sslmode=disable",
                "jdbc:postgresql://[::1]:5432/mednet_test"}) {
            assertThatCode(() -> PostgreSqlTestDatabase.requireLocalHost(url, false)).doesNotThrowAnyException();
        }
    }

    @Test
    void remoteHostsAreRefusedBeforeConnecting() {
        for (String url : new String[] {
                "jdbc:postgresql://aws-0-example.pooler.supabase.com:5432/mednet_test?sslmode=require",
                "jdbc:postgresql://db.example.test/mednet_test",
                "jdbc:postgresql://localhost:5432,db.example.test:5432/mednet_test",
                "jdbc:postgresql://localhost.example.test/mednet_test"}) {
            assertThatThrownBy(() -> PostgreSqlTestDatabase.requireLocalHost(url, false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("only localhost is allowed");
        }
    }

    @Test
    void remoteHostsNeedAnExplicitOptIn() {
        assertThatCode(() -> PostgreSqlTestDatabase.requireLocalHost("jdbc:postgresql://db.example.test/mednet_test", true))
                .doesNotThrowAnyException();
    }

    @Test
    void urlsWithoutAHostAreRefused() {
        assertThatThrownBy(() -> PostgreSqlTestDatabase.requireLocalHost("jdbc:postgresql:mednet_test", false))
                .isInstanceOf(IllegalStateException.class);
    }
}

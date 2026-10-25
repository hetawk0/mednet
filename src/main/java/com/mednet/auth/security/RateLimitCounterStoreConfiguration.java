package com.mednet.auth.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
class RateLimitCounterStoreConfiguration {

    @Bean
    @ConditionalOnExpression("'${spring.datasource.url:}'.startsWith('jdbc:postgresql:')")
    RateLimitCounterStore postgreSqlRateLimitCounterStore(JdbcTemplate jdbc) {
        return new PostgreSqlRateLimitCounterStore(jdbc);
    }

    @Bean
    @ConditionalOnExpression("!'${spring.datasource.url:}'.startsWith('jdbc:postgresql:')")
    RateLimitCounterStore inMemoryRateLimitCounterStore(AuthRateLimitProperties properties) {
        return new InMemoryRateLimitCounterStore(properties.getMaxTrackedKeys());
    }
}

package com.mednet;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

@SpringBootApplication
public class MedNetApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(MedNetApplication.class);
        application.addInitializers(context -> {
            Map<String, Object> normalizedEnvironment = new LinkedHashMap<>();
            String[] serverKeys = {
                    "SPRING_DATASOURCE_URL",
                    "SPRING_DATASOURCE_USERNAME",
                    "SPRING_DATASOURCE_PASSWORD",
                    "MEDNET_ADMIN_EMAIL",
                    "MEDNET_ADMIN_PASSWORD",
                    "SESSION_COOKIE_SECURE",
                    "GOOGLE_OAUTH_ENABLED",
                    "GOOGLE_CLIENT_ID",
                    "GOOGLE_CLIENT_SECRET",
                    "GOOGLE_REDIRECT_URI"
            };
            for (String key : serverKeys) {
                String value = normalizeEnvironmentValue(context.getEnvironment().getProperty(key));
                if (value != null)
                    normalizedEnvironment.put(key, value);
            }
            if (normalizedEnvironment.containsKey("SPRING_DATASOURCE_URL")) {
                normalizedEnvironment.put("spring.datasource.url", normalizedEnvironment.get("SPRING_DATASOURCE_URL"));
            }
            if (normalizedEnvironment.containsKey("SPRING_DATASOURCE_USERNAME")) {
                normalizedEnvironment.put("spring.datasource.username",
                        normalizedEnvironment.get("SPRING_DATASOURCE_USERNAME"));
            }
            if (normalizedEnvironment.containsKey("SPRING_DATASOURCE_PASSWORD")) {
                normalizedEnvironment.put("spring.datasource.password",
                        normalizedEnvironment.get("SPRING_DATASOURCE_PASSWORD"));
            }
            String googleClientId = normalizeEnvironmentValue(context.getEnvironment().getProperty("GOOGLE_CLIENT_ID"));
            String googleClientSecret = normalizeEnvironmentValue(
                    context.getEnvironment().getProperty("GOOGLE_CLIENT_SECRET"));
            if (StringUtils.hasText(googleClientId) && StringUtils.hasText(googleClientSecret)) {
                normalizedEnvironment.put("mednet.google.enabled", true);
            }
            if (!normalizedEnvironment.isEmpty()) {
                context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("mednet-server-environment", normalizedEnvironment));
            }
            if (StringUtils.hasText(context.getEnvironment().getProperty("spring.datasource.url")))
                return;

            Set<String> exclusions = new LinkedHashSet<>();
            String configuredExclusions = context.getEnvironment().getProperty("spring.autoconfigure.exclude", "");
            for (String exclusion : configuredExclusions.split(",")) {
                if (StringUtils.hasText(exclusion))
                    exclusions.add(exclusion.trim());
            }
            exclusions.add("org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration");
            exclusions.add("org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "mednet-no-database-mode",
                    java.util.Map.of("spring.autoconfigure.exclude", String.join(",", exclusions))));
        });
        application.run(args);
    }

    static String normalizeEnvironmentValue(String value) {
        if (value == null || value.length() < 2)
            return value;
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
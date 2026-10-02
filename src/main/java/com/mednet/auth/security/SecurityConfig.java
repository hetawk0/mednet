package com.mednet.auth.security;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import jakarta.servlet.http.HttpServletResponse;
import static org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService adminUserDetailsService(
            @Value("${mednet.admin.email:}") String adminEmail,
            @Value("${mednet.admin.password:}") String adminPassword,
            PasswordEncoder passwordEncoder) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            return username -> {
                throw new UsernameNotFoundException("Administrator access is not configured");
            };
        }

        String normalizedEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        UserDetails administrator = User.withUsername(normalizedEmail)
                .password(passwordEncoder.encode(adminPassword))
                .roles("ADMIN")
                .build();

        return username -> {
            if (normalizedEmail.equals(username.trim().toLowerCase(Locale.ROOT))) {
                return administrator;
            }
            throw new UsernameNotFoundException("Administrator account not found");
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookies) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .sameSite("Lax")
                .secure(secureCookies));

        AuthenticationSuccessHandler loginSuccess = (request, response, authentication) ->
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        AuthenticationFailureHandler loginFailure = (request, response, exception) ->
                writeUnauthorized(response, "Invalid administrator credentials");

        http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/api/v1/auth/csrf",
                                "/api/v1/auth/admin/login")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**", "/api/v1/auth/admin/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .permitAll())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/v1/auth/admin/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(loginSuccess)
                        .failureHandler(loginFailure))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/admin/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeUnauthorized(response, "Administrator authentication required")))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.migrateSession()));

        return http.build();
    }

    private static void writeUnauthorized(HttpServletResponse response, String message)
            throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
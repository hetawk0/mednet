package com.mednet.auth.security;

import java.util.Locale;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import static org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.api.ApiRequestIdFilter;
import com.mednet.provider.data.ProviderApplicationRepository;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(
            @Value("${mednet.admin.email:}") String adminEmail,
            @Value("${mednet.admin.password:}") String adminPassword,
            PasswordEncoder passwordEncoder,
            ObjectProvider<PlatformAccountRepository> accountsProvider,
            ObjectProvider<ProviderApplicationRepository> providersProvider) {
        String normalizedAdminEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        UserDetails administrator = normalizedAdminEmail.isBlank() || adminPassword.isBlank()
                ? null
                : User.withUsername(normalizedAdminEmail)
                        .password(passwordEncoder.encode(adminPassword))
                        .roles("SUPER_ADMIN")
                        .build();

        return username -> {
            String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
            if (administrator != null && normalizedAdminEmail.equals(normalizedUsername)) {
                return administrator;
            }
            PlatformAccountRepository accounts = accountsProvider.getIfAvailable();
            if (accounts == null) {
                throw new UsernameNotFoundException("Database-backed account sign-in is unavailable");
            }
            PlatformAccountEntity account = accounts.findFirstByEmailIgnoreCase(normalizedUsername)
                    .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
            if (account.getPasswordHash() == null) {
                throw new UsernameNotFoundException("Password sign-in is not configured for this account");
            }
            String role = account.getAccountType();
            if (role.equals("PATIENT") || role.equals("PROVIDER")) {
                ProviderApplicationRepository providers = providersProvider.getIfAvailable();
                boolean approvedProvider = providers != null
                        && providers.findFirstByEmailIgnoreCaseAndStatusOrderByReviewedAtDesc(
                                normalizedUsername, "APPROVED").isPresent();
                role = approvedProvider ? "PROVIDER" : "PATIENT";
            }
            return User.withUsername(account.getEmail())
                    .password(account.getPasswordHash())
                    .roles(role)
                    .disabled("SUSPENDED".equals(account.getStatus()))
                    .accountExpired(!account.isEmailVerified())
                    .build();
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<GoogleOAuth2UserService> googleUserService,
            ObjectProvider<ClientRegistrationRepository> registrations,
            @Value("${mednet.google.enabled:false}") boolean googleEnabled,
            @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookies,
            ObjectMapper objectMapper) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .sameSite("Lax")
                .secure(secureCookies));

        AuthenticationSuccessHandler loginSuccess = (request, response, authentication) -> response
                .setStatus(HttpServletResponse.SC_NO_CONTENT);
        http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/error")
                        .permitAll()
                        .requestMatchers(
                                "/api-docs",
                                "/api-docs/**",
                                "/swagger-ui",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(
                                "/api/v1/auth/csrf",
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/verify",
                                "/api/v1/auth/resend-verification",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password/verify",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/google/status",
                                "/api/v1/auth/oauth2/**")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**", "/api/v1/auth/admin/**")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .anyRequest()
                        .permitAll())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/v1/auth/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(loginSuccess)
                        .failureHandler((request, response, exception) -> writeError(
                                objectMapper, request, response, HttpServletResponse.SC_UNAUTHORIZED,
                                "UNAUTHORIZED", "Invalid email or password")))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response
                                .setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeError(
                                objectMapper, request, response, HttpServletResponse.SC_UNAUTHORIZED,
                                "UNAUTHORIZED", "Sign-in required"))
                        .accessDeniedHandler((request, response, exception) -> writeError(
                                objectMapper, request, response, HttpServletResponse.SC_FORBIDDEN,
                                "FORBIDDEN", "You do not have permission to perform this action")))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.migrateSession()));

        GoogleOAuth2UserService configuredUserService = googleUserService.getIfAvailable();
        if (googleEnabled && configuredUserService != null && registrations.getIfAvailable() != null) {
            http.oauth2Login(oauth2 -> oauth2
                    .authorizationEndpoint(endpoint -> endpoint.baseUri("/api/v1/auth/oauth2/authorization"))
                    .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/v1/auth/oauth2/callback/*"))
                    .userInfoEndpoint(endpoint -> endpoint.userService(configuredUserService))
                    .successHandler((request, response, authentication) -> {
                        boolean isAdmin = authentication.getAuthorities().stream()
                                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")
                                        || authority.getAuthority().equals("ROLE_SUPER_ADMIN"));
                        response.sendRedirect(isAdmin ? "/admin" : "/account");
                    })
                    .failureHandler((request, response, exception) -> {
                        if (exception instanceof OAuth2AuthenticationException oauthException) {
                            String errorCode = oauthException.getError().getErrorCode();
                            if ("mednet_authentication_error".equals(errorCode)) {
                                log.warn("Google sign-in rejected by MedNet account policy: {}",
                                        oauthException.getError().getDescription());
                            } else {
                                log.error("Google OAuth flow failed with error code {}", errorCode, exception);
                            }
                        } else {
                            log.error("Google OAuth flow failed with {}",
                                    exception.getClass().getSimpleName(), exception);
                        }
                        response.sendRedirect("/sign-in?error=google");
                    }));
        }

        return http.build();
    }

    private static void writeError(
            ObjectMapper objectMapper,
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String message)
            throws java.io.IOException {
        String requestId = (String) request.getAttribute(ApiRequestIdFilter.ATTRIBUTE_NAME);
        response.setStatus(status);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "success", false,
                "error", Map.of("code", code, "message", message, "details", List.of()),
                "detail", message,
                "meta", Map.of("requestId", requestId == null ? "" : requestId)));
    }
}
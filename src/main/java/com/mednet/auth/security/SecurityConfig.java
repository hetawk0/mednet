package com.mednet.auth.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
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
@EnableConfigurationProperties(AuthRateLimitProperties.class)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Shared session index lets account changes invalidate sessions on every API instance. */
    @Bean
    SessionRegistry sessionRegistry(
            ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> repositories) {
        FindByIndexNameSessionRepository<? extends Session> repository = repositories.getIfAvailable();
        return repository == null
                ? new SessionRegistryImpl()
                : new SpringSessionBackedSessionRegistry<>(repository);
    }

    /** Removes destroyed sessions from the registry, keeping it bounded by live sessions. */
    @Bean
    static HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    UserDetailsService userDetailsService(
            @Value("${mednet.admin.email:}") String adminEmail,
            @Value("${mednet.admin.password:}") String adminPassword,
            PasswordEncoder passwordEncoder,
            ObjectProvider<PlatformAccountRepository> accountsProvider,
            ObjectProvider<ProviderApplicationRepository> providersProvider) {
        String normalizedAdminEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        String administratorPasswordHash = normalizedAdminEmail.isBlank() || adminPassword.isBlank()
                ? null
                : passwordEncoder.encode(adminPassword);

        return username -> {
            String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
            if (administratorPasswordHash != null && normalizedAdminEmail.equals(normalizedUsername)) {
                // A new instance per lookup: Spring Security erases the credentials of the
                // authenticated principal, so a shared instance only allows one sign-in.
                return User.withUsername(normalizedAdminEmail)
                        .password(administratorPasswordHash)
                        .roles("SUPER_ADMIN")
                        .build();
            }
            PlatformAccountRepository accounts = accountsProvider.getIfAvailable();
            if (accounts == null) {
                throw new InternalAuthenticationServiceException(
                        "Database-backed account sign-in is unavailable");
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
            return User.withUsername(normalizedUsername)
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
            @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookies,
            @Value("${mednet.session.absolute-timeout:12h}") Duration absoluteSessionTimeout,
            @Value("${GOOGLE_CLIENT_ID:}") String googleClientId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String googleClientSecret,
            AuthRateLimiter rateLimiter,
            ClientAddressResolver clientAddresses,
            SessionRegistry sessionRegistry,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            ObjectProvider<GoogleOidcUserService> googleUserServices,
            Clock clock,
            ObjectMapper objectMapper) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .sameSite("Lax")
                .secure(secureCookies));

        AuthenticationSuccessHandler loginSuccess = (request, response, authentication) -> {
            SignInRateLimitFilter.clearAccount(rateLimiter, request);
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        };
        SignInRateLimitFilter.RateLimitResponseWriter rateLimited =
                (request, response, exception) -> writeRateLimited(objectMapper, request, response, exception);
        http
                .csrf(csrf -> {
                    csrf.csrfTokenRepository(csrfTokenRepository);
                })
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
                                "/api/v1/auth/verify-code",
                                "/api/v1/auth/resend-verification",
                                "/api/v1/auth/google/status",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password/verify",
                                "/api/v1/auth/reset-password")
                        .permitAll()
                        // The public provider directory, one provider's entry, specialty counts and open times.
                        .requestMatchers(HttpMethod.GET, "/api/v1/providers", "/api/v1/providers/*", "/api/v1/providers/*/availability")
                        .permitAll()
                        .requestMatchers("/oauth2/authorization/**", "/login/oauth2/code/**")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**", "/api/v1/auth/admin/**")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        // Deny by default: every other endpoint needs a signed-in account, and the
                        // controllers and services still check the role and ownership.
                        .anyRequest()
                        .authenticated())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/v1/auth/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(loginSuccess)
                        .failureHandler(loginFailureHandler(objectMapper, rateLimiter, clientAddresses)))
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
                        .sessionFixation(fixation -> fixation.migrateSession())
                        .sessionConcurrency(concurrency -> concurrency
                                .maximumSessions(-1)
                                .sessionRegistry(sessionRegistry)
                                .expiredSessionStrategy(event -> writeError(
                                        objectMapper, event.getRequest(), event.getResponse(),
                                        HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Sign-in required"))));

        if (!googleClientId.isBlank()
                && !googleClientSecret.isBlank()
                && clientRegistrations.getIfAvailable() != null) {
            OAuth2UserService<OidcUserRequest, OidcUser> googleUserService =
                    googleUserServices.getObject();
            http.oauth2Login(oauth -> oauth
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(googleUserService))
                    .successHandler((request, response, authentication) ->
                            response.sendRedirect(roleDestination(authentication)))
                    .failureHandler((request, response, exception) -> {
                        log.warn("Google sign-in failed ({})", exception.getClass().getSimpleName());
                        response.sendRedirect("/sign-in?googleError=1");
                    }));
        }

        http.addFilterBefore(
                new SignInRateLimitFilter(rateLimiter, clientAddresses, rateLimited),
                UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(
                new SessionLifetimeFilter(absoluteSessionTimeout, clock),
                SecurityContextHolderFilter.class);

        return http.build();
    }

    static AuthenticationFailureHandler loginFailureHandler(ObjectMapper objectMapper) {
        return loginFailureHandler(objectMapper, null, null);
    }

    static AuthenticationFailureHandler loginFailureHandler(
            ObjectMapper objectMapper, AuthRateLimiter rateLimiter, ClientAddressResolver clientAddresses) {
        return (request, response, exception) -> {
            if (exception instanceof InternalAuthenticationServiceException) {
                String requestId = (String) request.getAttribute(ApiRequestIdFilter.ATTRIBUTE_NAME);
                log.error("Authentication account lookup failed for request {}", requestId, exception);
                writeError(
                        objectMapper, request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "SERVICE_UNAVAILABLE", "Sign-in is temporarily unavailable");
                return;
            }
            if (rateLimiter != null) {
                SignInRateLimitFilter.recordFailure(rateLimiter, clientAddresses, request);
            }
            writeError(
                    objectMapper, request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHORIZED", "Invalid email or password");
        };
    }

    private static void writeRateLimited(
            ObjectMapper objectMapper,
            HttpServletRequest request,
            HttpServletResponse response,
            RateLimitExceededException exception)
            throws java.io.IOException {
        response.setHeader("Retry-After", Long.toString(exception.getRetryAfterSeconds()));
        writeError(objectMapper, request, response, 429, "RATE_LIMITED", RateLimitExceededException.MESSAGE);
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

    private static String roleDestination(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse("PATIENT");
        return switch (role) {
            case "ADMIN", "SUPER_ADMIN" -> "/admin";
            case "PROVIDER" -> "/provider";
            case "LABORATORY" -> "/laboratory";
            case "HOME_CARE" -> "/home-care";
            default -> "/patient";
        };
    }
}

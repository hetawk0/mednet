package com.mednet.auth.api;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mednet.admin.data.PlatformAccountEntity;
import com.mednet.admin.data.PlatformAccountRepository;
import com.mednet.auth.AuthTokenService;
import com.mednet.auth.AuthTokenService.IssuedToken;
import com.mednet.auth.email.EkdSendEmailService;

@RestController
@RequestMapping("/api/v1/auth")
public class AccountAuthController {

    private static final Duration VERIFICATION_LIFETIME = Duration.ofHours(24);
    private static final Duration RESET_LIFETIME = Duration.ofMinutes(30);
    private final PlatformAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService tokens;
    private final EkdSendEmailService email;
    private final String publicUrl;

    public AccountAuthController(
            PlatformAccountRepository accounts,
            PasswordEncoder passwordEncoder,
            AuthTokenService tokens,
            EkdSendEmailService email,
            @org.springframework.beans.factory.annotation.Value("${NEXTAUTH_URL:http://localhost:3000}") String publicUrl) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.email = email;
        this.publicUrl = publicUrl.replaceAll("/$", "");
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<MessageResponse> register(@RequestBody CredentialsRequest request) {
        String normalizedEmail = normalize(request.email());
        validatePassword(request.password());
        PlatformAccountEntity account = accounts.findFirstByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (account == null) {
            account = new PlatformAccountEntity(UUID.randomUUID().toString(), normalizedEmail,
                    requestedAccountType(request.accountType()));
        } else if (account.isEmailVerified()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(genericResponse());
        }
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        sendVerification(account);
        accounts.save(account);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(genericResponse());
    }

    @PostMapping("/resend-verification")
    @Transactional
    public ResponseEntity<MessageResponse> resendVerification(@RequestBody EmailRequest request) {
        PlatformAccountEntity account = accounts.findFirstByEmailIgnoreCase(normalize(request.email())).orElse(null);
        if (account != null && !account.isEmailVerified()) {
            sendVerification(account);
            accounts.save(account);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(genericResponse());
    }

    @GetMapping("/verify")
    @Transactional
    public MessageResponse verify(@RequestParam String email, @RequestParam String token) {
        PlatformAccountEntity account = accounts.findFirstByEmailIgnoreCase(normalize(email)).orElseThrow(
                () -> new AuthRequestException("Verification link is invalid or expired"));
        if (account.getVerificationTokenHash() == null
                || !account.getVerificationTokenHash().equals(tokens.hash(token))
                || account.getVerificationTokenExpiresAt() == null
                || account.getVerificationTokenExpiresAt().isBefore(Instant.now())) {
            throw new AuthRequestException("Verification link is invalid or expired");
        }
        account.verifyEmail();
        accounts.save(account);
        return new MessageResponse("Email verified. You can now sign in.");
    }

    @PostMapping("/forgot-password")
    @Transactional
    public ResponseEntity<MessageResponse> forgotPassword(@RequestBody EmailRequest request) {
        PlatformAccountEntity account = accounts.findFirstByEmailIgnoreCase(normalize(request.email())).orElse(null);
        if (account != null && account.isEmailVerified()) {
            IssuedToken token = tokens.issue(RESET_LIFETIME);
            account.setPasswordResetToken(token.hash(), token.expiresAt());
            accounts.save(account);
            String link = publicUrl + "/reset?email=" + encode(account.getEmail()) + "&token=" + encode(token.raw());
            email.send(account.getEmail(), "Reset your MedNet password",
                    "<p>Reset your password:</p><p><a href=\"" + link + "\">Reset password</a></p>",
                    "Reset your MedNet password: " + link);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(genericResponse());
    }

    @PostMapping("/reset-password")
    @Transactional
    public MessageResponse resetPassword(@RequestBody ResetPasswordRequest request) {
        validatePassword(request.password());
        PlatformAccountEntity account = accounts.findFirstByEmailIgnoreCase(normalize(request.email())).orElseThrow(
                () -> new AuthRequestException("Reset link is invalid or expired"));
        if (account.getPasswordResetTokenHash() == null
                || !account.getPasswordResetTokenHash().equals(tokens.hash(request.token()))
                || account.getPasswordResetTokenExpiresAt() == null
                || account.getPasswordResetTokenExpiresAt().isBefore(Instant.now())) {
            throw new AuthRequestException("Reset link is invalid or expired");
        }
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.clearPasswordResetToken();
        accounts.save(account);
        email.send(account.getEmail(), "Your MedNet password was changed",
                "<p>Your MedNet password was changed successfully.</p>",
                "Your MedNet password was changed successfully.");
        return new MessageResponse("Password updated. You can now sign in.");
    }

    private void sendVerification(PlatformAccountEntity account) {
        IssuedToken token = tokens.issue(VERIFICATION_LIFETIME);
        account.setVerificationToken(token.hash(), token.expiresAt());
        String link = publicUrl + "/verify?email=" + encode(account.getEmail()) + "&token=" + encode(token.raw());
        email.send(account.getEmail(), "Verify your MedNet email",
                "<p>Confirm your MedNet email:</p><p><a href=\"" + link + "\">Verify email</a></p>",
                "Verify your MedNet email: " + link);
    }

    private static String normalize(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new AuthRequestException("A valid email address is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 12) {
            throw new AuthRequestException("Password must be at least 12 characters");
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static MessageResponse genericResponse() {
        return new MessageResponse("If the account can receive this message, instructions have been sent.");
    }

    public record CredentialsRequest(String email, String password, String accountType) {
    }

    public record EmailRequest(String email) {
    }

    public record ResetPasswordRequest(String email, String token, String password) {
    }

    public record MessageResponse(String message) {
    }

    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class AuthRequestException extends RuntimeException {
        public AuthRequestException(String message) {
            super(message);
        }
    }

    private static String requestedAccountType(String accountType) {
        if (accountType == null || accountType.isBlank())
            return "PATIENT";
        String normalized = accountType.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("PATIENT") && !normalized.equals("PROVIDER")) {
            throw new AuthRequestException("Account type must be PATIENT or PROVIDER");
        }
        return normalized;
    }
}

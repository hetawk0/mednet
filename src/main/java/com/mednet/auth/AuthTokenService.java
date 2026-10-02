package com.mednet.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

import org.springframework.stereotype.Service;

@Service
public class AuthTokenService {

    private final SecureRandom random = new SecureRandom();

    public IssuedToken issue(Duration lifetime) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new IssuedToken(raw, hash(raw), Instant.now().plus(lifetime));
    }

    public IssuedToken issueNumericCode(Duration lifetime) {
        String raw = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        return new IssuedToken(raw, hash(raw), Instant.now().plus(lifetime));
    }

    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record IssuedToken(String raw, String hash, Instant expiresAt) {
    }
}

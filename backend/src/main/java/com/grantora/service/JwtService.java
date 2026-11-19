package com.grantora.service;

import com.grantora.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Date;

@Service
public class JwtService {

    public static final String DEV_FALLBACK_SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci1ncmFudG9yYS1kZXYtZW52aXJvbm1lbnQtMjU2Yml0cw==";

    private final SecretKey secretKey;
    private final long expiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration) {
        this(secret, expiration, null);
    }

    @Autowired
    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration,
            @Autowired(required = false) Environment environment) {

        validateProductionSecret(secret, environment);

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (Exception e) {
            throw new IllegalStateException("JWT secret must be a valid Base64-encoded string.", e);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret key must be at least 256 bits (32 bytes).");
        }

        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = expiration;
    }

    public static void validateProductionSecret(String secret, Environment environment) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret must not be null or blank.");
        }

        String[] activeProfiles = environment != null ? environment.getActiveProfiles() : new String[0];
        boolean isDevOrLocalOrTest = Arrays.stream(activeProfiles)
                .anyMatch(p -> "dev".equalsIgnoreCase(p)
                        || "local".equalsIgnoreCase(p)
                        || "test".equalsIgnoreCase(p));

        if (!isDevOrLocalOrTest && DEV_FALLBACK_SECRET.equals(secret.trim())) {
            throw new IllegalStateException(
                    "Configuration error: The development fallback JWT secret is only permitted when an explicit "
                            + "'dev', 'local', or 'test' profile is active. Please provide a secure custom JWT_SECRET or activate a development profile.");
        }
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, String email) {
        Claims claims = extractAllClaims(token);

        return claims.getSubject().equals(email)
                && claims.getExpiration().after(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

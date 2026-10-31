package com.example.amaliyot.security;

import com.example.amaliyot.domain.Entities.UserAccount;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    private final long tokenMinutes;

    public JwtService(@Value("${security.jwt.secret-base64:}") String configuredSecret,
                      @Value("${security.jwt.token-minutes:60}") long tokenMinutes,
                      @Value("${security.jwt.require-configured-secret:false}") boolean requireConfiguredSecret) {
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            byte[] bytes = Decoders.BASE64.decode(configuredSecret.trim());
            if (bytes.length < 32) throw new IllegalArgumentException("JWT secret must contain at least 256 bits");
            this.key = Keys.hmacShaKeyFor(bytes);
        } else {
            if (requireConfiguredSecret)
                throw new IllegalArgumentException("JWT_SECRET_BASE64 must be configured for this profile");
            byte[] temporary = new byte[32];
            new SecureRandom().nextBytes(temporary);
            this.key = Keys.hmacShaKeyFor(temporary);
        }
        this.tokenMinutes = Math.max(5, tokenMinutes);
    }

    public IssuedToken issue(UserAccount user) {
        Instant issued = Instant.now();
        Instant expires = issued.plusSeconds(tokenMinutes * 60);
        String token = Jwts.builder().id(UUID.randomUUID().toString()).subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("tokenVersion", user.getTokenVersion())
                .issuedAt(Date.from(issued)).expiration(Date.from(expires))
                .signWith(key).compact();
        return new IssuedToken(token, expires);
    }

    public Claims parse(String token) {
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        Object versionClaim = claims.get("tokenVersion");
        if (!(versionClaim instanceof Number tokenVersion)) throw new IllegalArgumentException("Token version is missing");
        if (claims.getId() == null || claims.getId().isBlank() || claims.getExpiration() == null
                || claims.getSubject() == null || claims.get("role", String.class) == null)
            throw new IllegalArgumentException("Required token claims are missing");
        return new Claims(Long.parseLong(claims.getSubject()), claims.get("role", String.class),
                tokenVersion.longValue(), claims.getId(), claims.getExpiration().toInstant());
    }

    public record IssuedToken(String value, Instant expiresAt) {}
    public record Claims(Long userId, String role, Long tokenVersion, String tokenId, Instant expiresAt) {}
}

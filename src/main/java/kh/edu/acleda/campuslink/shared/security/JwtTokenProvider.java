package kh.edu.acleda.campuslink.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Authentication authentication) {
        return generateAccessToken(authentication.getName());
    }

    public String generateAccessToken(String username) {
        return generateToken(username, properties.getAccessTokenTtlMinutes() * 60);
    }

    public String generateRefreshToken(Authentication authentication) {
        return generateRefreshToken(authentication.getName());
    }

    public String generateRefreshToken(String username) {
        return generateToken(username, properties.getRefreshTokenTtlDays() * 24 * 60 * 60);
    }

    private String generateToken(String subject, long ttlSeconds) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(ttlSeconds);

        return Jwts.builder()
            .issuer(properties.getIssuer())
            .subject(subject)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(secretKey)
            .compact();
    }

    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        parseClaims(token);
        return true;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}


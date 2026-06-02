package kh.edu.acleda.campuslink.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider implements JwtService {

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String generateAccessToken(Authentication authentication) {
        return generateAccessToken(authentication.getName());
    }

    @Override
    public String generateAccessToken(String username) {
        return generateToken(username, Duration.ofMillis(properties.getAccessTokenExpirationMs()));
    }

    @Override
    public String generateRefreshToken(Authentication authentication) {
        return generateRefreshToken(authentication.getName());
    }

    @Override
    public String generateRefreshToken(String username) {
        return generateToken(username, Duration.ofMillis(properties.getRefreshTokenExpirationMs()));
    }

    private String generateToken(String subject, Duration ttl) {
        Instant now = Instant.now();
        Instant expiry = now.plus(ttl);

        return Jwts.builder()
            .issuer(properties.getIssuer())
            .subject(subject)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(secretKey)
            .compact();
    }

    @Override
    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    @Override
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}

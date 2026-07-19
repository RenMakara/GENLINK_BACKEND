package kh.edu.acleda.campuslink.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider implements JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String TOKEN_VERSION_CLAIM = "tv";
    /** HS256 needs a 256-bit key. */
    private static final int MIN_SECRET_BYTES = 32;
    /** Profiles under which a missing secret is auto-generated instead of failing. */
    private static final Set<String> DEV_PROFILES = Set.of("dev", "local", "test");

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties properties, Environment environment) {
        this.properties = properties;
        this.secretKey = resolveSecretKey(properties, environment);
    }

    /**
     * The signing key comes from app.jwt.secret. When it is absent we auto-generate an
     * ephemeral key ONLY under a dev/local/test profile, so local runs need no config. In
     * any other case startup fails: a per-instance random secret would reject tokens across
     * instances and log everyone out on every restart.
     */
    private static SecretKey resolveSecretKey(JwtProperties properties, Environment environment) {
        String secret = properties.getSecret();
        if (secret != null && !secret.isBlank()) {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("app.jwt.secret is too short: HS256 needs at least "
                    + MIN_SECRET_BYTES + " characters (256 bits). Generate one, e.g. `openssl rand -base64 48`.");
            }
            return Keys.hmacShaKeyFor(bytes);
        }
        if (isDevProfile(environment)) {
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            log.warn("app.jwt.secret is not set - generated an EPHEMERAL dev signing key. Tokens will not "
                + "survive a restart and will not work across instances. Set app.jwt.secret for staging/production.");
            return Keys.hmacShaKeyFor(random);
        }
        throw new IllegalStateException("app.jwt.secret must be set to a strong value (>= " + MIN_SECRET_BYTES
            + " chars) via the app.jwt.secret property or an env var, e.g. `openssl rand -base64 48`. "
            + "(Auto-generation only happens under a dev/local/test profile.)");
    }

    private static boolean isDevProfile(Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if (DEV_PROFILES.contains(profile.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String generateAccessToken(String username, int tokenVersion) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMillis(properties.getAccessTokenExpirationMs()));

        return Jwts.builder()
            .issuer(properties.getIssuer())
            .subject(username)
            .claim(TOKEN_VERSION_CLAIM, tokenVersion)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(secretKey)
            .compact();
    }

    @Override
    public Integer getTokenVersion(String token) {
        Object claim = parseClaims(token).get(TOKEN_VERSION_CLAIM);
        return claim instanceof Number number ? number.intValue() : null;
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

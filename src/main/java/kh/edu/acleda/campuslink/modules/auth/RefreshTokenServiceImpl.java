package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.entity.RefreshToken;
import kh.edu.acleda.campuslink.modules.auth.dto.RefreshTokenRotation;
import kh.edu.acleda.campuslink.modules.auth.repository.RefreshTokenRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.security.JwtProperties;
import kh.edu.acleda.campuslink.shared.security.JwtService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public RefreshTokenServiceImpl(RefreshTokenRepository refreshTokenRepository,
                                   JwtService jwtService,
                                   JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Override
    @Transactional
    public String create(User user) {
        return create(user, null, null);
    }

    @Override
    @Transactional
    public String create(User user, String deviceInfo, String ipAddress) {
        return create(user, deviceInfo, ipAddress, null);
    }

    @Override
    @Transactional
    public String create(User user, String deviceInfo, String ipAddress, String userAgent) {
        return create(user, deviceInfo, ipAddress, userAgent, jwtProperties.getRefreshTokenExpirationMs());
    }

    @Override
    @Transactional
    public String create(User user, String deviceInfo, String ipAddress, String userAgent, long ttlMs) {
        String token = jwtService.generateRefreshToken(user.getEmail());
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(token));
        refreshToken.setExpiresAt(Instant.now().plus(ttlMs, ChronoUnit.MILLIS));
        refreshToken.setDeviceInfo(deviceInfo);
        refreshToken.setIpAddress(ipAddress);
        refreshToken.setUserAgent(userAgent);
        refreshTokenRepository.save(refreshToken);
        return token;
    }

    @Override
    @Transactional
    public RefreshTokenRotation rotate(String token) {
        return rotate(token, null, null);
    }

    @Override
    @Transactional
    public RefreshTokenRotation rotate(String token, String deviceInfo, String ipAddress) {
        return rotate(token, deviceInfo, ipAddress, null);
    }

    @Override
    @Transactional
    public RefreshTokenRotation rotate(String token, String deviceInfo, String ipAddress, String userAgent) {
        return rotate(token, deviceInfo, ipAddress, userAgent, jwtProperties.getRefreshTokenExpirationMs());
    }

    /**
     * Rotation with an explicit lifetime for the replacement token.
     *
     * <p>Added for the admin portal, whose refresh tokens live 8 hours rather than the mobile
     * app's. Without it the first rotation would quietly hand an admin session the mobile
     * lifetime - the session would not misbehave, it would just outlive its policy by days,
     * and nothing would show it.
     */
    @Override
    @Transactional
    public RefreshTokenRotation rotate(String token, String deviceInfo, String ipAddress,
                                       String userAgent, long ttlMs) {
        if (!jwtService.validateToken(token)) {
            throw new ApiException("Invalid refresh token", AuthErrorCode.TOKEN_INVALID);
        }

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hash(token))
            .orElseThrow(() -> new ApiException("Refresh token not found in database", AuthErrorCode.TOKEN_INVALID));

        if (storedToken.getRevokedAt() != null) {
            refreshTokenRepository.revokeByUser(storedToken.getUser(), Instant.now());
            throw new ApiException("Refresh token reuse detected. Please login again.", AuthErrorCode.TOKEN_REUSE_DETECTED);
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException("Refresh token has expired", AuthErrorCode.TOKEN_EXPIRED);
        }

        User user = storedToken.getUser();
        if (user.getAccountStatus() != AccountStatus.ACTIVE || !user.isEmailVerified()) {
            storedToken.setRevokedAt(Instant.now());
            refreshTokenRepository.save(storedToken);
            throw new ApiException("Account is not allowed to refresh tokens", AuthErrorCode.AUTH_ACCOUNT_NOT_ALLOWED);
        }

        storedToken.setLastUsedAt(Instant.now());
        storedToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(storedToken);

        String newRawToken = jwtService.generateRefreshToken(user.getEmail());
        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setUser(user);
        newRefreshToken.setTokenHash(hash(newRawToken));
        newRefreshToken.setExpiresAt(Instant.now().plus(ttlMs, ChronoUnit.MILLIS));
        newRefreshToken.setDeviceInfo(deviceInfo);
        newRefreshToken.setIpAddress(ipAddress);
        newRefreshToken.setUserAgent(userAgent);
        refreshTokenRepository.save(newRefreshToken);
        return new RefreshTokenRotation(user, newRawToken);
    }

    @Override
    @Transactional
    public void revoke(User user) {
        refreshTokenRepository.revokeByUser(user, Instant.now());
    }

    @Override
    @Transactional
    public void revoke(String token) {
        refreshTokenRepository.revokeByTokenHash(hash(token), Instant.now());
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}

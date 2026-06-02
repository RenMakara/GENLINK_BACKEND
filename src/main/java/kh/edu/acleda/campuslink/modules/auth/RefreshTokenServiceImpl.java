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
        String token = jwtService.generateRefreshToken(user.getEmail());
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(token));
        refreshToken.setExpiresAt(Instant.now().plus(jwtProperties.getRefreshTokenExpirationMs(), ChronoUnit.MILLIS));
        refreshToken.setDeviceInfo(deviceInfo);
        refreshToken.setIpAddress(ipAddress);
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
        if (!jwtService.validateToken(token)) {
            throw new ApiException("Invalid refresh token");
        }

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hash(token))
            .orElseThrow(() -> new ApiException("Refresh token not found in database"));

        if (storedToken.getRevokedAt() != null) {
            throw new ApiException("Refresh token has been revoked");
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException("Refresh token has expired");
        }

        User user = storedToken.getUser();
        if (user.getAccountStatus() == AccountStatus.BANNED) {
            throw new ApiException("Your account has been banned");
        }
        if (user.getAccountStatus() == AccountStatus.DELETED || user.getDeletedAt() != null) {
            throw new ApiException("Your account has been deleted");
        }
        if (user.getAccountStatus() != AccountStatus.ACTIVE || !user.isEmailVerified()) {
            throw new ApiException("Please verify your email address before refreshing tokens");
        }

        storedToken.setRevokedAt(Instant.now());
        refreshTokenRepository.save(storedToken);

        String newRawToken = jwtService.generateRefreshToken(user.getEmail());
        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setUser(user);
        newRefreshToken.setTokenHash(hash(newRawToken));
        newRefreshToken.setExpiresAt(Instant.now().plus(jwtProperties.getRefreshTokenExpirationMs(), ChronoUnit.MILLIS));
        newRefreshToken.setDeviceInfo(deviceInfo);
        newRefreshToken.setIpAddress(ipAddress);
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

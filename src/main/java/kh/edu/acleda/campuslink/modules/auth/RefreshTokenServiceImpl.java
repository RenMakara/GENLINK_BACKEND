package kh.edu.acleda.campuslink.modules.auth;

import kh.edu.acleda.campuslink.modules.auth.entity.RefreshToken;
import kh.edu.acleda.campuslink.modules.auth.repository.RefreshTokenRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.modules.user.entity.UserStatus;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.security.JwtProperties;
import kh.edu.acleda.campuslink.shared.security.JwtService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(jwtService.generateRefreshToken(user.getEmail()));
        refreshToken.setRevoked(false);
        refreshToken.setExpiresAt(Instant.now().plus(jwtProperties.getRefreshTokenExpirationMs(), ChronoUnit.MILLIS));
        refreshTokenRepository.save(refreshToken);
        return refreshToken.getToken();
    }

    @Override
    @Transactional
    public RefreshToken rotate(String token) {
        if (!jwtService.validateToken(token)) {
            throw new ApiException("Invalid refresh token");
        }

        RefreshToken storedToken = refreshTokenRepository.findByToken(token)
            .orElseThrow(() -> new ApiException("Refresh token not found in database"));

        if (storedToken.isRevoked()) {
            throw new ApiException("Refresh token has been revoked");
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException("Refresh token has expired");
        }

        User user = storedToken.getUser();
        if (user.getStatus() == UserStatus.BANNED) {
            throw new ApiException("Your account has been banned");
        }
        if (user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) {
            throw new ApiException("Please verify your email address before refreshing tokens");
        }

        refreshTokenRepository.delete(storedToken);
        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setUser(user);
        newRefreshToken.setToken(jwtService.generateRefreshToken(user.getEmail()));
        newRefreshToken.setRevoked(false);
        newRefreshToken.setExpiresAt(Instant.now().plus(jwtProperties.getRefreshTokenExpirationMs(), ChronoUnit.MILLIS));
        return refreshTokenRepository.save(newRefreshToken);
    }

    @Override
    @Transactional
    public void revoke(User user) {
        refreshTokenRepository.revokeByUser(user);
    }
}

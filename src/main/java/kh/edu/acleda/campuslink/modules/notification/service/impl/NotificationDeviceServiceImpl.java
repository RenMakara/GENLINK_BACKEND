package kh.edu.acleda.campuslink.modules.notification.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.dto.request.NotificationDeviceRegistrationRequest;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationDeviceResponse;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationDevice;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationDeviceRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationDeviceService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.crypto.FieldEncryptor;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeviceServiceImpl implements NotificationDeviceService {

    private static final int MAX_TOKEN_LENGTH = 4096;

    private final NotificationDeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final FieldEncryptor fieldEncryptor;
    private final Clock clock;

    @Autowired
    public NotificationDeviceServiceImpl(
        NotificationDeviceRepository deviceRepository,
        UserRepository userRepository,
        CurrentUserProvider currentUserProvider,
        FieldEncryptor fieldEncryptor) {
        this(deviceRepository, userRepository, currentUserProvider, fieldEncryptor,
            Clock.systemUTC());
    }

    public NotificationDeviceServiceImpl(
        NotificationDeviceRepository deviceRepository,
        UserRepository userRepository,
        CurrentUserProvider currentUserProvider,
        FieldEncryptor fieldEncryptor,
        Clock clock) {
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.fieldEncryptor = fieldEncryptor;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDeviceResponse> getMine() {
        UUID userId = currentUserProvider.getCurrentUserId();
        return deviceRepository.findByUserIdAndActiveTrueOrderByLastSeenAtDesc(userId).stream()
            .map(NotificationDeviceServiceImpl::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public NotificationDeviceResponse register(NotificationDeviceRegistrationRequest request) {
        if (request == null || request.platform() == null) {
            throw new ApiException("platform is required", "VALIDATION_ERROR");
        }
        String token = normalizeToken(request.token());
        UUID userId = currentUserProvider.getCurrentUserId();
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));
        String tokenHash = hashToken(token);
        Instant now = clock.instant();

        NotificationDevice device = deviceRepository.findByTokenHash(tokenHash).orElse(null);
        if (device == null) {
            device = NotificationDevice.builder()
                .user(user)
                .platform(request.platform())
                .tokenHash(tokenHash)
                .active(true)
                .lastSeenAt(now)
                .build();
        }

        // A mobile installation can change accounts. Re-associate the token so the previous
        // account cannot continue receiving notifications after logout/login on the device.
        device.setUser(user);
        device.setPlatform(request.platform());
        device.setTokenCiphertext(fieldEncryptor.encrypt(token));
        device.setActive(true);
        device.setLastSeenAt(now);
        device.setLastFailureAt(null);
        return toResponse(deviceRepository.save(device));
    }

    @Override
    @Transactional
    public void deactivate(UUID deviceId) {
        if (deviceId == null) {
            throw new ApiException("deviceId is required", "VALIDATION_ERROR");
        }
        UUID userId = currentUserProvider.getCurrentUserId();
        NotificationDevice device = deviceRepository.findByIdAndUserId(deviceId, userId)
            .orElseThrow(() -> new NotFoundException("Notification device not found"));
        device.setActive(false);
        deviceRepository.save(device);
    }

    static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String normalizeToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException("token is required", "VALIDATION_ERROR");
        }
        String normalized = token.trim();
        if (normalized.length() > MAX_TOKEN_LENGTH) {
            throw new ApiException("token is too long", "VALIDATION_ERROR");
        }
        return normalized;
    }

    private static NotificationDeviceResponse toResponse(NotificationDevice device) {
        return new NotificationDeviceResponse(
            device.getId(), device.getPlatform(), device.getActive(), device.getLastSeenAt());
    }
}

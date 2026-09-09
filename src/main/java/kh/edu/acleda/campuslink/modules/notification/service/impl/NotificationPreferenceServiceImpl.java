package kh.edu.acleda.campuslink.modules.notification.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import kh.edu.acleda.campuslink.modules.notification.dto.request.NotificationPreferenceUpdateRequest;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationPreferenceResponse;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationPreference;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationPreferenceRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationPreferenceService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {

    private static final Pattern NOTIFICATION_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,79}");

    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public NotificationPreferenceServiceImpl(NotificationPreferenceRepository preferenceRepository,
                                             UserRepository userRepository,
                                             CurrentUserProvider currentUserProvider) {
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> getMine() {
        return preferenceRepository.findByUserIdOrderByNotificationTypeAsc(
                currentUserProvider.getCurrentUserId())
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public NotificationPreferenceResponse update(
        String notificationType, NotificationPreferenceUpdateRequest request) {
        if (request == null || request.enabled() == null) {
            throw new ApiException("enabled is required", "VALIDATION_ERROR");
        }
        String normalizedType = normalizeType(notificationType);
        UUID userId = currentUserProvider.getCurrentUserId();
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));

        NotificationPreference preference = preferenceRepository
            .findByUserIdAndNotificationType(userId, normalizedType)
            .orElseGet(() -> NotificationPreference.builder()
                .user(user)
                .notificationType(normalizedType)
                .build());
        preference.setEnabled(request.enabled());
        return toResponse(preferenceRepository.save(preference));
    }

    static String normalizeType(String notificationType) {
        if (notificationType == null) {
            throw new ApiException("Notification type is required", "VALIDATION_ERROR");
        }
        String normalized = notificationType.trim().toUpperCase(Locale.ROOT);
        if (!NOTIFICATION_TYPE.matcher(normalized).matches()) {
            throw new ApiException(
                "Notification type must contain only A-Z, 0-9 and underscore characters",
                "VALIDATION_ERROR");
        }
        return normalized;
    }

    private NotificationPreferenceResponse toResponse(NotificationPreference preference) {
        return new NotificationPreferenceResponse(
            preference.getId(), preference.getNotificationType(), preference.getEnabled());
    }
}

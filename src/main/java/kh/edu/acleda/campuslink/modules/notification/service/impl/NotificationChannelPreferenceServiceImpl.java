package kh.edu.acleda.campuslink.modules.notification.service.impl;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.dto.request.NotificationPreferenceUpdateRequest;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationChannelPreferenceResponse;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationChannelPreference;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationChannelPreferenceRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannelPreferenceService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationChannelPreferenceServiceImpl
    implements NotificationChannelPreferenceService {

    private final NotificationChannelPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public NotificationChannelPreferenceServiceImpl(
        NotificationChannelPreferenceRepository preferenceRepository,
        UserRepository userRepository,
        CurrentUserProvider currentUserProvider) {
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationChannelPreferenceResponse> getMine() {
        UUID userId = currentUserProvider.getCurrentUserId();
        Map<NotificationChannel, NotificationChannelPreference> explicit = new EnumMap<>(
            NotificationChannel.class);
        preferenceRepository.findByUserIdOrderByChannelAsc(userId)
            .forEach(preference -> explicit.put(preference.getChannel(), preference));

        return Arrays.stream(NotificationChannel.values())
            .map(channel -> toResponse(explicit.get(channel), channel))
            .toList();
    }

    @Override
    @Transactional
    public NotificationChannelPreferenceResponse update(
        String channel, NotificationPreferenceUpdateRequest request) {
        if (request == null || request.enabled() == null) {
            throw new ApiException("enabled is required", "VALIDATION_ERROR");
        }
        NotificationChannel normalizedChannel = normalizeChannel(channel);
        UUID userId = currentUserProvider.getCurrentUserId();
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));

        NotificationChannelPreference preference = preferenceRepository
            .findByUserIdAndChannel(userId, normalizedChannel)
            .orElseGet(() -> NotificationChannelPreference.builder()
                .user(user)
                .channel(normalizedChannel)
                .build());
        preference.setEnabled(request.enabled());
        return toResponse(preferenceRepository.save(preference), normalizedChannel);
    }

    static NotificationChannel normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new ApiException("Notification channel is required", "VALIDATION_ERROR");
        }
        try {
            return NotificationChannel.valueOf(channel.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                "Notification channel must be IN_APP, EMAIL, PUSH, or MOBILE_PUSH",
                "VALIDATION_ERROR");
        }
    }

    private NotificationChannelPreferenceResponse toResponse(
        NotificationChannelPreference preference, NotificationChannel channel) {
        return new NotificationChannelPreferenceResponse(
            preference == null ? null : preference.getId(),
            channel,
            preference == null || !Boolean.FALSE.equals(preference.getEnabled()));
    }
}

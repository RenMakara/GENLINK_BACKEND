package kh.edu.acleda.campuslink.modules.notification.service.impl;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationPreference;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationPreferenceRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationDeliveryPolicy;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.profile.entity.UserSetting;
import kh.edu.acleda.campuslink.modules.profile.repository.UserSettingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class NotificationDeliveryPolicyImpl implements NotificationDeliveryPolicy {

    static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

    private final UserSettingRepository userSettingRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final Clock clock;

    @Autowired
    public NotificationDeliveryPolicyImpl(UserSettingRepository userSettingRepository,
                                          NotificationPreferenceRepository preferenceRepository) {
        this(userSettingRepository, preferenceRepository, Clock.system(ZONE));
    }

    public NotificationDeliveryPolicyImpl(UserSettingRepository userSettingRepository,
                                          NotificationPreferenceRepository preferenceRepository,
                                          Clock clock) {
        this.userSettingRepository = userSettingRepository;
        this.preferenceRepository = preferenceRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean isAllowed(NotificationRequest request) {
        try {
            UserSetting setting = userSettingRepository.findByUserId(request.recipientUserId())
                .orElse(null);
            if (setting != null && Boolean.FALSE.equals(setting.getNotificationEnabled())) {
                return false;
            }

            Optional<NotificationPreference> preference = preferenceRepository
                .findByUserIdAndNotificationType(
                    request.recipientUserId(), request.type().toUpperCase(Locale.ROOT));
            if (preference.isPresent() && Boolean.FALSE.equals(preference.get().getEnabled())) {
                return false;
            }

            return !isInsideQuietHours(setting, LocalTime.now(clock));
        } catch (RuntimeException exception) {
            // Preferences are secondary to the source action. A settings read failure must not
            // make RSVP, payment, moderation, or another business transaction fail.
            log.warn("Could not evaluate notification preferences for recipient={} type={}; allowing request",
                request.recipientUserId(), request.type(), exception);
            return true;
        }
    }

    static boolean isInsideQuietHours(UserSetting setting, LocalTime now) {
        if (setting == null || !Boolean.TRUE.equals(setting.getNotificationQuietHoursEnabled())) {
            return false;
        }
        LocalTime start = setting.getNotificationQuietStart();
        LocalTime end = setting.getNotificationQuietEnd();
        if (start == null || end == null || start.equals(end)) {
            // Invalid or legacy settings fail open; the settings API rejects this combination.
            return false;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        // A start after the end is an overnight window, for example 22:00-07:00.
        return !now.isBefore(start) || now.isBefore(end);
    }
}

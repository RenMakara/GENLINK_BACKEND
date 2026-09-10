package kh.edu.acleda.campuslink.modules.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationChannelPreference;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationPreference;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationChannelPreferenceRepository;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationPreferenceRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.notification.service.impl.NotificationDeliveryPolicyImpl;
import kh.edu.acleda.campuslink.modules.profile.entity.UserSetting;
import kh.edu.acleda.campuslink.modules.profile.repository.UserSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryPolicyImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

    @Mock private UserSettingRepository userSettingRepository;
    @Mock private NotificationPreferenceRepository preferenceRepository;
    @Mock private NotificationChannelPreferenceRepository channelPreferenceRepository;

    @Test
    void allowsByDefaultWhenTheUserHasNoSettingsOrTypeOverride() {
        NotificationDeliveryPolicyImpl policy = policyAt(LocalTime.of(12, 0));
        when(userSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(preferenceRepository.findByUserIdAndNotificationType(USER_ID, "COMMUNITY_EVENT_RSVP"))
            .thenReturn(Optional.empty());

        assertThat(policy.isAllowed(request("COMMUNITY_EVENT_RSVP"))).isTrue();
    }

    @Test
    void suppressesWhenGlobalNotificationsAreDisabled() {
        NotificationDeliveryPolicyImpl policy = policyAt(LocalTime.of(12, 0));
        when(userSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(settings(false)));

        assertThat(policy.isAllowed(request("COMMUNITY_EVENT_RSVP"))).isFalse();
    }

    @Test
    void suppressesWhenTheNotificationTypeIsDisabled() {
        NotificationDeliveryPolicyImpl policy = policyAt(LocalTime.of(12, 0));
        when(userSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(settings(true)));
        when(preferenceRepository.findByUserIdAndNotificationType(USER_ID, "COMMUNITY_EVENT_RSVP"))
            .thenReturn(Optional.of(NotificationPreference.builder().enabled(false).build()));

        assertThat(policy.isAllowed(request("COMMUNITY_EVENT_RSVP"))).isFalse();
    }

    @Test
    void suppressesInsideAnOvernightQuietHoursWindowAndAllowsAtTheEndBoundary() {
        UserSetting setting = settings(true);
        setting.setNotificationQuietHoursEnabled(true);
        setting.setNotificationQuietStart(LocalTime.of(22, 0));
        setting.setNotificationQuietEnd(LocalTime.of(7, 0));
        when(userSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(setting));
        when(preferenceRepository.findByUserIdAndNotificationType(USER_ID, "COMMUNITY_EVENT_RSVP"))
            .thenReturn(Optional.empty());

        assertThat(policyAt(LocalTime.of(23, 0)).isAllowed(request("COMMUNITY_EVENT_RSVP"))).isFalse();
        assertThat(policyAt(LocalTime.of(7, 0)).isAllowed(request("COMMUNITY_EVENT_RSVP"))).isTrue();
    }

    @Test
    void allowsWhenPreferenceStorageFailsSoTheBusinessActionIsNotRolledBack() {
        NotificationDeliveryPolicyImpl policy = policyAt(LocalTime.of(12, 0));
        when(userSettingRepository.findByUserId(USER_ID))
            .thenThrow(new IllegalStateException("database unavailable"));

        assertThat(policy.isAllowed(request("COMMUNITY_EVENT_RSVP"))).isTrue();
    }

    @Test
    void suppressesAnExplicitChannelWhenThatChannelIsDisabled() {
        NotificationDeliveryPolicyImpl policy = policyAtWithChannelPreferences(LocalTime.of(12, 0));
        when(userSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(settings(true)));
        when(preferenceRepository.findByUserIdAndNotificationType(USER_ID, "COMMUNITY_EVENT_RSVP"))
            .thenReturn(Optional.empty());
        when(channelPreferenceRepository.findByUserIdAndChannel(USER_ID, NotificationChannel.EMAIL))
            .thenReturn(Optional.of(NotificationChannelPreference.builder()
                .channel(NotificationChannel.EMAIL)
                .enabled(false)
                .build()));

        assertThat(policy.isAllowed(request(
            "COMMUNITY_EVENT_RSVP", Set.of(NotificationChannel.EMAIL)))).isFalse();
    }

    @Test
    void keepsMixedChannelRequestsEligibleWhenOneChannelIsDisabled() {
        NotificationDeliveryPolicyImpl policy = policyAtWithChannelPreferences(LocalTime.of(12, 0));
        when(userSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(settings(true)));
        when(preferenceRepository.findByUserIdAndNotificationType(USER_ID, "COMMUNITY_EVENT_RSVP"))
            .thenReturn(Optional.empty());

        assertThat(policy.isAllowed(request(
            "COMMUNITY_EVENT_RSVP",
            Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL)))).isTrue();
    }

    private NotificationDeliveryPolicyImpl policyAt(LocalTime localTime) {
        Clock clock = Clock.fixed(
            LocalDateTime.of(2026, 9, 10, localTime.getHour(), localTime.getMinute())
                .atZone(ZONE).toInstant(), ZONE);
        return new NotificationDeliveryPolicyImpl(userSettingRepository, preferenceRepository, clock);
    }

    private NotificationDeliveryPolicyImpl policyAtWithChannelPreferences(LocalTime localTime) {
        Clock clock = Clock.fixed(
            LocalDateTime.of(2026, 9, 10, localTime.getHour(), localTime.getMinute())
                .atZone(ZONE).toInstant(), ZONE);
        return new NotificationDeliveryPolicyImpl(
            userSettingRepository, preferenceRepository, channelPreferenceRepository, clock);
    }

    private NotificationRequest request(String type) {
        return new NotificationRequest(USER_ID, "TEST_TEMPLATE", type, Map.of());
    }

    private NotificationRequest request(String type, Set<NotificationChannel> channels) {
        return new NotificationRequest(
            USER_ID, "TEST_TEMPLATE", type, Map.of(), null, null, null, channels);
    }

    private UserSetting settings(boolean notificationsEnabled) {
        return UserSetting.builder()
            .notificationEnabled(notificationsEnabled)
            .notificationQuietHoursEnabled(false)
            .build();
    }
}

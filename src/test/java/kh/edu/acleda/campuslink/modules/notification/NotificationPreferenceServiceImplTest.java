package kh.edu.acleda.campuslink.modules.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.dto.request.NotificationPreferenceUpdateRequest;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationPreferenceResponse;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationPreference;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationPreferenceRepository;
import kh.edu.acleda.campuslink.modules.notification.service.impl.NotificationPreferenceServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private NotificationPreferenceRepository preferenceRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private NotificationPreferenceServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceServiceImpl(
            preferenceRepository, userRepository, currentUserProvider);
        user = new User();
    }

    @Test
    void returnsExplicitOverridesInStableTypeOrder() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        NotificationPreference first = NotificationPreference.builder()
            .notificationType("COMMUNITY_EVENT_RSVP").enabled(false).build();
        NotificationPreference second = NotificationPreference.builder()
            .notificationType("PRODUCTIVITY_TASK").enabled(true).build();
        when(preferenceRepository.findByUserIdOrderByNotificationTypeAsc(USER_ID))
            .thenReturn(List.of(first, second));

        List<NotificationPreferenceResponse> response = service.getMine();

        assertThat(response).extracting(NotificationPreferenceResponse::notificationType)
            .containsExactly("COMMUNITY_EVENT_RSVP", "PRODUCTIVITY_TASK");
        assertThat(response.get(0).enabled()).isFalse();
    }

    @Test
    void normalisesTypeAndUpsertsTheCurrentUsersOverride() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(preferenceRepository.findByUserIdAndNotificationType(USER_ID, "COMMUNITY_EVENT_RSVP"))
            .thenReturn(Optional.empty());
        when(preferenceRepository.save(any(NotificationPreference.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationPreferenceResponse response = service.update(
            " community_event_rsvp ", new NotificationPreferenceUpdateRequest(false));

        assertThat(response.notificationType()).isEqualTo("COMMUNITY_EVENT_RSVP");
        assertThat(response.enabled()).isFalse();
        org.mockito.Mockito.verify(preferenceRepository).save(any(NotificationPreference.class));
    }

    @Test
    void rejectsMalformedTypeNames() {
        assertThatThrownBy(() -> service.update(
            "community.event", new NotificationPreferenceUpdateRequest(false)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("A-Z, 0-9 and underscore");
    }
}

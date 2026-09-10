package kh.edu.acleda.campuslink.modules.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.dto.request.NotificationPreferenceUpdateRequest;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationChannelPreferenceResponse;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationChannelPreference;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationChannelPreferenceRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;
import kh.edu.acleda.campuslink.modules.notification.service.impl.NotificationChannelPreferenceServiceImpl;
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
class NotificationChannelPreferenceServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private NotificationChannelPreferenceRepository preferenceRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private NotificationChannelPreferenceServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new NotificationChannelPreferenceServiceImpl(
            preferenceRepository, userRepository, currentUserProvider);
        user = new User();
    }

    @Test
    void returnsEveryChannelWithEnabledDefaultsAndStableOrder() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        NotificationChannelPreference email = NotificationChannelPreference.builder()
            .channel(NotificationChannel.EMAIL)
            .enabled(false)
            .build();
        when(preferenceRepository.findByUserIdOrderByChannelAsc(USER_ID))
            .thenReturn(List.of(email));

        List<NotificationChannelPreferenceResponse> response = service.getMine();

        assertThat(response).extracting(NotificationChannelPreferenceResponse::channel)
            .containsExactly(NotificationChannel.IN_APP, NotificationChannel.EMAIL,
                NotificationChannel.PUSH);
        assertThat(response.get(0).enabled()).isTrue();
        assertThat(response.get(1).enabled()).isFalse();
        assertThat(response.get(2).enabled()).isTrue();
    }

    @Test
    void normalisesChannelAndUpsertsTheCurrentUsersOverride() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(preferenceRepository.findByUserIdAndChannel(USER_ID, NotificationChannel.EMAIL))
            .thenReturn(Optional.empty());
        when(preferenceRepository.save(any(NotificationChannelPreference.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationChannelPreferenceResponse response = service.update(
            " email ", new NotificationPreferenceUpdateRequest(false));

        assertThat(response.channel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(response.enabled()).isFalse();
        verify(preferenceRepository).save(any(NotificationChannelPreference.class));
    }

    @Test
    void rejectsMalformedChannelNames() {
        assertThatThrownBy(() -> service.update(
            "sms", new NotificationPreferenceUpdateRequest(false)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("IN_APP, EMAIL, or PUSH");
    }
}

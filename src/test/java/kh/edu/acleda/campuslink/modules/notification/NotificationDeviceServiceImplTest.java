package kh.edu.acleda.campuslink.modules.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.dto.request.NotificationDeviceRegistrationRequest;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationDeviceResponse;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationDevice;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationDevicePlatform;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationDeviceRepository;
import kh.edu.acleda.campuslink.modules.notification.service.impl.NotificationDeviceServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.crypto.FieldEncryptor;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDeviceServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final String TOKEN = "fcm-token-123";
    private static final String CIPHERTEXT = "v1:encrypted-token";
    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");

    @Mock private NotificationDeviceRepository deviceRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private FieldEncryptor fieldEncryptor;

    private NotificationDeviceServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new NotificationDeviceServiceImpl(
            deviceRepository,
            userRepository,
            currentUserProvider,
            fieldEncryptor,
            Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User();
    }

    @Test
    void registersAndReturnsDeviceWithoutExposingTheRawToken() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(deviceRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        when(fieldEncryptor.encrypt(TOKEN)).thenReturn(CIPHERTEXT);
        when(deviceRepository.save(any(NotificationDevice.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationDeviceResponse response = service.register(
            new NotificationDeviceRegistrationRequest(NotificationDevicePlatform.ANDROID, TOKEN));

        assertThat(response.platform()).isEqualTo(NotificationDevicePlatform.ANDROID);
        assertThat(response.active()).isTrue();
        assertThat(response.lastSeenAt()).isEqualTo(NOW);
        verify(fieldEncryptor).encrypt(TOKEN);
    }

    @Test
    void listsOnlyActiveDevicesForTheCurrentUser() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        NotificationDevice device = NotificationDevice.builder()
            .platform(NotificationDevicePlatform.IOS)
            .active(true)
            .lastSeenAt(NOW)
            .build();
        when(deviceRepository.findByUserIdAndActiveTrueOrderByLastSeenAtDesc(USER_ID))
            .thenReturn(List.of(device));

        List<NotificationDeviceResponse> response = service.getMine();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).platform()).isEqualTo(NotificationDevicePlatform.IOS);
        assertThat(response.get(0).active()).isTrue();
    }

    @Test
    void deactivatesOnlyAnOwnedDevice() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        NotificationDevice device = NotificationDevice.builder()
            .platform(NotificationDevicePlatform.ANDROID)
            .active(true)
            .build();
        when(deviceRepository.findByIdAndUserId(DEVICE_ID, USER_ID))
            .thenReturn(Optional.of(device));

        service.deactivate(DEVICE_ID);

        assertThat(device.getActive()).isFalse();
        verify(deviceRepository).save(device);
    }

    @Test
    void rejectsBlankTokens() {
        assertThatThrownBy(() -> service.register(
            new NotificationDeviceRegistrationRequest(NotificationDevicePlatform.ANDROID, "  ")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("token is required");
    }
}

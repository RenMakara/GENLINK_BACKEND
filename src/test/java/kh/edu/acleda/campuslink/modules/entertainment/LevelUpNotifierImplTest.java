package kh.edu.acleda.campuslink.modules.entertainment;

import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.service.impl.LevelUpNotifierImpl;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LevelUpNotifierImplTest {

    @Mock private NotificationEmitter notificationEmitter;

    @Test
    void emitsNavigableDeduplicatedLevelUpNotification() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        ReflectionTestUtils.setField(user, "id", userId);

        new LevelUpNotifierImpl(notificationEmitter).notifyForLevelUp(user, 4, 310);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(request.templateKey()).isEqualTo("ENTERTAINMENT_LEVEL_UP");
        assertThat(request.type()).isEqualTo("ENTERTAINMENT_LEVEL_UP");
        assertThat(request.parameters()).isEqualTo(Map.of("level", "4", "totalXp", "310"));
        assertThat(request.referenceType()).isEqualTo("ACHIEVEMENT");
        assertThat(request.referenceId()).isEqualTo(userId);
        assertThat(request.dedupeKey()).isEqualTo("level-up:" + userId + ":4");
    }

    @Test
    void skipsInvalidLevelUpData() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

        new LevelUpNotifierImpl(notificationEmitter).notifyForLevelUp(user, 0, 0);

        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }
}

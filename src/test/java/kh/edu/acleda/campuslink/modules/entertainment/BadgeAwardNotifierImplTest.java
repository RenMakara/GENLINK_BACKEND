package kh.edu.acleda.campuslink.modules.entertainment;

import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.Badge;
import kh.edu.acleda.campuslink.modules.entertainment.service.impl.BadgeAwardNotifierImpl;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
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
class BadgeAwardNotifierImplTest {

    @Mock private NotificationEmitter notificationEmitter;

    @Test
    void emitsNavigableDeduplicatedBadgeNotification() {
        UUID userId = UUID.randomUUID();
        UUID badgeId = UUID.randomUUID();
        User user = new User();
        ReflectionTestUtils.setField(user, "id", userId);
        user.setPreferredLanguage(Language.ENGLISH);
        Badge badge = Badge.builder().nameEn("Quiz Champion").build();
        ReflectionTestUtils.setField(badge, "id", badgeId);

        new BadgeAwardNotifierImpl(notificationEmitter).notifyForAwardedBadge(user, badge);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(request.templateKey()).isEqualTo("ENTERTAINMENT_BADGE_AWARDED");
        assertThat(request.type()).isEqualTo("ENTERTAINMENT_BADGE");
        assertThat(request.parameters()).isEqualTo(Map.of("badgeName", "Quiz Champion"));
        assertThat(request.referenceType()).isEqualTo("BADGE");
        assertThat(request.referenceId()).isEqualTo(badgeId);
        assertThat(request.dedupeKey()).isEqualTo("badge-awarded:" + badgeId);
    }

    @Test
    void usesKhmerBadgeNameWhenTheRecipientPrefersKhmer() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setPreferredLanguage(Language.KHMER);
        Badge badge = Badge.builder().nameEn("Quiz Champion").nameKm("អ្នកឈ្នះកម្រងសំណួរ").build();
        ReflectionTestUtils.setField(badge, "id", UUID.randomUUID());

        new BadgeAwardNotifierImpl(notificationEmitter).notifyForAwardedBadge(user, badge);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        assertThat(captor.getValue().parameters().get("badgeName"))
            .isEqualTo("អ្នកឈ្នះកម្រងសំណួរ");
    }

    @Test
    void skipsIncompleteBadgeData() {
        new BadgeAwardNotifierImpl(notificationEmitter).notifyForAwardedBadge(null, null);

        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }
}

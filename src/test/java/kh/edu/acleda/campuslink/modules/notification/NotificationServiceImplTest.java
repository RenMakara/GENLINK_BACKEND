package kh.edu.acleda.campuslink.modules.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.entity.Notification;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationRepository;
import kh.edu.acleda.campuslink.modules.notification.service.impl.NotificationServiceImpl;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID NOTIF_ID = UUID.randomUUID();

    @Mock private NotificationRepository notificationRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private NotificationServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationRepository, currentUserProvider);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
    }

    private Notification notification(boolean read) {
        Notification n = Notification.builder()
            .user(user).title("Budget alert").message("You are near your limit")
            .language(Language.ENGLISH).type("FINANCE_BUDGET").readStatus(read).build();
        ReflectionTestUtils.setField(n, "id", NOTIF_ID);
        return n;
    }

    @Test
    void getMineReturnsTheUsersNotifications() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(notification(false))));

        var page = service.getMine(false, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).title()).isEqualTo("Budget alert");
        assertThat(page.content().get(0).readStatus()).isFalse();
    }

    @Test
    void getMineUnreadOnlyUsesTheUnreadQuery() {
        when(notificationRepository.findByUserIdAndReadStatusFalseOrderByCreatedAtDesc(eq(USER_ID), any()))
            .thenReturn(new PageImpl<>(List.of(notification(false))));

        service.getMine(true, PageRequest.of(0, 20));

        verify(notificationRepository).findByUserIdAndReadStatusFalseOrderByCreatedAtDesc(eq(USER_ID), any());
        verify(notificationRepository, never()).findByUserIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void unreadCountDelegatesToTheRepository() {
        when(notificationRepository.countByUserIdAndReadStatusFalse(USER_ID)).thenReturn(4L);

        assertThat(service.unreadCount()).isEqualTo(4L);
    }

    @Test
    void markReadStampsAnOwnedUnreadNotification() {
        Notification n = notification(false);
        when(notificationRepository.findByIdAndUserId(NOTIF_ID, USER_ID)).thenReturn(Optional.of(n));

        service.markRead(NOTIF_ID);

        assertThat(n.getReadStatus()).isTrue();
        assertThat(n.getReadAt()).isNotNull();
        verify(notificationRepository).save(n);
    }

    /** Already read: no re-stamp, no write, so read_at keeps the original time. */
    @Test
    void markReadIsIdempotentWhenAlreadyRead() {
        when(notificationRepository.findByIdAndUserId(NOTIF_ID, USER_ID)).thenReturn(Optional.of(notification(true)));

        service.markRead(NOTIF_ID);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markReadOnAnotherUsersNotificationIsNotFound() {
        when(notificationRepository.findByIdAndUserId(NOTIF_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(NOTIF_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void markAllReadReturnsHowManyWereChanged() {
        when(notificationRepository.markAllReadForUser(eq(USER_ID), any())).thenReturn(3);

        assertThat(service.markAllRead()).isEqualTo(3);
    }
}

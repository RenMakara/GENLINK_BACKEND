package kh.edu.acleda.campuslink.modules.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.bff.service.MobileNotificationBffServiceImpl;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileNotificationsResponse;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationResponse;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationService;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class MobileNotificationBffServiceImplTest {

    @Mock
    private NotificationService notificationService;

    private MobileNotificationBffServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MobileNotificationBffServiceImpl(notificationService);
    }

    @Test
    void returnsUnreadBadgeAndNewestPreviewFromNotificationService() {
        NotificationResponse notification = new NotificationResponse(
            UUID.randomUUID(), "Notice", "Message", Language.ENGLISH,
            "ADMIN_BROADCAST", null, null, false, null);
        PageResponse<NotificationResponse> page = new PageResponse<>(
            List.of(notification), 0, 10, 11, 2, true);
        when(notificationService.getMine(eq(false), any(Pageable.class))).thenReturn(page);
        when(notificationService.unreadCount()).thenReturn(4L);

        MobileNotificationsResponse response = service.getNotifications(10);

        assertThat(response.unreadCount()).isEqualTo(4);
        assertThat(response.recentNotifications()).containsExactly(notification);
        assertThat(response.hasMore()).isTrue();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).getMine(eq(false), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }

    @Test
    void rejectsARequestOutsideTheMobilePreviewLimit() {
        assertThatThrownBy(() -> service.getNotifications(21))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "VALIDATION_ERROR");

        verifyNoInteractions(notificationService);
    }
}

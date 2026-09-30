package kh.edu.acleda.campuslink.modules.mobile.controller;

import kh.edu.acleda.campuslink.modules.bff.service.MobileFinanceBffService;
import kh.edu.acleda.campuslink.modules.bff.service.MobileHomeBffService;
import kh.edu.acleda.campuslink.modules.bff.service.MobileNotificationBffService;
import kh.edu.acleda.campuslink.modules.bff.service.MobileProductivityBffService;
import kh.edu.acleda.campuslink.modules.bff.service.MobileProfileBffService;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileNotificationsResponse;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationResponse;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.localization.LanguageResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Locks the still-deferred mobile event-detail endpoint as an honest {@code NOT_IMPLEMENTED} response. */
@ExtendWith(MockitoExtension.class)
class MobileControllerTest {

    @Mock private MobileProfileBffService mobileProfileBffService;
    @Mock private MobileHomeBffService mobileHomeBffService;
    @Mock private MobileFinanceBffService mobileFinanceBffService;
    @Mock private MobileNotificationBffService mobileNotificationBffService;
    @Mock private MobileProductivityBffService mobileProductivityBffService;
    @Mock private LanguageResolver languageResolver;

    private MobileController controller() {
        return new MobileController(mobileProfileBffService, mobileHomeBffService,
            mobileFinanceBffService, mobileNotificationBffService,
            mobileProductivityBffService, languageResolver);
    }

    @Test
    void eventDetailIsAnHonestNotImplementedDeferral() {
        assertThatThrownBy(() -> controller().eventDetail(UUID.randomUUID()))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "NOT_IMPLEMENTED")
            .hasMessageContaining("not available");
    }

    @Test
    void notificationsDelegatesToTheNotificationBff() {
        NotificationResponse notification = new NotificationResponse(
            UUID.randomUUID(), "Notice", "Message", Language.ENGLISH,
            "ADMIN_BROADCAST", null, null, false, null);
        when(mobileNotificationBffService.getNotifications(10))
            .thenReturn(new MobileNotificationsResponse(3, List.of(notification), true));

        var response = controller().notifications(10);

        assertThat(response.getBody().getData().unreadCount()).isEqualTo(3);
        assertThat(response.getBody().getData().recentNotifications())
            .containsExactly(notification);
        assertThat(response.getBody().getData().hasMore()).isTrue();
        verify(mobileNotificationBffService).getNotifications(10);
    }
}

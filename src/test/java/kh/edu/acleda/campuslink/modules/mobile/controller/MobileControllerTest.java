package kh.edu.acleda.campuslink.modules.mobile.controller;

import kh.edu.acleda.campuslink.modules.bff.service.MobileFinanceBffService;
import kh.edu.acleda.campuslink.modules.bff.service.MobileHomeBffService;
import kh.edu.acleda.campuslink.modules.bff.service.MobileProductivityBffService;
import kh.edu.acleda.campuslink.modules.bff.service.MobileProfileBffService;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.localization.LanguageResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Locks the two deferred mobile-BFF endpoints as honest {@code NOT_IMPLEMENTED} failures.
 *
 * <p>They previously returned {@code 200 success=true} with a literal {@code "TODO: ... aggregation"}
 * string — a fake success a client cannot distinguish from a real payload. There is no consumer or
 * pinned contract for either (the app uses the community event endpoint and the notification list
 * endpoint), so they are deferred rather than implemented. These tests fail if either endpoint
 * regresses to a 2xx success, matching the friends-leaderboard deferral guard.
 */
@ExtendWith(MockitoExtension.class)
class MobileControllerTest {

    @Mock private MobileProfileBffService mobileProfileBffService;
    @Mock private MobileHomeBffService mobileHomeBffService;
    @Mock private MobileFinanceBffService mobileFinanceBffService;
    @Mock private MobileProductivityBffService mobileProductivityBffService;
    @Mock private LanguageResolver languageResolver;

    private MobileController controller() {
        return new MobileController(mobileProfileBffService, mobileHomeBffService,
            mobileFinanceBffService, mobileProductivityBffService, languageResolver);
    }

    @Test
    void eventDetailIsAnHonestNotImplementedDeferral() {
        assertThatThrownBy(() -> controller().eventDetail(UUID.randomUUID()))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "NOT_IMPLEMENTED")
            .hasMessageContaining("not available");
    }

    @Test
    void notificationsIsAnHonestNotImplementedDeferral() {
        assertThatThrownBy(() -> controller().notifications())
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "NOT_IMPLEMENTED")
            .hasMessageContaining("not available");
    }
}

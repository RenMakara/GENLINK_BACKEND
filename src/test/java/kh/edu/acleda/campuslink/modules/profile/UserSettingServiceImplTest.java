package kh.edu.acleda.campuslink.modules.profile;

import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.profile.dto.request.UserSettingUpdateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.UserSettingResponse;
import kh.edu.acleda.campuslink.modules.profile.entity.UserSetting;
import kh.edu.acleda.campuslink.modules.profile.repository.UserSettingRepository;
import kh.edu.acleda.campuslink.modules.profile.service.impl.UserSettingServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.enums.Theme;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * This service used to be a stub that returned hardcoded values and persisted
 * nothing, so these tests exist mainly to pin that it really reads and writes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserSettingServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private UserSettingRepository userSettingRepository;
    @Mock private UserRepository userRepository;
    @Mock private kh.edu.acleda.campuslink.shared.security.CurrentUserProvider currentUserProvider;

    private UserSettingServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new UserSettingServiceImpl(userSettingRepository, userRepository, currentUserProvider);
        user = new User();
        user.setEmail("user@example.com");
        user.setPreferredLanguage(Language.KHMER);
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userSettingRepository.save(any(UserSetting.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** Users who registered before this feature existed must not get a 404. */
    @Test
    void getMineCreatesDefaultsWhenTheUserHasNoSettingsRow() {
        when(userSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        UserSettingResponse response = service.getMine();

        assertThat(response.language()).isEqualTo(Language.KHMER);
        assertThat(response.theme()).isEqualTo(Theme.SYSTEM_DEFAULT);
        assertThat(response.privacyProfile()).isEqualTo("PUBLIC");
        assertThat(response.notificationEnabled()).isTrue();
        assertThat(response.quietHoursEnabled()).isFalse();
        assertThat(response.biometricEnabled()).isFalse();
    }

    /**
     * ENGLISH is the intended default (confirmed 2026-07-17), so settings must inherit
     * it rather than invent one. The data dictionary and language plan still say KHMER
     * and are the documents that are out of date.
     *
     * <p>Null is unrepresentable in practice - the setter coerces it - so this also pins
     * that the service does not reintroduce a default of its own.
     */
    @Test
    void settingsInheritTheUserLanguageDefaultingToEnglish() {
        user.setPreferredLanguage(null);
        when(userSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.getMine().language()).isEqualTo(Language.ENGLISH);
    }

    @Test
    void updateLeavesOmittedFieldsUntouched() {
        when(userSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existingSetting()));

        UserSettingResponse response = service.update(new UserSettingUpdateRequest(
            null, Theme.DARK, null, null, null, null, null));

        assertThat(response.theme()).isEqualTo(Theme.DARK);
        assertThat(response.language()).isEqualTo(Language.ENGLISH);
        assertThat(response.privacyProfile()).isEqualTo("FRIENDS");
        assertThat(response.allowMessages()).isFalse();
    }

    @Test
    void updateNormalisesPrivacyToUpperCase() {
        when(userSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existingSetting()));

        UserSettingResponse response = service.update(new UserSettingUpdateRequest(
            null, null, null, "private", null, null, null));

        assertThat(response.privacyProfile()).isEqualTo("PRIVATE");
    }

    @Test
    void updateStoresAnOvernightQuietHoursWindow() {
        when(userSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existingSetting()));

        UserSettingResponse response = service.update(new UserSettingUpdateRequest(
            null, null, null, true, LocalTime.of(22, 0), LocalTime.of(7, 0),
            null, null, null, null));

        assertThat(response.quietHoursEnabled()).isTrue();
        assertThat(response.quietHoursStart()).isEqualTo(LocalTime.of(22, 0));
        assertThat(response.quietHoursEnd()).isEqualTo(LocalTime.of(7, 0));
    }

    @Test
    void rejectsEnabledQuietHoursWithoutBothBounds() {
        when(userSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existingSetting()));

        assertThatThrownBy(() -> service.update(new UserSettingUpdateRequest(
            null, null, null, true, null, null, null, null, null, null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("both quietHoursStart and quietHoursEnd");
    }

    @Test
    void updateRejectsAnUnknownPrivacyValue() {
        when(userSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existingSetting()));

        assertThatThrownBy(() -> service.update(new UserSettingUpdateRequest(
            null, null, null, "EVERYONE", null, null, null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("PUBLIC, FRIENDS or PRIVATE");
    }

    private UserSetting existingSetting() {
        return UserSetting.builder()
            .user(user)
            .language(Language.ENGLISH)
            .theme(Theme.LIGHT)
            .notificationEnabled(true)
            .privacyProfile("FRIENDS")
            .allowMessages(false)
            .allowFollowRequest(true)
            .biometricEnabled(false)
            .build();
    }
}

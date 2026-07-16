package kh.edu.acleda.campuslink.modules.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import kh.edu.acleda.campuslink.modules.community.repository.FollowRepository;
import kh.edu.acleda.campuslink.modules.profile.dto.ProfileUpdateRequest;
import kh.edu.acleda.campuslink.modules.profile.repository.UserSettingRepository;
import kh.edu.acleda.campuslink.modules.profile.service.InterestService;
import kh.edu.acleda.campuslink.modules.profile.service.PortfolioService;
import kh.edu.acleda.campuslink.modules.profile.service.SocialLinkService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSettingRepository userSettingRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private PortfolioService portfolioService;
    @Mock
    private InterestService interestService;
    @Mock
    private SocialLinkService socialLinkService;
    @Mock
    private CurrentUserProvider currentUserProvider;

    /** Only userRepository is exercised here; the rest back getPublicProfile. */
    private ProfileServiceImpl newProfileService() {
        return new ProfileServiceImpl(
            userRepository, userSettingRepository, followRepository, portfolioService,
            interestService, socialLinkService, currentUserProvider);
    }

    @Test
    void updateCurrentProfileAllowsEditableFieldsOnly() {
        ProfileServiceImpl profileService = newProfileService();
        User user = activeUser("student@example.com");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        profileService.updateCurrentProfile(user.getEmail(), new ProfileUpdateRequest(
            "Updated Name",
            "updated_user",
            "+85512345678",
            "https://cdn.example.com/avatar.png",
            "Flutter learner",
            null,
            null,
            "ACLEDA University",
            WorkStatus.STUDENT,
            "Phnom Penh",
            Language.KHMER
        ));

        assertThat(user.getFullName()).isEqualTo("Updated Name");
        assertThat(user.getUsername()).isEqualTo("updated_user");
        assertThat(user.getPhoneNumber()).isEqualTo("+85512345678");
        assertThat(user.getLocation()).isEqualTo("Phnom Penh");
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getTotalXp()).isZero();
        assertThat(user.getLevel()).isEqualTo(1);
        assertThat(user.getPreferredLanguage()).isEqualTo(Language.KHMER);
        verify(userRepository).save(user);
    }

    @Test
    void updateCurrentProfileCanChangePreferredLanguageBackToEnglish() {
        ProfileServiceImpl profileService = newProfileService();
        User user = activeUser("student@example.com");
        user.setPreferredLanguage(Language.KHMER);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        profileService.updateCurrentProfile(user.getEmail(), new ProfileUpdateRequest(
            null, null, null, null, null, null, null, null, null, null, Language.ENGLISH
        ));

        assertThat(user.getPreferredLanguage()).isEqualTo(Language.ENGLISH);
    }

    @Test
    void profileUpdateRequestRejectsUnsupportedLanguageValue() {
        ObjectMapper objectMapper = new ObjectMapper();

        assertThatThrownBy(() -> objectMapper.readValue(
            "{\"preferredLanguage\":\"SPANISH\"}",
            ProfileUpdateRequest.class
        ))
            .isInstanceOf(Exception.class);
    }

    @Test
    void updateCurrentProfileRejectsDuplicateUsername() {
        ProfileServiceImpl profileService = newProfileService();
        User user = activeUser("student@example.com");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        assertThatThrownBy(() -> profileService.updateCurrentProfile(user.getEmail(), new ProfileUpdateRequest(
            null, "taken", null, null, null, null, null, null, null, null, null
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Username");
    }

    @Test
    void updateCurrentProfileRejectsBannedUser() {
        ProfileServiceImpl profileService = newProfileService();
        User user = activeUser("student@example.com");
        user.setAccountStatus(AccountStatus.BANNED);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> profileService.updateCurrentProfile(user.getEmail(), new ProfileUpdateRequest(
            "Updated", null, null, null, null, null, null, null, null, null, null
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("banned");
    }

    private User activeUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFullName("Student");
        user.setUsername("student");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setWorkStatus(WorkStatus.STUDENT);
        user.setLevel(1);
        return user;
    }
}

package kh.edu.acleda.campuslink.modules.profile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.FollowStatus;
import kh.edu.acleda.campuslink.modules.community.repository.FollowRepository;
import kh.edu.acleda.campuslink.modules.profile.dto.PublicProfileResponse;
import kh.edu.acleda.campuslink.modules.profile.entity.UserSetting;
import kh.edu.acleda.campuslink.modules.profile.repository.UserSettingRepository;
import kh.edu.acleda.campuslink.modules.profile.service.InterestService;
import kh.edu.acleda.campuslink.modules.profile.service.PortfolioService;
import kh.edu.acleda.campuslink.modules.profile.service.SocialLinkService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Pins who may see a profile, since this is the only place privacy is enforced. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublicProfileVisibilityTest {

    private static final UUID VIEWER_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();

    @Mock private UserRepository userRepository;
    @Mock private UserSettingRepository userSettingRepository;
    @Mock private FollowRepository followRepository;
    @Mock private PortfolioService portfolioService;
    @Mock private InterestService interestService;
    @Mock private SocialLinkService socialLinkService;
    @Mock private CurrentUserProvider currentUserProvider;

    private ProfileServiceImpl service;
    private User target;

    @BeforeEach
    void setUp() {
        service = new ProfileServiceImpl(userRepository, userSettingRepository, followRepository,
            portfolioService, interestService, socialLinkService, currentUserProvider);

        target = new User();
        target.setEmail("target@example.com");
        target.setUsername("target");
        target.setFullName("Target User");
        target.setAccountStatus(AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(target, "id", TARGET_ID);

        when(userRepository.findByUsername("target")).thenReturn(Optional.of(target));
        when(currentUserProvider.getCurrentUserId()).thenReturn(VIEWER_ID);
        when(portfolioService.getByUserId(any())).thenReturn(List.of());
        when(interestService.getByUserId(any(), any())).thenReturn(List.of());
        when(socialLinkService.getByUserId(any())).thenReturn(List.of());
    }

    @Test
    void publicProfileIsVisibleToAnyone() {
        privacy("PUBLIC");
        target.setCoverPreset(4);

        PublicProfileResponse response = service.getPublicProfile("target", Language.ENGLISH);

        assertThat(response.username()).isEqualTo("target");
        assertThat(response.coverPreset()).isEqualTo(4);
    }

    @Test
    void privateProfileIsHiddenFromOtherUsers() {
        privacy("PRIVATE");

        assertThatThrownBy(() -> service.getPublicProfile("target", Language.ENGLISH))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("private");
    }

    @Test
    void privateProfileIsStillVisibleToItsOwner() {
        privacy("PRIVATE");
        when(currentUserProvider.getCurrentUserId()).thenReturn(TARGET_ID);

        assertThat(service.getPublicProfile("target", Language.ENGLISH).username()).isEqualTo("target");
    }

    /** A friend is a mutual accepted follow: one direction is not enough. */
    @Test
    void friendsOnlyProfileIsHiddenWhenTheFollowIsOneWay() {
        privacy("FRIENDS");
        when(followRepository.existsByFollowerIdAndFollowingIdAndStatus(VIEWER_ID, TARGET_ID, FollowStatus.ACCEPTED))
            .thenReturn(true);
        when(followRepository.existsByFollowerIdAndFollowingIdAndStatus(TARGET_ID, VIEWER_ID, FollowStatus.ACCEPTED))
            .thenReturn(false);

        assertThatThrownBy(() -> service.getPublicProfile("target", Language.ENGLISH))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("friends only");
    }

    @Test
    void friendsOnlyProfileIsVisibleOnAMutualFollow() {
        privacy("FRIENDS");
        when(followRepository.existsByFollowerIdAndFollowingIdAndStatus(VIEWER_ID, TARGET_ID, FollowStatus.ACCEPTED))
            .thenReturn(true);
        when(followRepository.existsByFollowerIdAndFollowingIdAndStatus(TARGET_ID, VIEWER_ID, FollowStatus.ACCEPTED))
            .thenReturn(true);

        assertThat(service.getPublicProfile("target", Language.ENGLISH).username()).isEqualTo("target");
    }

    /** A user with no settings row must not become invisible. */
    @Test
    void missingSettingsRowDefaultsToPublic() {
        when(userSettingRepository.findByUserId(TARGET_ID)).thenReturn(Optional.empty());

        assertThat(service.getPublicProfile("target", Language.ENGLISH).username()).isEqualTo("target");
    }

    @Test
    void bannedUserReadsAsNotFoundRatherThanLeakingThatTheyExist() {
        privacy("PUBLIC");
        target.setAccountStatus(AccountStatus.BANNED);

        assertThatThrownBy(() -> service.getPublicProfile("target", Language.ENGLISH))
            .isInstanceOf(NotFoundException.class);
    }

    private void privacy(String value) {
        when(userSettingRepository.findByUserId(TARGET_ID))
            .thenReturn(Optional.of(UserSetting.builder().user(target).privacyProfile(value).build()));
    }
}

package kh.edu.acleda.campuslink.modules.profile;

import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.profile.dto.request.SocialLinkUpsertRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.SocialLinkResponse;
import kh.edu.acleda.campuslink.modules.profile.entity.UserSocialLink;
import kh.edu.acleda.campuslink.modules.profile.enums.SocialPlatform;
import kh.edu.acleda.campuslink.modules.profile.repository.UserSocialLinkRepository;
import kh.edu.acleda.campuslink.modules.profile.service.impl.SocialLinkServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SocialLinkServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private UserSocialLinkRepository userSocialLinkRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private SocialLinkServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SocialLinkServiceImpl(userSocialLinkRepository, userRepository, currentUserProvider);
        User user = new User();
        user.setEmail("user@example.com");
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userSocialLinkRepository.save(any(UserSocialLink.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void upsertCreatesTheLinkWhenThePlatformIsNew() {
        when(userSocialLinkRepository.findByUserIdAndPlatform(USER_ID, SocialPlatform.GITHUB))
            .thenReturn(Optional.empty());

        SocialLinkResponse response = service.upsert(
            new SocialLinkUpsertRequest(SocialPlatform.GITHUB, "https://github.com/RenMakara"));

        assertThat(response.platform()).isEqualTo(SocialPlatform.GITHUB);
        assertThat(response.url()).isEqualTo("https://github.com/RenMakara");
    }

    /** One link per platform, so a second upsert replaces rather than duplicates. */
    @Test
    void upsertReplacesTheUrlWhenThePlatformAlreadyExists() {
        UserSocialLink existing = UserSocialLink.builder()
            .platform(SocialPlatform.GITHUB).url("https://github.com/old").build();
        when(userSocialLinkRepository.findByUserIdAndPlatform(USER_ID, SocialPlatform.GITHUB))
            .thenReturn(Optional.of(existing));

        service.upsert(new SocialLinkUpsertRequest(SocialPlatform.GITHUB, "https://github.com/new"));

        assertThat(existing.getUrl()).isEqualTo("https://github.com/new");
    }

    /** Profile links are rendered tappable, so a javascript: URL stored here would run when another user opened the profile. */
    @Test
    void upsertRejectsANonHttpUrl() {
        assertThatThrownBy(() -> service.upsert(
            new SocialLinkUpsertRequest(SocialPlatform.WEBSITE, "javascript:alert(document.cookie)")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("http://");
    }

    @Test
    void upsertRejectsAProtocolRelativeUrl() {
        assertThatThrownBy(() -> service.upsert(
            new SocialLinkUpsertRequest(SocialPlatform.WEBSITE, "//evil.example.com")))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void upsertTrimsSurroundingWhitespace() {
        when(userSocialLinkRepository.findByUserIdAndPlatform(USER_ID, SocialPlatform.WEBSITE))
            .thenReturn(Optional.empty());

        SocialLinkResponse response = service.upsert(
            new SocialLinkUpsertRequest(SocialPlatform.WEBSITE, "  https://genlink.kh  "));

        assertThat(response.url()).isEqualTo("https://genlink.kh");
    }

    @Test
    void deleteRejectsAPlatformTheUserHasNoLinkFor() {
        when(userSocialLinkRepository.findByUserIdAndPlatform(USER_ID, SocialPlatform.TIKTOK))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(SocialPlatform.TIKTOK))
            .isInstanceOf(NotFoundException.class);
    }
}

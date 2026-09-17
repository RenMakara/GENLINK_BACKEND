package kh.edu.acleda.campuslink.modules.community;

import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.response.FollowResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Follow;
import kh.edu.acleda.campuslink.modules.community.enums.FollowStatus;
import kh.edu.acleda.campuslink.modules.community.repository.FollowRepository;
import kh.edu.acleda.campuslink.modules.community.service.impl.FollowServiceImpl;
import kh.edu.acleda.campuslink.modules.profile.entity.UserSetting;
import kh.edu.acleda.campuslink.modules.profile.repository.UserSettingRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FollowServiceImplTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();

    @Mock private FollowRepository followRepository;
    @Mock private UserSettingRepository userSettingRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter notificationEmitter;

    private FollowServiceImpl service;
    private User target;

    @BeforeEach
    void setUp() {
        service = new FollowServiceImpl(followRepository, userSettingRepository, userRepository, currentUserProvider, notificationEmitter);

        User me = user(ME, "me");
        target = user(TARGET, "target");

        when(currentUserProvider.getCurrentUserId()).thenReturn(ME);
        when(userRepository.findById(ME)).thenReturn(Optional.of(me));
        when(userRepository.findById(TARGET)).thenReturn(Optional.of(target));
        when(followRepository.save(any(Follow.class))).thenAnswer(i -> i.getArgument(0));
        when(followRepository.findByFollowerIdAndFollowingId(ME, TARGET)).thenReturn(Optional.empty());
        when(userSettingRepository.findByUserId(TARGET)).thenReturn(Optional.empty());
    }

    @Test
    void followingAnOpenProfileIsAcceptedImmediately() {
        FollowResponse response = service.follow(TARGET);

        assertThat(response.status()).isEqualTo(FollowStatus.ACCEPTED);
    }

    /** A PRIVATE profile gates its followers, mirroring how getPublicProfile hides it. */
    @Test
    void followingAPrivateProfileCreatesAPendingRequest() {
        when(userSettingRepository.findByUserId(TARGET))
            .thenReturn(Optional.of(UserSetting.builder().privacyProfile("PRIVATE").build()));

        assertThat(service.follow(TARGET).status()).isEqualTo(FollowStatus.PENDING);
    }

    /** Follow backs a toggle, and (follower_id, following_id) is unique. */
    @Test
    void followingTwiceDoesNotInsertASecondRow() {
        when(followRepository.findByFollowerIdAndFollowingId(ME, TARGET))
            .thenReturn(Optional.of(Follow.builder().following(target).status(FollowStatus.ACCEPTED).build()));

        assertThat(service.follow(TARGET).status()).isEqualTo(FollowStatus.ACCEPTED);
        verify(followRepository, never()).save(any(Follow.class));
    }

    /** Also guarded by chk_follows_no_self_follow; a clear error beats a 500. */
    @Test
    void followingYourselfIsRejected() {
        assertThatThrownBy(() -> service.follow(ME))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("cannot follow yourself");
    }

    @Test
    void followingABannedUserReadsAsNotFound() {
        target.setAccountStatus(AccountStatus.BANNED);

        assertThatThrownBy(() -> service.follow(TARGET)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void unfollowingSomeoneNeverFollowedIsANoOp() {
        service.unfollow(TARGET);

        verify(followRepository, never()).delete(any(Follow.class));
    }

    /** A friend is mutual: one-way ACCEPTED is a follower, not a friend. */
    @Test
    void statsReportFriendOnlyWhenTheFollowIsMutual() {
        when(followRepository.findByFollowerIdAndFollowingId(ME, TARGET))
            .thenReturn(Optional.of(Follow.builder().status(FollowStatus.ACCEPTED).build()));
        when(followRepository.existsByFollowerIdAndFollowingIdAndStatus(TARGET, ME, FollowStatus.ACCEPTED))
            .thenReturn(false);

        assertThat(service.stats(TARGET).friend()).isFalse();

        when(followRepository.existsByFollowerIdAndFollowingIdAndStatus(TARGET, ME, FollowStatus.ACCEPTED))
            .thenReturn(true);

        assertThat(service.stats(TARGET).friend()).isTrue();
    }

    @Test
    void acceptingARequestThatIsNotPendingReadsAsNotFound() {
        when(followRepository.findByFollowerIdAndFollowingId(TARGET, ME))
            .thenReturn(Optional.of(Follow.builder().follower(target).status(FollowStatus.ACCEPTED).build()));

        assertThatThrownBy(() -> service.acceptRequest(TARGET)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void acceptingAPendingRequestSettlesIt() {
        Follow pending = Follow.builder().follower(target).status(FollowStatus.PENDING).build();
        when(followRepository.findByFollowerIdAndFollowingId(TARGET, ME)).thenReturn(Optional.of(pending));

        assertThat(service.acceptRequest(TARGET).status()).isEqualTo(FollowStatus.ACCEPTED);
        assertThat(pending.getStatus()).isEqualTo(FollowStatus.ACCEPTED);
    }

    @Test
    void followingAnOpenProfileEmitsNewFollowerNotification() {
        service.follow(TARGET);

        org.mockito.ArgumentCaptor<kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest> captor =
            org.mockito.ArgumentCaptor.forClass(kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest req = captor.getValue();
        assertThat(req.recipientUserId()).isEqualTo(TARGET);
        assertThat(req.templateKey()).isEqualTo("COMMUNITY_NEW_FOLLOWER");
        assertThat(req.dedupeKey()).isEqualTo("new-follow:" + ME + ":" + TARGET);
        assertThat(req.channels()).containsExactlyInAnyOrder(
            kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel.IN_APP,
            kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel.MOBILE_PUSH);
    }

    @Test
    void followingAPrivateProfileEmitsFollowRequestNotification() {
        when(userSettingRepository.findByUserId(TARGET))
            .thenReturn(Optional.of(UserSetting.builder().privacyProfile("PRIVATE").build()));

        service.follow(TARGET);

        org.mockito.ArgumentCaptor<kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest> captor =
            org.mockito.ArgumentCaptor.forClass(kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest req = captor.getValue();
        assertThat(req.recipientUserId()).isEqualTo(TARGET);
        assertThat(req.templateKey()).isEqualTo("COMMUNITY_FOLLOW_REQUEST");
        assertThat(req.dedupeKey()).isEqualTo("follow-req:" + ME + ":" + TARGET);
    }

    @Test
    void acceptingAPendingRequestEmitsFollowAcceptedNotification() {
        Follow pending = Follow.builder().follower(target).status(FollowStatus.PENDING).build();
        when(followRepository.findByFollowerIdAndFollowingId(TARGET, ME)).thenReturn(Optional.of(pending));

        service.acceptRequest(TARGET);

        org.mockito.ArgumentCaptor<kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest> captor =
            org.mockito.ArgumentCaptor.forClass(kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest req = captor.getValue();
        assertThat(req.recipientUserId()).isEqualTo(TARGET);
        assertThat(req.templateKey()).isEqualTo("COMMUNITY_FOLLOW_ACCEPTED");
        assertThat(req.dedupeKey()).isEqualTo("follow-acc:" + ME + ":" + TARGET);
    }

    private User user(UUID id, String name) {
        User user = new User();
        user.setEmail(name + "@example.com");
        user.setFullName(name);
        user.setUsername(name);
        user.setAccountStatus(AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}

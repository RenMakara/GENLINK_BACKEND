package kh.edu.acleda.campuslink.modules.entertainment;

import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.DailyChallenge;
import kh.edu.acleda.campuslink.modules.entertainment.entity.UserChallenge;
import kh.edu.acleda.campuslink.modules.entertainment.enums.ChallengeStatus;
import kh.edu.acleda.campuslink.modules.entertainment.enums.ChallengeType;
import kh.edu.acleda.campuslink.modules.entertainment.service.impl.ChallengeRewardNotifierImpl;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
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
class ChallengeRewardNotifierImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CHALLENGE_ID = UUID.randomUUID();
    private static final UUID USER_CHALLENGE_ID = UUID.randomUUID();

    @Mock private NotificationEmitter notificationEmitter;

    private ChallengeRewardNotifierImpl notifier;
    private UserChallenge userChallenge;

    @BeforeEach
    void setUp() {
        notifier = new ChallengeRewardNotifierImpl(notificationEmitter);
        User user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        DailyChallenge challenge = DailyChallenge.builder()
            .titleEn("Take a quiz").descriptionEn("d").challengeType(ChallengeType.QUIZ).xpReward(20).build();
        ReflectionTestUtils.setField(challenge, "id", CHALLENGE_ID);
        userChallenge = UserChallenge.builder().challenge(challenge).user(user)
            .status(ChallengeStatus.CLAIMED).progress(100).build();
        ReflectionTestUtils.setField(userChallenge, "id", USER_CHALLENGE_ID);
    }

    @Test
    void emitsChallengeCompletionNotificationAfterRewardClaim() {
        notifier.notifyForClaimedChallenge(userChallenge, "Take a quiz");

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(request.recipientUserId()).isEqualTo(USER_ID);
        assertThat(request.templateKey()).isEqualTo("ENTERTAINMENT_CHALLENGE_COMPLETE");
        assertThat(request.type()).isEqualTo("ENTERTAINMENT_CHALLENGE");
        assertThat(request.parameters()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "challengeName", "Take a quiz", "rewardXp", "20"));
        assertThat(request.referenceType()).isEqualTo("CHALLENGE");
        assertThat(request.referenceId()).isEqualTo(CHALLENGE_ID);
        assertThat(request.dedupeKey()).isEqualTo("challenge-reward:" + CHALLENGE_ID);
    }

    @Test
    void skipsUnclaimedOrIncompleteChallengeData() {
        userChallenge.setStatus(ChallengeStatus.COMPLETED);
        notifier.notifyForClaimedChallenge(userChallenge, "Take a quiz");

        notifier.notifyForClaimedChallenge(userChallenge, "");

        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }
}

package kh.edu.acleda.campuslink.modules.entertainment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.ChallengeResponse;
import kh.edu.acleda.campuslink.modules.entertainment.entity.DailyChallenge;
import kh.edu.acleda.campuslink.modules.entertainment.entity.UserChallenge;
import kh.edu.acleda.campuslink.modules.entertainment.enums.ChallengeStatus;
import kh.edu.acleda.campuslink.modules.entertainment.enums.ChallengeType;
import kh.edu.acleda.campuslink.modules.entertainment.repository.DailyChallengeRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.UserChallengeRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.RewardService;
import kh.edu.acleda.campuslink.modules.entertainment.service.impl.ChallengeServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChallengeServiceImplTest {

    private static final Language EN = Language.ENGLISH;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CH_ID = UUID.randomUUID();

    @Mock private DailyChallengeRepository challengeRepository;
    @Mock private UserChallengeRepository userChallengeRepository;
    @Mock private RewardService rewardService;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private ChallengeServiceImpl service;
    private User user;
    private DailyChallenge challenge;

    @BeforeEach
    void setUp() {
        service = new ChallengeServiceImpl(challengeRepository, userChallengeRepository, rewardService,
            userRepository, currentUserProvider);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        challenge = DailyChallenge.builder().titleEn("Take a quiz").descriptionEn("d")
            .challengeType(ChallengeType.QUIZ).xpReward(20)
            .deadline(LocalDateTime.now().plusDays(5)).build();
        ReflectionTestUtils.setField(challenge, "id", CH_ID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(challengeRepository.findByIdAndDeletedAtIsNull(CH_ID)).thenReturn(Optional.of(challenge));
        when(userChallengeRepository.save(any(UserChallenge.class))).thenAnswer(i -> i.getArgument(0));
        when(userChallengeRepository.findByUserIdAndChallengeId(USER_ID, CH_ID)).thenReturn(Optional.empty());
    }

    private UserChallenge userChallenge(ChallengeStatus status) {
        UserChallenge uc = UserChallenge.builder().challenge(challenge).user(user)
            .status(status).progress(status == ChallengeStatus.IN_PROGRESS ? 0 : 100).build();
        ReflectionTestUtils.setField(uc, "id", UUID.randomUUID());
        return uc;
    }

    @Test
    void joinCreatesAnInProgressEntryWhenNotYetJoined() {
        ChallengeResponse r = service.join(CH_ID, EN);

        verify(userChallengeRepository).save(any(UserChallenge.class));
        assertThat(r.status()).isEqualTo(ChallengeStatus.IN_PROGRESS);
    }

    @Test
    void joinIsIdempotent() {
        when(userChallengeRepository.findByUserIdAndChallengeId(USER_ID, CH_ID))
            .thenReturn(Optional.of(userChallenge(ChallengeStatus.IN_PROGRESS)));

        service.join(CH_ID, EN);

        verify(userChallengeRepository, never()).save(any());
    }

    @Test
    void completeMovesInProgressToCompleted() {
        when(userChallengeRepository.findByUserIdAndChallengeId(USER_ID, CH_ID))
            .thenReturn(Optional.of(userChallenge(ChallengeStatus.IN_PROGRESS)));

        ChallengeResponse r = service.complete(CH_ID, EN);

        assertThat(r.status()).isEqualTo(ChallengeStatus.COMPLETED);
        assertThat(r.progress()).isEqualTo(100);
    }

    @Test
    void completeRequiresJoiningFirst() {
        assertThatThrownBy(() -> service.complete(CH_ID, EN))
            .isInstanceOf(ApiException.class).hasMessageContaining("Join the challenge");
    }

    @Test
    void claimAwardsXpOnceAndMarksClaimed() {
        when(userChallengeRepository.findByUserIdAndChallengeId(USER_ID, CH_ID))
            .thenReturn(Optional.of(userChallenge(ChallengeStatus.COMPLETED)));

        ChallengeResponse r = service.claim(CH_ID, EN);

        verify(rewardService).award(eq(user), eq("CHALLENGE"), eq(CH_ID), eq(20));
        assertThat(r.status()).isEqualTo(ChallengeStatus.CLAIMED);
    }

    @Test
    void claimRequiresCompletion() {
        when(userChallengeRepository.findByUserIdAndChallengeId(USER_ID, CH_ID))
            .thenReturn(Optional.of(userChallenge(ChallengeStatus.IN_PROGRESS)));

        assertThatThrownBy(() -> service.claim(CH_ID, EN))
            .isInstanceOf(ApiException.class).hasMessageContaining("Complete the challenge");
        verify(rewardService, never()).award(any(), any(), any(), anyInt());
    }

    @Test
    void claimCannotBeDoneTwice() {
        when(userChallengeRepository.findByUserIdAndChallengeId(USER_ID, CH_ID))
            .thenReturn(Optional.of(userChallenge(ChallengeStatus.CLAIMED)));

        assertThatThrownBy(() -> service.claim(CH_ID, EN))
            .isInstanceOf(ApiException.class).hasMessageContaining("already claimed");
        verify(rewardService, never()).award(any(), any(), any(), anyInt());
    }

    @Test
    void activeListExcludesExpiredAndDefaultsStatusToNotStarted() {
        DailyChallenge expired = DailyChallenge.builder().titleEn("old").descriptionEn("d")
            .challengeType(ChallengeType.HABIT).xpReward(10)
            .deadline(LocalDateTime.now().minusDays(1)).build();
        ReflectionTestUtils.setField(expired, "id", UUID.randomUUID());
        when(challengeRepository.findByDeletedAtIsNullOrderByCreatedAtDesc())
            .thenReturn(List.of(challenge, expired));

        List<ChallengeResponse> active = service.getActive(EN);

        assertThat(active).hasSize(1);
        assertThat(active.get(0).status()).isEqualTo(ChallengeStatus.NOT_STARTED);
    }
}

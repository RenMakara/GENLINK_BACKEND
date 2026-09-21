package kh.edu.acleda.campuslink.modules.productivity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.service.RewardService;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.FocusSessionStartRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.FocusSessionResponse;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.FocusStatsResponse;
import kh.edu.acleda.campuslink.modules.productivity.entity.FocusSession;
import kh.edu.acleda.campuslink.modules.productivity.entity.FocusSessionStatus;
import kh.edu.acleda.campuslink.modules.productivity.repository.FocusSessionRepository;
import kh.edu.acleda.campuslink.modules.productivity.service.impl.FocusSessionServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FocusSessionServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();

    @Mock private FocusSessionRepository focusSessionRepository;
    @Mock private RewardService rewardService;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private FocusSessionServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new FocusSessionServiceImpl(focusSessionRepository, rewardService,
            userRepository, currentUserProvider);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "totalXp", 0);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(focusSessionRepository.findFirstByUserIdAndStatusInOrderByStartedAtDesc(
            eq(USER_ID), any())).thenReturn(Optional.empty());
        when(focusSessionRepository.save(any(FocusSession.class))).thenAnswer(i -> {
            FocusSession s = i.getArgument(0);
            if (s.getId() == null) {
                ReflectionTestUtils.setField(s, "id", SESSION_ID);
            }
            return s;
        });
    }

    private FocusSession session(int durationMinutes, boolean completed) {
        LocalDateTime startedAt = LocalDateTime.now();
        FocusSession s = FocusSession.builder().user(user).mode("POMODORO")
            .durationMinutes(durationMinutes).completed(completed)
            .status(completed ? FocusSessionStatus.COMPLETED : FocusSessionStatus.ACTIVE)
            .focusedSeconds(completed ? durationMinutes * 60 : 0).xpEarned(0)
            .startedAt(startedAt)
            .activeStartedAt(completed ? null : startedAt)
            .build();
        ReflectionTestUtils.setField(s, "id", SESSION_ID);
        return s;
    }

    @Test
    void startCreatesAnIncompleteSessionWithNoXp() {
        FocusSessionResponse r = service.start(new FocusSessionStartRequest("POMODORO", 25, "RAIN"));

        assertThat(r.completed()).isFalse();
        assertThat(r.xpEarned()).isZero();
        assertThat(r.startedAt()).isNotNull();
    }

    @Test
    void completingA25MinuteSessionAwardsFiveXp() {
        FocusSession active = session(25, false);
        active.setStartedAt(LocalDateTime.now().minusMinutes(25));
        active.setActiveStartedAt(active.getStartedAt());
        when(focusSessionRepository.findByIdAndUserIdForUpdate(SESSION_ID, USER_ID))
            .thenReturn(Optional.of(active));

        FocusSessionResponse r = service.complete(SESSION_ID);

        assertThat(r.completed()).isTrue();
        assertThat(r.xpEarned()).isEqualTo(5);
        assertThat(r.endedAt()).isNotNull();
        verify(rewardService).award(any(), eq("FOCUS_SESSION"), any(), eq(5));
    }

    @Test
    void xpIsCappedForVeryLongSessions() {
        FocusSession active = session(200, false);
        active.setStartedAt(LocalDateTime.now().minusMinutes(200));
        active.setActiveStartedAt(active.getStartedAt());
        when(focusSessionRepository.findByIdAndUserIdForUpdate(SESSION_ID, USER_ID))
            .thenReturn(Optional.of(active)); // 200/5 = 40, capped at 30

        assertThat(service.complete(SESSION_ID).xpEarned()).isEqualTo(30);
    }

    @Test
    void aSessionShorterThanFiveMinutesEarnsNoXp() {
        FocusSession active = session(3, false);
        active.setStartedAt(LocalDateTime.now().minusMinutes(3));
        active.setActiveStartedAt(active.getStartedAt());
        when(focusSessionRepository.findByIdAndUserIdForUpdate(SESSION_ID, USER_ID))
            .thenReturn(Optional.of(active));

        assertThat(service.complete(SESSION_ID).xpEarned()).isZero();
        verify(rewardService, never()).award(any(), any(), any(), anyInt());
    }

    @Test
    void completingAnAlreadyCompletedSessionDoesNotPayAgain() {
        FocusSession done = session(25, true);
        done.setXpEarned(5);
        when(focusSessionRepository.findByIdAndUserIdForUpdate(SESSION_ID, USER_ID))
            .thenReturn(Optional.of(done));

        service.complete(SESSION_ID);

        verify(rewardService, never()).award(any(), any(), any(), anyInt());
    }

    @Test
    void statsAggregateTodayAndAllTime() {
        FocusSession today = session(25, true);
        today.setXpEarned(5);
        FocusSession past = FocusSession.builder().user(user).durationMinutes(50).completed(true)
            .status(FocusSessionStatus.COMPLETED).focusedSeconds(50 * 60).xpEarned(10)
            .startedAt(LocalDateTime.now().minusDays(3)).build();
        ReflectionTestUtils.setField(past, "id", UUID.randomUUID());
        when(focusSessionRepository.findByUserIdAndCompletedTrue(USER_ID)).thenReturn(List.of(today, past));

        FocusStatsResponse s = service.stats();

        assertThat(s.totalSessions()).isEqualTo(2);
        assertThat(s.totalMinutes()).isEqualTo(75);
        assertThat(s.totalXp()).isEqualTo(15);
        assertThat(s.todaySessions()).isEqualTo(1);
        assertThat(s.todayMinutes()).isEqualTo(25);
    }

    @Test
    void completingAnUnknownSessionReadsAsNotFound() {
        when(focusSessionRepository.findByIdAndUserIdForUpdate(SESSION_ID, USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(SESSION_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void pauseAndResumeAreIdempotentStateTransitions() {
        FocusSession active = session(25, false);
        when(focusSessionRepository.findByIdAndUserIdForUpdate(SESSION_ID, USER_ID))
            .thenReturn(Optional.of(active));

        FocusSessionResponse paused = service.pause(SESSION_ID);
        assertThat(paused.status()).isEqualTo(FocusSessionStatus.PAUSED);

        FocusSessionResponse pausedAgain = service.pause(SESSION_ID);
        assertThat(pausedAgain.status()).isEqualTo(FocusSessionStatus.PAUSED);

        FocusSessionResponse resumed = service.resume(SESSION_ID);
        assertThat(resumed.status()).isEqualTo(FocusSessionStatus.ACTIVE);

        FocusSessionResponse resumedAgain = service.resume(SESSION_ID);
        assertThat(resumedAgain.status()).isEqualTo(FocusSessionStatus.ACTIVE);
    }

    @Test
    void leavingDoesNotAwardXpAndIsIdempotent() {
        FocusSession active = session(25, false);
        when(focusSessionRepository.findByIdAndUserIdForUpdate(SESSION_ID, USER_ID))
            .thenReturn(Optional.of(active));

        FocusSessionResponse abandoned = service.abandon(SESSION_ID);
        assertThat(abandoned.status()).isEqualTo(FocusSessionStatus.ABANDONED);
        assertThat(abandoned.xpEarned()).isZero();
        verify(rewardService, never()).award(any(), any(), any(), anyInt());

        assertThat(service.abandon(SESSION_ID).status()).isEqualTo(FocusSessionStatus.ABANDONED);
    }

    @Test
    void startReturnsExistingActiveSessionInsteadOfCreatingADuplicate() {
        FocusSession active = session(25, false);
        when(focusSessionRepository.findFirstByUserIdAndStatusInOrderByStartedAtDesc(
            eq(USER_ID), any())).thenReturn(Optional.of(active));

        FocusSessionResponse response = service.start(
            new FocusSessionStartRequest("POMODORO", 50, null));

        assertThat(response.sessionId()).isEqualTo(SESSION_ID);
        verify(focusSessionRepository, never()).save(any(FocusSession.class));
    }

    @Test
    void completionUsesActualFocusedTimeInsteadOfPlannedDuration() {
        FocusSession active = session(90, false);
        active.setStartedAt(LocalDateTime.now().minusMinutes(3));
        active.setActiveStartedAt(active.getStartedAt());
        when(focusSessionRepository.findByIdAndUserIdForUpdate(SESSION_ID, USER_ID))
            .thenReturn(Optional.of(active));

        FocusSessionResponse response = service.complete(SESSION_ID);

        assertThat(response.focusedSeconds()).isLessThan(5 * 60);
        assertThat(response.xpEarned()).isZero();
    }
}

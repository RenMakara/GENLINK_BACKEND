package kh.edu.acleda.campuslink.modules.productivity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.repository.XpHistoryRepository;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.FocusSessionStartRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.FocusSessionResponse;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.FocusStatsResponse;
import kh.edu.acleda.campuslink.modules.productivity.entity.FocusSession;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FocusSessionServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();

    @Mock private FocusSessionRepository focusSessionRepository;
    @Mock private XpHistoryRepository xpHistoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private FocusSessionServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new FocusSessionServiceImpl(focusSessionRepository, xpHistoryRepository,
            userRepository, currentUserProvider);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "totalXp", 0);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(focusSessionRepository.save(any(FocusSession.class))).thenAnswer(i -> {
            FocusSession s = i.getArgument(0);
            if (s.getId() == null) {
                ReflectionTestUtils.setField(s, "id", SESSION_ID);
            }
            return s;
        });
    }

    private FocusSession session(int durationMinutes, boolean completed) {
        FocusSession s = FocusSession.builder().user(user).mode("POMODORO")
            .durationMinutes(durationMinutes).completed(completed).xpEarned(0)
            .startedAt(LocalDateTime.now()).build();
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
        when(focusSessionRepository.findByIdAndUserId(SESSION_ID, USER_ID))
            .thenReturn(Optional.of(session(25, false)));

        FocusSessionResponse r = service.complete(SESSION_ID);

        assertThat(r.completed()).isTrue();
        assertThat(r.xpEarned()).isEqualTo(5);
        assertThat(r.endedAt()).isNotNull();
        assertThat(user.getTotalXp()).isEqualTo(5);
        verify(xpHistoryRepository).save(any());
    }

    @Test
    void xpIsCappedForVeryLongSessions() {
        when(focusSessionRepository.findByIdAndUserId(SESSION_ID, USER_ID))
            .thenReturn(Optional.of(session(200, false))); // 200/5 = 40, capped at 30

        assertThat(service.complete(SESSION_ID).xpEarned()).isEqualTo(30);
    }

    @Test
    void aSessionShorterThanFiveMinutesEarnsNoXp() {
        when(focusSessionRepository.findByIdAndUserId(SESSION_ID, USER_ID))
            .thenReturn(Optional.of(session(3, false)));

        assertThat(service.complete(SESSION_ID).xpEarned()).isZero();
        assertThat(user.getTotalXp()).isZero();
        verify(xpHistoryRepository, never()).save(any());
    }

    @Test
    void completingAnAlreadyCompletedSessionDoesNotPayAgain() {
        FocusSession done = session(25, true);
        done.setXpEarned(5);
        when(focusSessionRepository.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(done));

        service.complete(SESSION_ID);

        assertThat(user.getTotalXp()).isZero();
        verify(xpHistoryRepository, never()).save(any());
    }

    @Test
    void statsAggregateTodayAndAllTime() {
        FocusSession today = session(25, true);
        today.setXpEarned(5);
        FocusSession past = FocusSession.builder().user(user).durationMinutes(50).completed(true)
            .xpEarned(10).startedAt(LocalDateTime.now().minusDays(3)).build();
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
        when(focusSessionRepository.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(SESSION_ID)).isInstanceOf(NotFoundException.class);
    }
}

package kh.edu.acleda.campuslink.modules.entertainment;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.LeaderboardResponse;
import kh.edu.acleda.campuslink.modules.entertainment.enums.LeaderboardCategory;
import kh.edu.acleda.campuslink.modules.entertainment.enums.LeaderboardPeriod;
import kh.edu.acleda.campuslink.modules.entertainment.repository.XpHistoryRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.impl.LeaderboardServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeaderboardServiceImplTest {

    private static final UUID U1 = UUID.randomUUID();
    private static final UUID U2 = UUID.randomUUID();
    private static final UUID ME = UUID.randomUUID();

    @Mock private XpHistoryRepository xpHistoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private LeaderboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LeaderboardServiceImpl(xpHistoryRepository, userRepository, currentUserProvider);
        when(currentUserProvider.getCurrentUserId()).thenReturn(ME);
        when(userRepository.findAllById(any()))
            .thenReturn(List.of(user(U1, "Sok", 4), user(U2, "Dara", 2), user(ME, "Me", 1)));
    }

    private User user(UUID id, String name, int level) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        ReflectionTestUtils.setField(u, "fullName", name);
        ReflectionTestUtils.setField(u, "level", level);
        return u;
    }

    private Object[] row(UUID id, long points) {
        return new Object[]{id, points};
    }

    @Test
    void overallLeaderboardRanksAndReportsMyStanding() {
        when(xpHistoryRepository.leaderboardOverall(any()))
            .thenReturn(List.of(row(U1, 300L), row(U2, 150L), row(ME, 50L)));

        LeaderboardResponse r = service.getLeaderboard(LeaderboardCategory.OVERALL_XP, LeaderboardPeriod.ALL_TIME);

        assertThat(r.entries()).hasSize(3);
        assertThat(r.entries().get(0).rank()).isEqualTo(1);
        assertThat(r.entries().get(0).userId()).isEqualTo(U1);
        assertThat(r.entries().get(0).points()).isEqualTo(300);
        assertThat(r.entries().get(0).name()).isEqualTo("Sok");
        assertThat(r.me().rank()).isEqualTo(3);
        assertThat(r.me().points()).isEqualTo(50);
    }

    @Test
    void categoryUsesTheSourceFilter() {
        when(xpHistoryRepository.leaderboardByCategory(any(), any())).thenReturn(List.<Object[]>of(row(U1, 40L)));

        service.getLeaderboard(LeaderboardCategory.QUIZ_PERFORMANCE, LeaderboardPeriod.WEEKLY);

        verify(xpHistoryRepository).leaderboardByCategory(any(), eq(List.of("QUIZ")));
    }

    @Test
    void callerWithNoPointsHasANullRank() {
        when(xpHistoryRepository.leaderboardOverall(any())).thenReturn(List.of(row(U1, 300L), row(U2, 150L)));

        LeaderboardResponse.Me me = service.getLeaderboard(
            LeaderboardCategory.OVERALL_XP, LeaderboardPeriod.ALL_TIME).me();

        assertThat(me.rank()).isNull();
        assertThat(me.points()).isZero();
    }

    @Test
    void friendsPeriodIsNotImplementedYet() {
        assertThatThrownBy(() -> service.getLeaderboard(LeaderboardCategory.OVERALL_XP, LeaderboardPeriod.FRIENDS))
            .isInstanceOf(ApiException.class).hasMessageContaining("not available");
    }
}

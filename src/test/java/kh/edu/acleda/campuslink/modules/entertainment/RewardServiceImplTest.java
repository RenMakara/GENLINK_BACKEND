package kh.edu.acleda.campuslink.modules.entertainment;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.XpHistory;
import kh.edu.acleda.campuslink.modules.entertainment.repository.XpHistoryRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.BadgeAwarder;
import kh.edu.acleda.campuslink.modules.entertainment.service.impl.RewardServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardServiceImplTest {

    @Mock private XpHistoryRepository xpHistoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private BadgeAwarder badgeAwarder;

    private RewardServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new RewardServiceImpl(xpHistoryRepository, userRepository, badgeAwarder);
        user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "totalXp", 0);
        ReflectionTestUtils.setField(user, "level", 1);
        lenient().when(userRepository.findByIdForUpdate(user.getId())).thenReturn(java.util.Optional.of(user));
    }

    @Test
    void awardAddsXpRecomputesLevelWritesHistoryAndChecksBadges() {
        UUID source = UUID.randomUUID();

        int awarded = service.award(user, "QUIZ", source, 150);

        assertThat(awarded).isEqualTo(150);
        assertThat(user.getTotalXp()).isEqualTo(150);
        assertThat(user.getLevel()).isEqualTo(2); // 1 + 150/100
        verify(userRepository).save(user);
        verify(xpHistoryRepository).save(any(XpHistory.class));
        verify(badgeAwarder).evaluate(user, "QUIZ");
    }

    @Test
    void levelClimbsWithTotalXp() {
        ReflectionTestUtils.setField(user, "totalXp", 260);

        service.award(user, "TASK", UUID.randomUUID(), 50); // 310 -> level 4

        assertThat(user.getLevel()).isEqualTo(4);
    }

    @Test
    void nonPositiveXpIsANoOp() {
        int awarded = service.award(user, "TASK", UUID.randomUUID(), 0);

        assertThat(awarded).isZero();
        assertThat(user.getTotalXp()).isZero();
        verify(userRepository, never()).save(any());
        verify(xpHistoryRepository, never()).save(any());
        verify(badgeAwarder, never()).evaluate(any(), eq("TASK"));
    }

    @Test
    void duplicateLogicalSourceDoesNotAwardXpAgain() {
        UUID source = UUID.randomUUID();
        when(xpHistoryRepository.existsByUserIdAndSourceTypeAndSourceId(user.getId(), "TASK", source))
            .thenReturn(true);

        int awarded = service.award(user, "TASK", source, 10);

        assertThat(awarded).isZero();
        assertThat(user.getTotalXp()).isZero();
        verify(userRepository, never()).save(any());
        verify(xpHistoryRepository, never()).save(any());
        verify(badgeAwarder, never()).evaluate(any(), any());
    }
}

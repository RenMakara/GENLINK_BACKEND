package kh.edu.acleda.campuslink.modules.entertainment;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.Badge;
import kh.edu.acleda.campuslink.modules.entertainment.entity.UserBadge;
import kh.edu.acleda.campuslink.modules.entertainment.repository.BadgeRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.UserBadgeRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.XpHistoryRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.BadgeAwardNotifier;
import kh.edu.acleda.campuslink.modules.entertainment.service.impl.BadgeAwarderImpl;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BadgeAwarderImplTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private BadgeRepository badgeRepository;
    @Mock private UserBadgeRepository userBadgeRepository;
    @Mock private XpHistoryRepository xpHistoryRepository;
    @Mock private BadgeAwardNotifier badgeAwardNotifier;

    private BadgeAwarderImpl awarder;
    private User user;

    @BeforeEach
    void setUp() {
        awarder = new BadgeAwarderImpl(
            badgeRepository, userBadgeRepository, xpHistoryRepository, badgeAwardNotifier);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "totalXp", 0);
        when(userBadgeRepository.existsByUserIdAndBadgeId(eq(USER_ID), any())).thenReturn(false);
        when(xpHistoryRepository.countByUserIdAndSourceType(eq(USER_ID), anyString())).thenReturn(0L);
    }

    private Badge badge(String code) {
        Badge b = Badge.builder().code(code).nameEn(code).build();
        ReflectionTestUtils.setField(b, "id", UUID.randomUUID());
        return b;
    }

    private void catalog(Badge... badges) {
        when(badgeRepository.findByDeletedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(badges));
    }

    @Test
    void unlocksQuizChampionAtThreeQuizzes() {
        Badge quizChampion = badge("QUIZ_CHAMPION");
        catalog(quizChampion);
        when(xpHistoryRepository.countByUserIdAndSourceType(USER_ID, "QUIZ")).thenReturn(3L);

        awarder.evaluate(user, "QUIZ");

        verify(userBadgeRepository).saveAndFlush(any(UserBadge.class));
        verify(badgeAwardNotifier).notifyForAwardedBadge(user, quizChampion);
    }

    @Test
    void doesNotUnlockBelowThreshold() {
        catalog(badge("QUIZ_CHAMPION"));
        when(xpHistoryRepository.countByUserIdAndSourceType(USER_ID, "QUIZ")).thenReturn(2L);

        awarder.evaluate(user, "QUIZ");

        verify(userBadgeRepository, never()).saveAndFlush(any());
    }

    @Test
    void skipsBadgesAlreadyEarned() {
        Badge quiz = badge("QUIZ_CHAMPION");
        catalog(quiz);
        when(xpHistoryRepository.countByUserIdAndSourceType(USER_ID, "QUIZ")).thenReturn(5L);
        when(userBadgeRepository.existsByUserIdAndBadgeId(USER_ID, quiz.getId())).thenReturn(true);

        awarder.evaluate(user, "QUIZ");

        verify(userBadgeRepository, never()).saveAndFlush(any());
    }

    @Test
    void communicationHelperUsesTotalXp() {
        catalog(badge("COMMUNICATION_HELPER"));
        ReflectionTestUtils.setField(user, "totalXp", 300);

        awarder.evaluate(user, "EVENT_RSVP");

        verify(userBadgeRepository).saveAndFlush(any(UserBadge.class));
    }

    @Test
    void productivityHeroSumsProductivitySources() {
        catalog(badge("PRODUCTIVITY_HERO"));
        when(xpHistoryRepository.countByUserIdAndSourceType(USER_ID, "HABIT")).thenReturn(4L);
        when(xpHistoryRepository.countByUserIdAndSourceType(USER_ID, "TASK")).thenReturn(3L);
        when(xpHistoryRepository.countByUserIdAndSourceType(USER_ID, "FOCUS_SESSION")).thenReturn(3L);

        awarder.evaluate(user, "HABIT");

        verify(userBadgeRepository).saveAndFlush(any(UserBadge.class));
    }
}

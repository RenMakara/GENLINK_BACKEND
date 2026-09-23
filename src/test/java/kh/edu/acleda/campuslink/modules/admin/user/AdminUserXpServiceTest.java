package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserXpResponse;
import kh.edu.acleda.campuslink.modules.entertainment.entity.Badge;
import kh.edu.acleda.campuslink.modules.entertainment.entity.UserBadge;
import kh.edu.acleda.campuslink.modules.entertainment.entity.XpHistory;
import kh.edu.acleda.campuslink.modules.entertainment.repository.UserBadgeRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.XpHistoryRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class AdminUserXpServiceTest {

    private UserRepository userRepository;
    private XpHistoryRepository xpHistoryRepository;
    private UserBadgeRepository userBadgeRepository;
    private AdminUserXpService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        xpHistoryRepository = mock(XpHistoryRepository.class);
        userBadgeRepository = mock(UserBadgeRepository.class);
        service = new AdminUserXpService(userRepository, xpHistoryRepository, userBadgeRepository);
    }

    @Test
    void returnsSummaryLedgerAndEarnedBadgesIncludingNegativeEntries() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID newestEntryId = UUID.randomUUID();
        UUID olderEntryId = UUID.randomUUID();
        UUID badgeId = UUID.randomUUID();
        User user = mock(User.class);
        XpHistory newestEntry = mock(XpHistory.class);
        XpHistory olderEntry = mock(XpHistory.class);
        XpHistoryRepository.SourceBreakdownProjection quiz = mock(
            XpHistoryRepository.SourceBreakdownProjection.class);
        XpHistoryRepository.SourceBreakdownProjection adjustment = mock(
            XpHistoryRepository.SourceBreakdownProjection.class);
        Badge badge = mock(Badge.class);
        UserBadge userBadge = mock(UserBadge.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(user.getTotalXp()).thenReturn(275);
        when(quiz.getSourceType()).thenReturn("QUIZ");
        when(quiz.getXpAmount()).thenReturn(300L);
        when(quiz.getEntryCount()).thenReturn(3L);
        when(adjustment.getSourceType()).thenReturn("ADMIN_ADJUSTMENT");
        when(adjustment.getXpAmount()).thenReturn(-25L);
        when(adjustment.getEntryCount()).thenReturn(1L);
        when(xpHistoryRepository.summarizeByUserId(userId)).thenReturn(List.of(quiz, adjustment));

        when(newestEntry.getId()).thenReturn(newestEntryId);
        when(newestEntry.getSourceType()).thenReturn("ADMIN_ADJUSTMENT");
        when(newestEntry.getSourceId()).thenReturn(null);
        when(newestEntry.getXpAmount()).thenReturn(-25);
        when(newestEntry.getCreatedAt()).thenReturn(Instant.parse("2026-09-23T10:00:00Z"));
        when(olderEntry.getId()).thenReturn(olderEntryId);
        when(olderEntry.getSourceType()).thenReturn("QUIZ");
        when(olderEntry.getSourceId()).thenReturn(UUID.randomUUID());
        when(olderEntry.getXpAmount()).thenReturn(300);
        when(olderEntry.getCreatedAt()).thenReturn(Instant.parse("2026-09-22T10:00:00Z"));
        PageRequest pageRequest = PageRequest.of(0, 25);
        when(xpHistoryRepository.findPageByUserId(userId, pageRequest)).thenReturn(
            new PageImpl<>(List.of(newestEntry, olderEntry), pageRequest, 2));

        when(userBadge.getBadge()).thenReturn(badge);
        when(badge.getId()).thenReturn(badgeId);
        when(badge.getCode()).thenReturn("QUIZ_CHAMPION");
        when(badge.getNameEn()).thenReturn("Quiz Champion");
        when(badge.getNameKm()).thenReturn("អ្នកជើងខ្លាំង Quiz");
        when(badge.getDescriptionEn()).thenReturn("A quiz winner.");
        when(badge.getDescriptionKm()).thenReturn("អ្នកឈ្នះ Quiz។");
        when(badge.getRequirement()).thenReturn("Earn XP on 3 different quizzes.");
        when(badge.getIconUrl()).thenReturn("https://cdn.example.test/quiz.png");
        when(badge.getDeletedAt()).thenReturn(null);
        when(userBadge.getUnlockedAt()).thenReturn(LocalDateTime.of(2026, 9, 23, 11, 0));
        when(userBadgeRepository.findByUserIdOrderByUnlockedAtDesc(userId)).thenReturn(List.of(userBadge));

        AdminUserXpResponse response = service.get(userId, 0, 25);

        assertThat(response.totalXp()).isEqualTo(275);
        assertThat(response.level()).isEqualTo(3);
        assertThat(response.xpIntoLevel()).isEqualTo(75);
        assertThat(response.xpToNextLevel()).isEqualTo(25);
        assertThat(response.levelProgressPercent()).isEqualTo(75);
        assertThat(response.breakdown())
            .extracting(AdminUserXpResponse.SourceBreakdown::xpAmount)
            .containsExactly(300L, -25L);
        assertThat(response.breakdown()).extracting(AdminUserXpResponse.SourceBreakdown::xpAmount)
            .satisfies(values -> assertThat(values.stream().mapToLong(Long::longValue).sum()).isEqualTo(275));
        assertThat(response.ledger().content()).extracting(AdminUserXpResponse.LedgerEntry::xpAmount)
            .containsExactly(-25, 300);
        assertThat(response.badges()).hasSize(1);
        assertThat(response.badges().get(0).badgeId()).isEqualTo(badgeId);

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);
        assertThat(json).contains("\"xpAmount\":-25", "QUIZ_CHAMPION");
        assertThat(json).doesNotContain("passwordHash", "tokenHash", "details");
        verify(xpHistoryRepository).findPageByUserId(userId, pageRequest);
    }

    @Test
    void excludesDeletedBadgesAndRejectsUnknownUsersOrInvalidPages() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(userId, 0, 25))
            .isInstanceOf(NotFoundException.class);

        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(user.getTotalXp()).thenReturn(0);

        assertThatThrownBy(() -> service.get(userId, -1, 25))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("page must be zero or greater");
        assertThatThrownBy(() -> service.get(userId, 0, 24))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("size must be one of 25, 50, or 100");
    }
}

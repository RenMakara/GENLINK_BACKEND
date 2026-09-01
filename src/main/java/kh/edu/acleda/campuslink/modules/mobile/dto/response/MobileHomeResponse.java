package kh.edu.acleda.campuslink.modules.mobile.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;

/**
 * Everything the Home Super Dashboard needs, in one call.
 *
 * <p>The card records are nullable on purpose: a brand new user has no budget, no
 * RSVP and possibly no challenge running. Null means "show this card's empty state",
 * not an error. Lists are empty rather than null.
 */
public record MobileHomeResponse(
    HomeUser user,
    TodayProgress todayProgress,
    ChallengePreview dailyChallenge,
    FinanceSnapshot financeSnapshot,
    UpcomingEvent upcomingEvent,
    List<CommunitySuggestion> recommendedCommunities,
    MotivationCard dailyMotivation,
    List<FeedPreview> feedPreview
) {

    public record HomeUser(
        UUID userId,
        String fullName,
        String avatarUrl,
        int level,
        int totalXp
    ) {}

    /** Drives the progress ring. Totals can be 0, so the client must guard the divide. */
    public record TodayProgress(
        long habitsCompletedToday,
        long habitsTotal,
        long tasksCompleted,
        long tasksTotal
    ) {}

    public record ChallengePreview(
        UUID challengeId,
        String title,
        String description,
        Integer xpReward,
        LocalDateTime deadline
    ) {}

    /**
     * Budget values come from the selected-currency budget; spend is calculated live from
     * expenses in that same currency. Saving amounts are aggregated only for that currency.
     */
    public record FinanceSnapshot(
        Currency currency,
        BigDecimal totalBudget,
        BigDecimal spentAmount,
        BigDecimal remainingAmount,
        String budgetStatus,
        BigDecimal savingTarget,
        BigDecimal savingCurrent
    ) {}

    public record UpcomingEvent(
        UUID eventId,
        String title,
        LocalDateTime dateTime,
        String locationName,
        String coverImageUrl
    ) {}

    public record CommunitySuggestion(
        UUID communityId,
        String name,
        String slug,
        String category,
        Integer memberCount,
        String avatarUrl
    ) {}

    public record MotivationCard(
        UUID motivationId,
        String title,
        String content
    ) {}

    public record FeedPreview(
        UUID postId,
        String authorName,
        String authorAvatarUrl,
        String contentText,
        String imageUrl,
        Integer likeCount,
        Instant createdAt
    ) {}
}

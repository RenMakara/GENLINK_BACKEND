package kh.edu.acleda.campuslink.modules.bff.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.Community;
import kh.edu.acleda.campuslink.modules.community.entity.Event;
import kh.edu.acleda.campuslink.modules.community.entity.EventRsvp;
import kh.edu.acleda.campuslink.modules.community.entity.Post;
import kh.edu.acleda.campuslink.modules.community.enums.CommunityPrivacy;
import kh.edu.acleda.campuslink.modules.community.enums.RsvpStatus;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import kh.edu.acleda.campuslink.modules.community.repository.CommunityMemberRepository;
import kh.edu.acleda.campuslink.modules.community.repository.CommunityRepository;
import kh.edu.acleda.campuslink.modules.community.repository.EventRsvpRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PostRepository;
import kh.edu.acleda.campuslink.modules.entertainment.entity.DailyChallenge;
import kh.edu.acleda.campuslink.modules.entertainment.repository.DailyChallengeRepository;
import kh.edu.acleda.campuslink.modules.finance.entity.Budget;
import kh.edu.acleda.campuslink.modules.finance.entity.SavingGoal;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus;
import kh.edu.acleda.campuslink.modules.finance.repository.BudgetRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.SavingGoalRepository;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileHomeResponse;
import kh.edu.acleda.campuslink.modules.productivity.entity.MotivationContent;
import kh.edu.acleda.campuslink.modules.productivity.repository.HabitCheckInRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.HabitRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.MotivationContentRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.TaskRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.localization.LocalizedTextSelector;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates the Home Super Dashboard.
 *
 * <p>This is the BFF-lite pattern: one call for a dashboard that would otherwise cost
 * the client eight. Reading across domains is what this module exists for, so the
 * coupling lives here rather than leaking into the domain modules.
 *
 * <p>Every card degrades independently - a user with no budget still gets their
 * habits, motivation and feed. Missing cards come back null, not as an error.
 */
@Service
public class MobileHomeBffServiceImpl implements MobileHomeBffService {

    private static final int SUGGESTED_COMMUNITIES = 5;
    private static final int FEED_PREVIEW_POSTS = 3;
    private static final Set<RsvpStatus> ATTENDING = Set.of(RsvpStatus.JOINED, RsvpStatus.INTERESTED);

    private final UserRepository userRepository;
    private final HabitRepository habitRepository;
    private final HabitCheckInRepository habitCheckInRepository;
    private final TaskRepository taskRepository;
    private final MotivationContentRepository motivationContentRepository;
    private final DailyChallengeRepository dailyChallengeRepository;
    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;
    private final SavingGoalRepository savingGoalRepository;
    private final EventRsvpRepository eventRsvpRepository;
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final PostRepository postRepository;

    public MobileHomeBffServiceImpl(UserRepository userRepository,
                                    HabitRepository habitRepository,
                                    HabitCheckInRepository habitCheckInRepository,
                                    TaskRepository taskRepository,
                                    MotivationContentRepository motivationContentRepository,
                                    DailyChallengeRepository dailyChallengeRepository,
                                    BudgetRepository budgetRepository,
                                    ExpenseRepository expenseRepository,
                                    SavingGoalRepository savingGoalRepository,
                                    EventRsvpRepository eventRsvpRepository,
                                    CommunityRepository communityRepository,
                                    CommunityMemberRepository communityMemberRepository,
                                    PostRepository postRepository) {
        this.userRepository = userRepository;
        this.habitRepository = habitRepository;
        this.habitCheckInRepository = habitCheckInRepository;
        this.taskRepository = taskRepository;
        this.motivationContentRepository = motivationContentRepository;
        this.dailyChallengeRepository = dailyChallengeRepository;
        this.budgetRepository = budgetRepository;
        this.expenseRepository = expenseRepository;
        this.savingGoalRepository = savingGoalRepository;
        this.eventRsvpRepository = eventRsvpRepository;
        this.communityRepository = communityRepository;
        this.communityMemberRepository = communityMemberRepository;
        this.postRepository = postRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public MobileHomeResponse getHome(String email, Language language) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new NotFoundException("User not found"));
        UUID userId = user.getId();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        return new MobileHomeResponse(
            new MobileHomeResponse.HomeUser(userId, user.getFullName(), user.getAvatarUrl(),
                user.getLevel(), user.getTotalXp()),
            todayProgress(userId, today),
            dailyChallenge(now, language),
            financeSnapshot(userId, today),
            upcomingEvent(userId, now),
            recommendedCommunities(userId),
            dailyMotivation(today, language),
            feedPreview()
        );
    }

    private MobileHomeResponse.TodayProgress todayProgress(UUID userId, LocalDate today) {
        return new MobileHomeResponse.TodayProgress(
            habitCheckInRepository.countByUserIdAndCheckInDateAndCompletedTrue(userId, today),
            habitRepository.countByUserIdAndDeletedAtIsNull(userId),
            taskRepository.countByUserIdAndDeletedAtIsNullAndCompletedTrue(userId),
            taskRepository.countByUserIdAndDeletedAtIsNull(userId)
        );
    }

    private MobileHomeResponse.ChallengePreview dailyChallenge(LocalDateTime now, Language language) {
        List<DailyChallenge> open = dailyChallengeRepository
            .findByDeletedAtIsNullAndDeadlineAfterOrderByDeadlineAsc(now);
        if (open.isEmpty()) {
            return null;
        }
        DailyChallenge challenge = open.get(0);
        return new MobileHomeResponse.ChallengePreview(
            challenge.getId(),
            LocalizedTextSelector.select(challenge.getTitleEn(), challenge.getTitleKm(), language),
            LocalizedTextSelector.select(challenge.getDescriptionEn(), challenge.getDescriptionKm(), language),
            challenge.getXpReward(),
            challenge.getDeadline()
        );
    }

    private MobileHomeResponse.FinanceSnapshot financeSnapshot(UUID userId, LocalDate today) {
        Budget budget = budgetRepository
            .findByUserIdAndMonthAndYearAndCurrencyAndDeletedAtIsNull(
                userId, today.getMonthValue(), today.getYear(), Currency.USD)
            .orElse(null);
        List<SavingGoal> goals = savingGoalRepository
            .findByUserIdAndDeletedAtIsNullAndStatusOrderByCreatedAtDesc(userId, SavingGoalStatus.IN_PROGRESS);

        // Nothing to show at all - let the client render the "set a budget" empty state.
        if (budget == null && goals.isEmpty()) {
            return null;
        }

        BigDecimal savingTarget = goals.stream()
            .map(SavingGoal::getTargetAmount).filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal savingCurrent = goals.stream()
            .map(SavingGoal::getCurrentAmount).filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Spend is computed live from expenses, NOT the stale budget.spentAmount counter (which
        // nothing maintains, so it always read 0). Mirrors the finance-dashboard BFF.
        BigDecimal total = budget == null ? null : budget.getTotalBudget();
        BigDecimal spent = null;
        BigDecimal remaining = null;
        String status = null;
        if (budget != null) {
            LocalDate monthStart = LocalDate.of(today.getYear(), today.getMonthValue(), 1);
            // Live spend for the budget's own currency (a USD budget never counts KHR expenses).
            BigDecimal live = expenseRepository.sumForMonthByCurrency(
                userId, budget.getCurrency(), monthStart, monthStart.plusMonths(1));
            spent = live == null ? BigDecimal.ZERO : live;
            remaining = total.subtract(spent);
            status = budgetStatus(spent, total);
        }

        return new MobileHomeResponse.FinanceSnapshot(total, spent, remaining, status, savingTarget, savingCurrent);
    }

    /** SAFE / WARNING (>=80%) / LIMIT_REACHED (>=100%) from live spend, same thresholds as the budget module. */
    private String budgetStatus(BigDecimal spent, BigDecimal total) {
        if (total == null || total.signum() <= 0) {
            return "SAFE";
        }
        BigDecimal ratio = spent.multiply(BigDecimal.valueOf(100))
            .divide(total, 0, java.math.RoundingMode.DOWN);
        if (ratio.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return "LIMIT_REACHED";
        }
        return ratio.compareTo(BigDecimal.valueOf(80)) >= 0 ? "WARNING" : "SAFE";
    }

    private MobileHomeResponse.UpcomingEvent upcomingEvent(UUID userId, LocalDateTime now) {
        List<EventRsvp> next = eventRsvpRepository
            .findByUserIdAndStatusInAndEvent_DeletedAtIsNullAndEvent_DateTimeAfterOrderByEvent_DateTimeAsc(
                userId, ATTENDING, now, PageRequest.of(0, 1));
        if (next.isEmpty()) {
            return null;
        }
        Event event = next.get(0).getEvent();
        return new MobileHomeResponse.UpcomingEvent(
            event.getId(), event.getTitle(), event.getDateTime(),
            event.getLocationName(), event.getCoverImageUrl());
    }

    private List<MobileHomeResponse.CommunitySuggestion> recommendedCommunities(UUID userId) {
        List<UUID> joined = communityMemberRepository.findByUserId(userId).stream()
            .map(member -> member.getCommunity().getId())
            .toList();
        // "NOT IN ()" is invalid SQL, so a user with no memberships needs a placeholder.
        Collection<UUID> excluded = joined.isEmpty() ? List.of(UUID.randomUUID()) : joined;

        return communityRepository
            .findSuggestions(CommunityPrivacy.PUBLIC, excluded, PageRequest.of(0, SUGGESTED_COMMUNITIES))
            .stream()
            .map(c -> new MobileHomeResponse.CommunitySuggestion(
                c.getId(), c.getName(), c.getSlug(), c.getCategory(), c.getMemberCount(), c.getAvatarUrl()))
            .toList();
    }

    /**
     * Rotates by day of year rather than at random, so the card is stable if the
     * screen is refreshed, and there is no scheduler or extra table to maintain.
     */
    private MobileHomeResponse.MotivationCard dailyMotivation(LocalDate today, Language language) {
        List<MotivationContent> contents = motivationContentRepository.findByDeletedAtIsNull();
        if (contents.isEmpty()) {
            return null;
        }
        List<MotivationContent> ordered = new ArrayList<>(contents);
        ordered.sort((a, b) -> a.getId().compareTo(b.getId()));
        MotivationContent pick = ordered.get(today.getDayOfYear() % ordered.size());
        return new MobileHomeResponse.MotivationCard(
            pick.getId(),
            LocalizedTextSelector.select(pick.getTitleEn(), pick.getTitleKm(), language),
            LocalizedTextSelector.select(pick.getContentEn(), pick.getContentKm(), language)
        );
    }

    private List<MobileHomeResponse.FeedPreview> feedPreview() {
        return postRepository.findFeedPreview(Visibility.PUBLIC, PageRequest.of(0, FEED_PREVIEW_POSTS))
            .stream()
            .map(this::toFeedPreview)
            .toList();
    }

    private MobileHomeResponse.FeedPreview toFeedPreview(Post post) {
        User author = post.getUser();
        return new MobileHomeResponse.FeedPreview(
            post.getId(),
            author == null ? null : author.getFullName(),
            author == null ? null : author.getAvatarUrl(),
            post.getContentText(),
            post.getImageUrl(),
            post.getLikeCount(),
            post.getCreatedAt()
        );
    }
}

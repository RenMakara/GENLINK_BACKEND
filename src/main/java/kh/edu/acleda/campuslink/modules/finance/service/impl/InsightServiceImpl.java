package kh.edu.acleda.campuslink.modules.finance.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.response.FinanceInsightsResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.FinanceInsightsResponse.Insight;
import kh.edu.acleda.campuslink.modules.finance.dto.response.FinanceInsightsResponse.SuggestedLesson;
import kh.edu.acleda.campuslink.modules.finance.entity.FinancialLesson;
import kh.edu.acleda.campuslink.modules.finance.entity.SavingGoal;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.LiteracyTopic;
import kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.FinancialLessonRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.IncomeRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.SavingGoalRepository;
import kh.edu.acleda.campuslink.modules.finance.service.InsightService;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule-based insights over the finance data. Each rule is a plain comparison against this
 * month vs last month (and the saving goals); there is no ML here, just thresholds a student
 * can reason about. Every rule is currency-scoped and best-effort matched to a lesson.
 */
@Service
public class InsightServiceImpl implements InsightService {

    private static final String UNCATEGORIZED = "UNCATEGORIZED";

    // Savings rate bands (percent of income kept).
    private static final BigDecimal LOW_SAVINGS_RATE = BigDecimal.valueOf(10);
    private static final BigDecimal HEALTHY_SAVINGS_RATE = BigDecimal.valueOf(20);

    // Month-over-month total spend is "up" past +20%.
    private static final BigDecimal TREND_UP_FACTOR = new BigDecimal("1.20");
    // A single category "spikes" past +50% vs last month...
    private static final BigDecimal CATEGORY_SPIKE_FACTOR = new BigDecimal("1.50");
    // ...but only if it is a meaningful slice of the month (ignore tiny line items).
    private static final BigDecimal CATEGORY_MIN_SHARE = new BigDecimal("0.15");

    private static final int MAX_INSIGHTS = 5;

    private static final String SEV_CRITICAL = "CRITICAL";
    private static final String SEV_WARNING = "WARNING";
    private static final String SEV_GOOD = "GOOD";

    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final SavingGoalRepository savingGoalRepository;
    private final FinancialLessonRepository lessonRepository;
    private final CurrentUserProvider currentUserProvider;

    public InsightServiceImpl(IncomeRepository incomeRepository,
                              ExpenseRepository expenseRepository,
                              SavingGoalRepository savingGoalRepository,
                              FinancialLessonRepository lessonRepository,
                              CurrentUserProvider currentUserProvider) {
        this.incomeRepository = incomeRepository;
        this.expenseRepository = expenseRepository;
        this.savingGoalRepository = savingGoalRepository;
        this.lessonRepository = lessonRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public FinanceInsightsResponse getInsights(Currency currency, int month, int year) {
        validateMonth(month);
        UUID userId = currentUserProvider.getCurrentUserId();

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1);
        LocalDate prevStart = start.minusMonths(1);

        BigDecimal income = orZero(incomeRepository.sumForMonthByCurrency(userId, currency, start, end));
        BigDecimal expense = orZero(expenseRepository.sumForMonthByCurrency(userId, currency, start, end));
        BigDecimal prevExpense = orZero(expenseRepository.sumForMonthByCurrency(userId, currency, prevStart, start));
        BigDecimal net = income.subtract(expense);
        BigDecimal savingsRate = income.signum() == 0
            ? BigDecimal.ZERO
            : net.multiply(BigDecimal.valueOf(100)).divide(income, 1, RoundingMode.HALF_UP);

        List<Insight> insights = new ArrayList<>();
        cashFlowInsight(currency, income, expense, net, savingsRate).ifPresent(insights::add);
        spendingTrendInsight(currency, expense, prevExpense).ifPresent(insights::add);
        categorySpikeInsight(currency,
            categoryTotals(expenseRepository.sumByCategoryAndCurrency(userId, currency, start, end)),
            categoryTotals(expenseRepository.sumByCategoryAndCurrency(userId, currency, prevStart, start)),
            expense).ifPresent(insights::add);
        goalPaceInsight(currency, userId, start, net).ifPresent(insights::add);

        // Most urgent first; stable so rule order breaks ties predictably.
        insights.sort(Comparator.comparingInt(i -> severityRank(i.severity())));
        if (insights.size() > MAX_INSIGHTS) {
            insights = insights.subList(0, MAX_INSIGHTS);
        }

        return new FinanceInsightsResponse(currency, month, year, income, expense, net, savingsRate, insights);
    }

    // ---- rules -------------------------------------------------------------

    /** One insight about the month's overall balance: overspent, thin savings, or healthy. */
    private Optional<Insight> cashFlowInsight(Currency currency, BigDecimal income,
                                                        BigDecimal expense, BigDecimal net,
                                                        BigDecimal savingsRate) {
        if (net.signum() < 0) {
            BigDecimal over = net.abs();
            return Optional.of(new Insight("NEGATIVE_CASH_FLOW", SEV_CRITICAL,
                "You spent more than you earned",
                "You spent " + money(currency, expense) + " but earned " + money(currency, income)
                    + " - that is " + money(currency, over) + " over. Build a simple budget to turn this around.",
                null, over, lessonForTopic(LiteracyTopic.BUDGETING)));
        }
        if (income.signum() == 0) {
            return Optional.empty(); // nothing earned and nothing overspent - stay quiet
        }
        if (savingsRate.compareTo(LOW_SAVINGS_RATE) < 0) {
            return Optional.of(new Insight("LOW_SAVINGS_RATE", SEV_WARNING,
                "Your savings rate is low",
                "You kept " + savingsRate.stripTrailingZeros().toPlainString() + "% of your income this month. "
                    + "Aiming for 10-20% builds a cushion faster.",
                null, savingsRate, lessonForTopic(LiteracyTopic.SAVING_MONEY)));
        }
        if (savingsRate.compareTo(HEALTHY_SAVINGS_RATE) >= 0) {
            return Optional.of(new Insight("HEALTHY_SAVINGS", SEV_GOOD,
                "Great savings this month",
                "You kept " + savingsRate.stripTrailingZeros().toPlainString() + "% of your income. "
                    + "Once you have an emergency fund, learn how saved money can grow.",
                null, savingsRate, lessonForTopic(LiteracyTopic.INVESTING_BASICS)));
        }
        return Optional.empty();
    }

    /** Total spend rose meaningfully vs last month. */
    private Optional<Insight> spendingTrendInsight(Currency currency, BigDecimal expense,
                                                             BigDecimal prevExpense) {
        if (prevExpense.signum() <= 0
            || expense.compareTo(prevExpense.multiply(TREND_UP_FACTOR)) <= 0) {
            return Optional.empty();
        }
        int pct = percentIncrease(prevExpense, expense);
        return Optional.of(new Insight("SPENDING_TREND_UP", SEV_WARNING,
            "Spending is trending up",
            "You spent " + money(currency, expense) + " this month, " + pct + "% more than last month's "
                + money(currency, prevExpense) + ".",
            null, expense, lessonForTopic(LiteracyTopic.SMART_SPENDING)));
    }

    /** The single biggest category that jumped vs last month (and is a real slice of the month). */
    private Optional<Insight> categorySpikeInsight(Currency currency,
                                                             Map<String, BigDecimal> thisMonth,
                                                             Map<String, BigDecimal> lastMonth,
                                                             BigDecimal totalThisMonth) {
        if (totalThisMonth.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal shareFloor = totalThisMonth.multiply(CATEGORY_MIN_SHARE);
        String spikeCategory = null;
        BigDecimal spikeAmount = null;
        BigDecimal spikePrev = null;
        BigDecimal biggestJump = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : thisMonth.entrySet()) {
            BigDecimal now = e.getValue();
            if (now.compareTo(shareFloor) < 0) {
                continue; // too small a slice to nag about
            }
            BigDecimal prev = lastMonth.getOrDefault(e.getKey(), BigDecimal.ZERO);
            boolean spiked = prev.signum() > 0
                ? now.compareTo(prev.multiply(CATEGORY_SPIKE_FACTOR)) > 0
                : now.compareTo(shareFloor) >= 0; // brand-new large category also counts
            if (!spiked) {
                continue;
            }
            BigDecimal jump = now.subtract(prev);
            if (jump.compareTo(biggestJump) > 0) {
                biggestJump = jump;
                spikeCategory = e.getKey();
                spikeAmount = now;
                spikePrev = prev;
            }
        }
        if (spikeCategory == null) {
            return Optional.empty();
        }
        String message = spikePrev.signum() > 0
            ? "You spent " + money(currency, spikeAmount) + " on " + spikeCategory + " this month, up from "
                + money(currency, spikePrev) + " - a " + percentIncrease(spikePrev, spikeAmount) + "% jump."
            : "You spent " + money(currency, spikeAmount) + " on " + spikeCategory
                + " this month, a new top category.";
        return Optional.of(new Insight("CATEGORY_SPIKE", SEV_WARNING,
            "Spending spike: " + spikeCategory, message, spikeCategory, spikeAmount,
            lessonForCategory(spikeCategory)));
    }

    /** The active goal most behind the pace it needs to hit its target date. */
    private Optional<Insight> goalPaceInsight(Currency currency, UUID userId,
                                                        LocalDate monthStart, BigDecimal net) {
        List<SavingGoal> goals = savingGoalRepository
            .findByUserIdAndDeletedAtIsNullAndStatusOrderByCreatedAtDesc(userId, SavingGoalStatus.IN_PROGRESS);

        SavingGoal worst = null;
        BigDecimal worstShortfall = BigDecimal.ZERO;
        BigDecimal worstRequired = null;
        for (SavingGoal goal : goals) {
            if (goal.getCurrency() != currency || goal.getTargetDate() == null) {
                continue;
            }
            BigDecimal remaining = orZero(goal.getTargetAmount()).subtract(orZero(goal.getCurrentAmount()));
            if (remaining.signum() <= 0) {
                continue; // already funded
            }
            long monthsLeft = ChronoUnit.MONTHS.between(monthStart, goal.getTargetDate().withDayOfMonth(1));
            // Overdue or due this month => the whole remainder is needed now.
            BigDecimal required = monthsLeft <= 0
                ? remaining
                : remaining.divide(BigDecimal.valueOf(monthsLeft), 2, RoundingMode.HALF_UP);
            if (net.compareTo(required) >= 0) {
                continue; // on pace at this month's savings
            }
            BigDecimal shortfall = required.subtract(net.max(BigDecimal.ZERO));
            if (shortfall.compareTo(worstShortfall) > 0) {
                worstShortfall = shortfall;
                worst = goal;
                worstRequired = required;
            }
        }
        if (worst == null) {
            return Optional.empty();
        }
        return Optional.of(new Insight("GOAL_BEHIND", SEV_WARNING,
            "Saving goal is behind pace",
            "To reach \"" + worst.getTitle() + "\" on time you need about " + money(currency, worstRequired)
                + "/month, but this month you saved " + money(currency, net.max(BigDecimal.ZERO)) + ".",
            null, worstRequired, lessonForTopic(LiteracyTopic.SAVING_MONEY)));
    }

    // ---- lesson matching ---------------------------------------------------

    private SuggestedLesson lessonForCategory(String category) {
        FinancialLesson lesson = category == null ? null
            : lessonRepository.findFirstByRelatedCategoryIgnoreCaseAndDeletedAtIsNull(category).orElse(null);
        if (lesson == null) {
            return lessonForTopic(LiteracyTopic.SMART_SPENDING);
        }
        return toSuggested(lesson);
    }

    private SuggestedLesson lessonForTopic(LiteracyTopic topic) {
        return lessonRepository.findFirstByTopicAndDeletedAtIsNullOrderByCreatedAtAsc(topic.name())
            .map(this::toSuggested)
            .orElse(null);
    }

    private SuggestedLesson toSuggested(FinancialLesson lesson) {
        // English title only - the insight text is English too; the full lesson (incl. Khmer)
        // is one call away via the literacy API by lessonId.
        return new SuggestedLesson(lesson.getId(), lesson.getTitleEn(), lesson.getTopic());
    }

    // ---- helpers -----------------------------------------------------------

    private Map<String, BigDecimal> categoryTotals(List<Object[]> rows) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String category = row[0] == null ? UNCATEGORIZED : (String) row[0];
            totals.merge(category, (BigDecimal) row[1], BigDecimal::add);
        }
        return totals;
    }

    private int percentIncrease(BigDecimal from, BigDecimal to) {
        if (from.signum() <= 0) {
            return 0;
        }
        return to.subtract(from).multiply(BigDecimal.valueOf(100))
            .divide(from, 0, RoundingMode.HALF_UP).intValue();
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case SEV_CRITICAL -> 0;
            case SEV_WARNING -> 1;
            case SEV_GOOD -> 2;
            default -> 3;
        };
    }

    private String money(Currency currency, BigDecimal amount) {
        return currency.name() + " " + orZero(amount).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void validateMonth(int month) {
        if (month < 1 || month > 12) {
            throw new ApiException("Month must be between 1 and 12", "VALIDATION_ERROR");
        }
    }
}

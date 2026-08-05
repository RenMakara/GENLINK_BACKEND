package kh.edu.acleda.campuslink.modules.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.response.FinanceInsightsResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.FinanceInsightsResponse.Insight;
import kh.edu.acleda.campuslink.modules.finance.entity.FinancialLesson;
import kh.edu.acleda.campuslink.modules.finance.entity.SavingGoal;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.FinancialLessonRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.IncomeRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.SavingGoalRepository;
import kh.edu.acleda.campuslink.modules.finance.service.impl.InsightServiceImpl;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InsightServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final int MONTH = 7;
    private static final int YEAR = 2026;
    private static final LocalDate CUR_START = LocalDate.of(2026, 7, 1);
    private static final LocalDate CUR_END = LocalDate.of(2026, 8, 1);
    private static final LocalDate PREV_START = LocalDate.of(2026, 6, 1);

    @Mock private IncomeRepository incomeRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private SavingGoalRepository savingGoalRepository;
    @Mock private FinancialLessonRepository lessonRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private InsightServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InsightServiceImpl(incomeRepository, expenseRepository, savingGoalRepository,
            lessonRepository, currentUserProvider);
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
    }

    // ---- stub helpers ----

    private void income(String amount) {
        when(incomeRepository.sumForMonthByCurrency(eq(USER_ID), eq(Currency.USD), eq(CUR_START), eq(CUR_END)))
            .thenReturn(amount == null ? null : new BigDecimal(amount));
    }

    private void expenseThisMonth(String amount) {
        when(expenseRepository.sumForMonthByCurrency(eq(USER_ID), eq(Currency.USD), eq(CUR_START), eq(CUR_END)))
            .thenReturn(amount == null ? null : new BigDecimal(amount));
    }

    private void expenseLastMonth(String amount) {
        when(expenseRepository.sumForMonthByCurrency(eq(USER_ID), eq(Currency.USD), eq(PREV_START), eq(CUR_START)))
            .thenReturn(amount == null ? null : new BigDecimal(amount));
    }

    private void categoriesThisMonth(List<Object[]> rows) {
        when(expenseRepository.sumByCategoryAndCurrency(eq(USER_ID), eq(Currency.USD), eq(CUR_START), eq(CUR_END)))
            .thenReturn(rows);
    }

    private void categoriesLastMonth(List<Object[]> rows) {
        when(expenseRepository.sumByCategoryAndCurrency(eq(USER_ID), eq(Currency.USD), eq(PREV_START), eq(CUR_START)))
            .thenReturn(rows);
    }

    private static Object[] row(String category, String amount) {
        return new Object[]{category, new BigDecimal(amount)};
    }

    private void topicLesson(String topic, String title) {
        FinancialLesson lesson = FinancialLesson.builder().titleEn(title).topic(topic).xpReward(20).build();
        ReflectionTestUtils.setField(lesson, "id", UUID.randomUUID());
        when(lessonRepository.findFirstByTopicAndDeletedAtIsNullOrderByCreatedAtAsc(topic))
            .thenReturn(Optional.of(lesson));
    }

    private Insight find(FinanceInsightsResponse r, String type) {
        return r.insights().stream().filter(i -> i.type().equals(type)).findFirst().orElse(null);
    }

    // ---- tests ----

    @Test
    void overspendingProducesACriticalInsightWithABudgetingLesson() {
        income("50.00");
        expenseThisMonth("80.00");
        topicLesson("BUDGETING", "Build your first budget");

        FinanceInsightsResponse r = service.getInsights(Currency.USD, MONTH, YEAR);

        assertThat(r.net()).isEqualByComparingTo("-30.00");
        Insight overspend = find(r, "NEGATIVE_CASH_FLOW");
        assertThat(overspend).isNotNull();
        assertThat(overspend.severity()).isEqualTo("CRITICAL");
        assertThat(overspend.amount()).isEqualByComparingTo("30.00");
        assertThat(overspend.lesson().topic()).isEqualTo("BUDGETING");
    }

    @Test
    void lowSavingsRateProducesAWarning() {
        income("100.00");
        expenseThisMonth("95.00"); // net 5 -> 5% kept
        topicLesson("SAVING_MONEY", "Why saving early matters");

        FinanceInsightsResponse r = service.getInsights(Currency.USD, MONTH, YEAR);

        assertThat(r.savingsRatePercent()).isEqualByComparingTo("5.0");
        Insight low = find(r, "LOW_SAVINGS_RATE");
        assertThat(low).isNotNull();
        assertThat(low.severity()).isEqualTo("WARNING");
        assertThat(low.lesson().topic()).isEqualTo("SAVING_MONEY");
    }

    @Test
    void healthySavingsRateProducesAGoodInsight() {
        income("100.00");
        expenseThisMonth("70.00"); // 30% kept

        FinanceInsightsResponse r = service.getInsights(Currency.USD, MONTH, YEAR);

        Insight good = find(r, "HEALTHY_SAVINGS");
        assertThat(good).isNotNull();
        assertThat(good.severity()).isEqualTo("GOOD");
    }

    /** A savings rate between the low and healthy bands, nothing else amiss, yields no insight. */
    @Test
    void anOrdinaryMonthProducesNoInsights() {
        income("100.00");
        expenseThisMonth("85.00"); // 15% kept - neither low nor healthy

        FinanceInsightsResponse r = service.getInsights(Currency.USD, MONTH, YEAR);

        assertThat(r.insights()).isEmpty();
    }

    @Test
    void spendingTrendUpIsDetected() {
        income("1000.00"); // keep cash flow healthy so the trend rule is what we assert
        expenseThisMonth("60.00");
        expenseLastMonth("40.00"); // 60 > 40 * 1.2

        FinanceInsightsResponse r = service.getInsights(Currency.USD, MONTH, YEAR);

        Insight trend = find(r, "SPENDING_TREND_UP");
        assertThat(trend).isNotNull();
        assertThat(trend.severity()).isEqualTo("WARNING");
        assertThat(trend.message()).contains("50%");
    }

    @Test
    void categorySpikeMatchesACategorySpecificLesson() {
        income("1000.00");
        expenseThisMonth("60.00");
        expenseLastMonth("30.00");
        categoriesThisMonth(List.<Object[]>of(row("FOOD", "60.00")));   // 60 vs 20 last month
        categoriesLastMonth(List.<Object[]>of(row("FOOD", "20.00")));

        FinancialLesson foodLesson = FinancialLesson.builder()
            .titleEn("Spend smart").topic("SMART_SPENDING").relatedCategory("FOOD").xpReward(20).build();
        ReflectionTestUtils.setField(foodLesson, "id", UUID.randomUUID());
        when(lessonRepository.findFirstByRelatedCategoryIgnoreCaseAndDeletedAtIsNull("FOOD"))
            .thenReturn(Optional.of(foodLesson));

        FinanceInsightsResponse r = service.getInsights(Currency.USD, MONTH, YEAR);

        Insight spike = find(r, "CATEGORY_SPIKE");
        assertThat(spike).isNotNull();
        assertThat(spike.category()).isEqualTo("FOOD");
        assertThat(spike.lesson().title()).isEqualTo("Spend smart");
    }

    @Test
    void categorySpikeFallsBackToATopicLessonWhenNoCategoryMatch() {
        income("1000.00");
        expenseThisMonth("60.00");
        expenseLastMonth("30.00");
        categoriesThisMonth(List.<Object[]>of(row("TRANSPORT", "60.00")));
        categoriesLastMonth(List.<Object[]>of(row("TRANSPORT", "20.00")));
        // No related-category lesson for TRANSPORT; the smart-spending topic is the fallback.
        topicLesson("SMART_SPENDING", "Spend smart");

        FinanceInsightsResponse r = service.getInsights(Currency.USD, MONTH, YEAR);

        Insight spike = find(r, "CATEGORY_SPIKE");
        assertThat(spike).isNotNull();
        assertThat(spike.lesson().topic()).isEqualTo("SMART_SPENDING");
    }

    @Test
    void aGoalBehindPaceProducesAWarning() {
        income("100.00");
        expenseThisMonth("90.00"); // net 10, rate 10% -> no cash-flow insight
        SavingGoal goal = SavingGoal.builder()
            .title("Laptop").currency(Currency.USD)
            .targetAmount(new BigDecimal("1000.00")).currentAmount(new BigDecimal("100.00"))
            .targetDate(LocalDate.of(2026, 10, 1)) // 3 months out -> need 300/month
            .status(SavingGoalStatus.IN_PROGRESS).build();
        when(savingGoalRepository.findByUserIdAndDeletedAtIsNullAndStatusOrderByCreatedAtDesc(
            USER_ID, SavingGoalStatus.IN_PROGRESS)).thenReturn(List.of(goal));
        topicLesson("SAVING_MONEY", "Why saving early matters");

        FinanceInsightsResponse r = service.getInsights(Currency.USD, MONTH, YEAR);

        Insight behind = find(r, "GOAL_BEHIND");
        assertThat(behind).isNotNull();
        assertThat(behind.amount()).isEqualByComparingTo("300.00");
        assertThat(behind.message()).contains("Laptop");
        assertThat(behind.lesson().topic()).isEqualTo("SAVING_MONEY");
    }

    /** A goal already on pace at this month's savings raises nothing. */
    @Test
    void aGoalOnPaceProducesNoGoalInsight() {
        income("1000.00");
        expenseThisMonth("100.00"); // net 900, easily covers 300/month
        SavingGoal goal = SavingGoal.builder()
            .title("Laptop").currency(Currency.USD)
            .targetAmount(new BigDecimal("1000.00")).currentAmount(new BigDecimal("100.00"))
            .targetDate(LocalDate.of(2026, 10, 1))
            .status(SavingGoalStatus.IN_PROGRESS).build();
        when(savingGoalRepository.findByUserIdAndDeletedAtIsNullAndStatusOrderByCreatedAtDesc(
            USER_ID, SavingGoalStatus.IN_PROGRESS)).thenReturn(List.of(goal));

        FinanceInsightsResponse r = service.getInsights(Currency.USD, MONTH, YEAR);

        assertThat(find(r, "GOAL_BEHIND")).isNull();
    }

    /** Critical insights sort ahead of warnings. */
    @Test
    void insightsAreOrderedBySeverity() {
        income("50.00");
        expenseThisMonth("80.00");   // negative cash flow -> CRITICAL
        expenseLastMonth("40.00");   // spending trend up -> WARNING

        FinanceInsightsResponse r = service.getInsights(Currency.USD, MONTH, YEAR);

        assertThat(r.insights()).isNotEmpty();
        assertThat(r.insights().get(0).severity()).isEqualTo("CRITICAL");
    }

    @Test
    void anInvalidMonthIsRejected() {
        assertThatThrownBy(() -> service.getInsights(Currency.USD, 13, YEAR))
            .isInstanceOf(ApiException.class);
    }
}

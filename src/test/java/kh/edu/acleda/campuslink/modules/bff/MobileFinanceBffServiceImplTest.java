package kh.edu.acleda.campuslink.modules.bff;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.bff.service.MobileFinanceBffServiceImpl;
import kh.edu.acleda.campuslink.modules.finance.entity.Budget;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.entity.Expense;
import kh.edu.acleda.campuslink.modules.finance.entity.SavingGoal;
import kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus;
import kh.edu.acleda.campuslink.modules.finance.repository.BudgetRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoWalletRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.SavingGoalRepository;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileFinanceDashboardResponse;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MobileFinanceBffServiceImplTest {

    private static final String EMAIL = "sok@example.com";
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private UserRepository userRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private SavingGoalRepository savingGoalRepository;
    @Mock private DemoWalletRepository demoWalletRepository;

    private MobileFinanceBffServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new MobileFinanceBffServiceImpl(userRepository, budgetRepository, expenseRepository,
            savingGoalRepository, demoWalletRepository);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);

        // Empty-by-default world; each test overrides what it needs.
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(expenseRepository.sumForMonthByCurrency(any(), any(), any(), any())).thenReturn(null);
        when(expenseRepository.sumByCategoryAndCurrency(any(), any(), any(), any())).thenReturn(List.of());
        when(expenseRepository.findByUser(any(), any(), any())).thenReturn(Page.empty());
        when(budgetRepository.findByUserIdAndMonthAndYearAndDeletedAtIsNull(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(savingGoalRepository.findByUserIdAndDeletedAtIsNullAndStatusOrderByCreatedAtDesc(
            any(), eq(SavingGoalStatus.IN_PROGRESS))).thenReturn(List.of());
        when(demoWalletRepository.findByUserId(any())).thenReturn(Optional.empty());
    }

    @Test
    void aBrandNewUserStillGetsAWellFormedBody() {
        MobileFinanceDashboardResponse r = service.getFinanceDashboard(EMAIL);

        assertThat(r.month().spent()).isEqualByComparingTo("0");
        assertThat(r.month().budgetTotal()).isNull();
        assertThat(r.topCategories()).isEmpty();
        assertThat(r.saving().activeGoals()).isZero();
        assertThat(r.saving().progressPercent()).isZero();
        assertThat(r.recentExpenses()).isEmpty();
        assertThat(r.wallet()).isNull();
        assertThat(r.financialTip()).isNotBlank();
        assertThat(r.quickActions()).contains("ADD_EXPENSE", "TOP_UP_WALLET");
    }

    @Test
    void monthOverviewComputesSpendLiveAndFlagsWarning() {
        Budget budget = Budget.builder().totalBudget(new BigDecimal("200.00")).build();
        when(budgetRepository.findByUserIdAndMonthAndYearAndDeletedAtIsNull(any(), any(), any()))
            .thenReturn(Optional.of(budget));
        when(expenseRepository.sumForMonthByCurrency(any(), any(), any(), any())).thenReturn(new BigDecimal("180.00"));

        MobileFinanceDashboardResponse.MonthOverview m = service.getFinanceDashboard(EMAIL).month();

        assertThat(m.spent()).isEqualByComparingTo("180.00");
        assertThat(m.budgetRemaining()).isEqualByComparingTo("20.00");
        assertThat(m.budgetStatus()).isEqualTo("WARNING");
    }

    @Test
    void budgetStatusIsLimitReachedAtOrOverTotal() {
        Budget budget = Budget.builder().totalBudget(new BigDecimal("100.00")).build();
        when(budgetRepository.findByUserIdAndMonthAndYearAndDeletedAtIsNull(any(), any(), any()))
            .thenReturn(Optional.of(budget));
        when(expenseRepository.sumForMonthByCurrency(any(), any(), any(), any())).thenReturn(new BigDecimal("100.00"));

        assertThat(service.getFinanceDashboard(EMAIL).month().budgetStatus()).isEqualTo("LIMIT_REACHED");
    }

    @Test
    void topCategoriesAreCappedAtFiveAndNullBecomesUncategorized() {
        when(expenseRepository.sumByCategoryAndCurrency(any(), any(), any(), any())).thenReturn(List.of(
            new Object[]{"FOOD", new BigDecimal("60")},
            new Object[]{null, new BigDecimal("40")},
            new Object[]{"TRANSPORT", new BigDecimal("30")},
            new Object[]{"FUN", new BigDecimal("20")},
            new Object[]{"BILLS", new BigDecimal("10")},
            new Object[]{"MISC", new BigDecimal("5")}));

        List<MobileFinanceDashboardResponse.CategorySpend> top = service.getFinanceDashboard(EMAIL).topCategories();

        assertThat(top).hasSize(5);
        assertThat(top.get(1).category()).isEqualTo("UNCATEGORIZED");
    }

    @Test
    void savingProgressAggregatesActiveGoals() {
        when(savingGoalRepository.findByUserIdAndDeletedAtIsNullAndStatusOrderByCreatedAtDesc(
            any(), eq(SavingGoalStatus.IN_PROGRESS))).thenReturn(List.of(
                SavingGoal.builder().targetAmount(new BigDecimal("100")).currentAmount(new BigDecimal("30")).build(),
                SavingGoal.builder().targetAmount(new BigDecimal("200")).currentAmount(new BigDecimal("60")).build()));

        MobileFinanceDashboardResponse.SavingProgress s = service.getFinanceDashboard(EMAIL).saving();

        assertThat(s.totalTarget()).isEqualByComparingTo("300");
        assertThat(s.totalSaved()).isEqualByComparingTo("90");
        assertThat(s.progressPercent()).isEqualTo(30);
        assertThat(s.activeGoals()).isEqualTo(2);
    }

    @Test
    void recentExpensesAreMapped() {
        Expense e = Expense.builder().title("Coffee").category("FOOD")
            .amount(new BigDecimal("2.50")).expenseDate(java.time.LocalDate.now()).build();
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        when(expenseRepository.findByUser(any(), any(), any())).thenReturn(new PageImpl<>(List.of(e)));

        assertThat(service.getFinanceDashboard(EMAIL).recentExpenses()).singleElement()
            .satisfies(re -> {
                assertThat(re.title()).isEqualTo("Coffee");
                assertThat(re.amount()).isEqualByComparingTo("2.50");
            });
    }

    @Test
    void walletSummaryIsPresentWhenTheWalletExists() {
        DemoWallet wallet = DemoWallet.builder()
            .balanceUsd(new BigDecimal("45.00"))
            .balanceKhr(new BigDecimal("120000.00"))
            .build();
        when(demoWalletRepository.findByUserId(any())).thenReturn(Optional.of(wallet));

        MobileFinanceDashboardResponse.WalletSummary w = service.getFinanceDashboard(EMAIL).wallet();

        assertThat(w).isNotNull();
        assertThat(w.balanceUsd()).isEqualByComparingTo("45.00");
        assertThat(w.balanceKhr()).isEqualByComparingTo("120000.00");
    }

    @Test
    void unknownUserThrowsNotFound() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getFinanceDashboard(EMAIL)).isInstanceOf(NotFoundException.class);
    }
}

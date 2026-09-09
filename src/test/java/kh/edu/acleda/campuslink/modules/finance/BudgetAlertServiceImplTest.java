package kh.edu.acleda.campuslink.modules.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.Budget;
import kh.edu.acleda.campuslink.modules.finance.entity.Expense;
import kh.edu.acleda.campuslink.modules.finance.enums.BudgetStatus;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.repository.BudgetRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.finance.service.impl.BudgetAlertServiceImpl;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetAlertServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID BUDGET_ID = UUID.randomUUID();
    private static final UUID EXPENSE_ID = UUID.randomUUID();
    private static final LocalDate EXPENSE_DATE = LocalDate.of(2026, 9, 15);

    @Mock private BudgetRepository budgetRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private NotificationEmitter notificationEmitter;

    private BudgetAlertServiceImpl service;
    private Budget budget;
    private Expense expense;

    @BeforeEach
    void setUp() {
        service = new BudgetAlertServiceImpl(budgetRepository, expenseRepository, notificationEmitter);

        User user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        budget = Budget.builder().user(user).month(9).year(2026)
            .totalBudget(new BigDecimal("300.00")).currency(Currency.USD).build();
        ReflectionTestUtils.setField(budget, "id", BUDGET_ID);

        expense = Expense.builder().user(user).title("Coffee").amount(new BigDecimal("50.00"))
            .currency(Currency.USD).expenseDate(EXPENSE_DATE).build();
        ReflectionTestUtils.setField(expense, "id", EXPENSE_ID);

        when(budgetRepository.findByUserIdAndMonthAndYearAndCurrencyAndDeletedAtIsNull(
            USER_ID, 9, 2026, Currency.USD)).thenReturn(Optional.of(budget));
    }

    @Test
    void emitsApproachingAlertWhenSpendCrossesEightyPercent() {
        when(expenseRepository.sumForMonthByCurrency(
            USER_ID, Currency.USD, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)))
            .thenReturn(new BigDecimal("200.00"), new BigDecimal("250.00"));

        BudgetStatus before = service.statusBeforeExpenseMutation(USER_ID, EXPENSE_DATE, Currency.USD);
        service.evaluateAfterExpenseMutation(expense, before);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(before).isEqualTo(BudgetStatus.SAFE);
        assertThat(request.recipientUserId()).isEqualTo(USER_ID);
        assertThat(request.templateKey()).isEqualTo("FINANCE_BUDGET_APPROACHING");
        assertThat(request.type()).isEqualTo("FINANCE_BUDGET");
        assertThat(request.parameters()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "budgetName", "September 2026 budget (USD)", "percent", "83.33"));
        assertThat(request.referenceType()).isEqualTo("BUDGET");
        assertThat(request.referenceId()).isEqualTo(BUDGET_ID);
        assertThat(request.dedupeKey()).isEqualTo("budget-approaching:" + BUDGET_ID);
    }

    @Test
    void emitsExceededAlertWhenSpendCrossesTheBudgetLimit() {
        when(expenseRepository.sumForMonthByCurrency(
            USER_ID, Currency.USD, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)))
            .thenReturn(new BigDecimal("240.00"), new BigDecimal("300.00"));

        BudgetStatus before = service.statusBeforeExpenseMutation(USER_ID, EXPENSE_DATE, Currency.USD);
        service.evaluateAfterExpenseMutation(expense, before);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(before).isEqualTo(BudgetStatus.WARNING);
        assertThat(request.templateKey()).isEqualTo("FINANCE_BUDGET_EXCEEDED");
        assertThat(request.parameters()).containsEntry("budgetName", "September 2026 budget (USD)");
        assertThat(request.dedupeKey()).isEqualTo("budget-exceeded:" + BUDGET_ID);
    }

    @Test
    void doesNotRepeatAlertWhileSpendRemainsInTheSameThreshold() {
        when(expenseRepository.sumForMonthByCurrency(
            USER_ID, Currency.USD, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)))
            .thenReturn(new BigDecimal("240.00"), new BigDecimal("280.00"));

        BudgetStatus before = service.statusBeforeExpenseMutation(USER_ID, EXPENSE_DATE, Currency.USD);
        service.evaluateAfterExpenseMutation(expense, before);

        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }

    @Test
    void evaluatesTheMatchingKhrBudgetWithoutMixingUsdSpend() {
        Budget khrBudget = Budget.builder().month(9).year(2026)
            .totalBudget(new BigDecimal("1000000.00")).currency(Currency.KHR).build();
        ReflectionTestUtils.setField(khrBudget, "id", BUDGET_ID);
        User user = expense.getUser();
        khrBudget.setUser(user);
        Expense khrExpense = Expense.builder().user(user).title("Transport")
            .amount(new BigDecimal("150000.00")).currency(Currency.KHR).expenseDate(EXPENSE_DATE).build();
        ReflectionTestUtils.setField(khrExpense, "id", EXPENSE_ID);

        when(budgetRepository.findByUserIdAndMonthAndYearAndCurrencyAndDeletedAtIsNull(
            USER_ID, 9, 2026, Currency.KHR)).thenReturn(Optional.of(khrBudget));
        when(expenseRepository.sumForMonthByCurrency(
            USER_ID, Currency.KHR, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)))
            .thenReturn(new BigDecimal("700000.00"), new BigDecimal("850000.00"));

        BudgetStatus before = service.statusBeforeExpenseMutation(USER_ID, EXPENSE_DATE, Currency.KHR);
        service.evaluateAfterExpenseMutation(khrExpense, before);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(before).isEqualTo(BudgetStatus.SAFE);
        assertThat(request.parameters()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "budgetName", "September 2026 budget (KHR)", "percent", "85"));
        assertThat(request.referenceId()).isEqualTo(BUDGET_ID);
    }
}

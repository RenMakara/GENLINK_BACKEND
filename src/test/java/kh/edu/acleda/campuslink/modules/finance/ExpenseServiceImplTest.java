package kh.edu.acleda.campuslink.modules.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.ExpenseCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.MonthlySummaryResponse;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.entity.Expense;
import kh.edu.acleda.campuslink.modules.finance.enums.BudgetStatus;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.finance.service.BudgetAlertService;
import kh.edu.acleda.campuslink.modules.finance.service.DemoWalletService;
import kh.edu.acleda.campuslink.modules.finance.service.LedgerService;
import kh.edu.acleda.campuslink.modules.finance.service.impl.ExpenseServiceImpl;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpenseServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EXPENSE_ID = UUID.randomUUID();

    @Mock private ExpenseRepository expenseRepository;
    @Mock private DemoWalletService demoWalletService;
    @Mock private LedgerService ledgerService;
    @Mock private BudgetAlertService budgetAlertService;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private ExpenseServiceImpl service;
    private User user;
    private Expense expense;

    @BeforeEach
    void setUp() {
        service = new ExpenseServiceImpl(expenseRepository, demoWalletService, ledgerService,
            budgetAlertService, userRepository, currentUserProvider);
        user = new User();
        user.setEmail("user@example.com");
        ReflectionTestUtils.setField(user, "id", USER_ID);

        expense = Expense.builder().user(user).title("Coffee").amount(new BigDecimal("2.50"))
            .currency(Currency.USD).category("FOOD").expenseDate(LocalDate.now()).build();
        ReflectionTestUtils.setField(expense, "id", EXPENSE_ID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(i -> {
            Expense e = i.getArgument(0);
            if (e.getId() == null) {
                ReflectionTestUtils.setField(e, "id", EXPENSE_ID);
            }
            return e;
        });
    }

    @Test
    void createPersistsTheExpense() {
        var response = service.create(new ExpenseCreateRequest(
            "Coffee", new BigDecimal("2.50"), "FOOD", "Morning", LocalDate.now(), "CASH", null, null));

        verify(expenseRepository).save(any(Expense.class));
        assertThat(response.title()).isEqualTo("Coffee");
        assertThat(response.amount()).isEqualByComparingTo("2.50");
        // Default (not paid from wallet) - the balance is never touched.
        verify(ledgerService, never()).debit(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void payingFromWalletDebitsTheLedger() {
        DemoWallet wallet = DemoWallet.builder().user(user)
            .balanceUsd(new BigDecimal("50.00"))
            .balanceKhr(new BigDecimal("0.00"))
            .build();
        when(demoWalletService.getOrCreateWalletEntity()).thenReturn(wallet);

        service.create(new ExpenseCreateRequest(
            "Lunch", new BigDecimal("4.00"), "FOOD", null, LocalDate.now(), "WALLET", true, null));

        verify(ledgerService).debit(eq(wallet), eq(TransactionType.EXPENSE), eq(Currency.USD),
            eq(new BigDecimal("4.00")), any(), eq("EXPENSE"), any());
    }

    @Test
    void createEvaluatesBudgetAfterTheExpenseSucceeds() {
        LocalDate expenseDate = LocalDate.of(2026, 9, 15);
        when(budgetAlertService.statusBeforeExpenseMutation(USER_ID, expenseDate, Currency.USD))
            .thenReturn(BudgetStatus.SAFE);

        service.create(new ExpenseCreateRequest(
            "Coffee", new BigDecimal("2.50"), "FOOD", null, expenseDate, "CASH", null, null));

        verify(budgetAlertService).statusBeforeExpenseMutation(USER_ID, expenseDate, Currency.USD);
        verify(budgetAlertService).evaluateAfterExpenseMutation(any(Expense.class), eq(BudgetStatus.SAFE));
    }

    /** month + year together route to the date-range query. */
    @Test
    void listWithMonthAndYearUsesTheDateRangeQuery() {
        when(expenseRepository.findByUserAndDateRange(eq(USER_ID), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(expense)));

        service.getMine(null, 3, 2026, Currency.KHR, PageRequest.of(0, 20));

        verify(expenseRepository).findByUserAndDateRange(eq(USER_ID), isNull(), eq(Currency.KHR),
            eq(LocalDate.of(2026, 3, 1)), eq(LocalDate.of(2026, 4, 1)), any());
        verify(expenseRepository, never()).findByUser(any(), any(), any(), any());
    }

    /** month without year cannot bound a range, so it falls back to the plain list. */
    @Test
    void listWithMonthButNoYearFallsBackToPlainList() {
        when(expenseRepository.findByUser(eq(USER_ID), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(expense)));

        service.getMine("FOOD", 3, null, PageRequest.of(0, 20));

        verify(expenseRepository).findByUser(eq(USER_ID), eq("FOOD"), isNull(), any());
        verify(expenseRepository, never()).findByUserAndDateRange(any(), any(), any(), any(), any(), any());
    }

    @Test
    void summaryMapsCategoriesAndReportsNullAsUncategorized() {
        when(expenseRepository.sumByCategoryAndCurrency(eq(USER_ID), eq(Currency.USD), any(), any())).thenReturn(List.of(
            new Object[]{"FOOD", new BigDecimal("10.00")},
            new Object[]{null, new BigDecimal("5.00")}));
        when(expenseRepository.sumForMonthByCurrency(eq(USER_ID), eq(Currency.USD), any(), any()))
            .thenReturn(new BigDecimal("15.00"));

        MonthlySummaryResponse summary = service.monthlySummary(3, 2026, Currency.USD);

        assertThat(summary.total()).isEqualByComparingTo("15.00");
        assertThat(summary.currency()).isEqualTo(Currency.USD);
        assertThat(summary.byCategory()).hasSize(2);
        assertThat(summary.byCategory().get(1).category()).isEqualTo("UNCATEGORIZED");
    }

    /** A month with no expenses returns zero, not null. */
    @Test
    void summaryReturnsZeroWhenThereAreNoExpenses() {
        when(expenseRepository.sumByCategoryAndCurrency(any(), any(), any(), any())).thenReturn(List.of());
        when(expenseRepository.sumForMonthByCurrency(any(), any(), any(), any())).thenReturn(null);

        assertThat(service.monthlySummary(3, 2026).total()).isEqualByComparingTo("0");
    }

    @Test
    void summaryQueriesOnlyTheRequestedCurrency() {
        when(expenseRepository.sumByCategoryAndCurrency(USER_ID, Currency.KHR,
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1))).thenReturn(List.of());
        when(expenseRepository.sumForMonthByCurrency(USER_ID, Currency.KHR,
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1))).thenReturn(new BigDecimal("40000"));

        MonthlySummaryResponse summary = service.monthlySummary(3, 2026, Currency.KHR);

        assertThat(summary.currency()).isEqualTo(Currency.KHR);
        assertThat(summary.total()).isEqualByComparingTo("40000");
    }

    @Test
    void summaryRejectsAnInvalidMonth() {
        assertThatThrownBy(() -> service.monthlySummary(13, 2026))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("between 1 and 12");
    }

    @Test
    void deleteIsASoftDelete() {
        expense.setPaidFromWallet(true);
        DemoWallet wallet = wallet();
        when(expenseRepository.findByIdAndUserIdForUpdate(EXPENSE_ID, USER_ID))
            .thenReturn(Optional.of(expense));
        when(demoWalletService.getOrCreateWalletEntity()).thenReturn(wallet);

        service.delete(EXPENSE_ID);

        assertThat(expense.getDeletedAt()).isNotNull();
        verify(ledgerService).credit(wallet, TransactionType.EXPENSE, Currency.USD,
            new BigDecimal("2.50"), "Expense deleted (refund): Coffee", "EXPENSE", EXPENSE_ID);
    }

    @Test
    void reducingWalletPaidExpenseRefundsTheDifference() {
        expense.setPaidFromWallet(true);
        expense.setAmount(new BigDecimal("50.00"));
        DemoWallet wallet = wallet();
        when(expenseRepository.findByIdAndUserIdForUpdate(EXPENSE_ID, USER_ID))
            .thenReturn(Optional.of(expense));
        when(demoWalletService.getOrCreateWalletEntity()).thenReturn(wallet);

        service.update(EXPENSE_ID, new ExpenseCreateRequest(
            "Coffee", new BigDecimal("20.00"), "FOOD", null, LocalDate.now(),
            "WALLET", true, Currency.USD));

        verify(ledgerService).credit(wallet, TransactionType.EXPENSE, Currency.USD,
            new BigDecimal("30.00"), "Expense reduced (refund): Coffee", "EXPENSE", EXPENSE_ID);
        assertThat(expense.getAmount()).isEqualByComparingTo("20.00");
        assertThat(expense.isPaidFromWallet()).isTrue();
    }

    @Test
    void changingExpenseCurrencyRefundsOldAndDebitsNew() {
        expense.setPaidFromWallet(true);
        DemoWallet wallet = wallet();
        when(expenseRepository.findByIdAndUserIdForUpdate(EXPENSE_ID, USER_ID))
            .thenReturn(Optional.of(expense));
        when(demoWalletService.getOrCreateWalletEntity()).thenReturn(wallet);

        service.update(EXPENSE_ID, new ExpenseCreateRequest(
            "Coffee", new BigDecimal("10000"), "FOOD", null, LocalDate.now(),
            "WALLET", true, Currency.KHR));

        verify(ledgerService).credit(wallet, TransactionType.EXPENSE, Currency.USD,
            new BigDecimal("2.50"), "Expense currency changed (refund): Coffee", "EXPENSE", EXPENSE_ID);
        verify(ledgerService).debit(wallet, TransactionType.EXPENSE, Currency.KHR,
            new BigDecimal("10000"), "Expense currency changed: Coffee", "EXPENSE", EXPENSE_ID);
        assertThat(expense.getCurrency()).isEqualTo(Currency.KHR);
    }

    @Test
    void updatingSomeoneElsesExpenseReadsAsNotFound() {
        when(expenseRepository.findByIdAndUserIdForUpdate(EXPENSE_ID, USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(EXPENSE_ID, new ExpenseCreateRequest(
            "Hack", new BigDecimal("1.00"), null, null, LocalDate.now(), null, null, null)))
            .isInstanceOf(NotFoundException.class);
    }

    private DemoWallet wallet() {
        return DemoWallet.builder().user(user)
            .balanceUsd(new BigDecimal("500.00"))
            .balanceKhr(new BigDecimal("2000000.00"))
            .build();
    }
}

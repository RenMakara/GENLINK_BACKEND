package kh.edu.acleda.campuslink.modules.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.RecurringTransactionCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.entity.Expense;
import kh.edu.acleda.campuslink.modules.finance.entity.Income;
import kh.edu.acleda.campuslink.modules.finance.entity.RecurringTransaction;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.RecurringFrequency;
import kh.edu.acleda.campuslink.modules.finance.enums.RecurringType;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.IncomeRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.RecurringTransactionRepository;
import kh.edu.acleda.campuslink.modules.finance.service.DemoWalletService;
import kh.edu.acleda.campuslink.modules.finance.service.LedgerService;
import kh.edu.acleda.campuslink.modules.finance.service.impl.RecurringTransactionServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecurringTransactionServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private RecurringTransactionRepository recurringRepository;
    @Mock private IncomeRepository incomeRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private DemoWalletService demoWalletService;
    @Mock private LedgerService ledgerService;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private RecurringTransactionServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new RecurringTransactionServiceImpl(recurringRepository, incomeRepository, expenseRepository,
            demoWalletService, ledgerService, userRepository, currentUserProvider);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(recurringRepository.save(any(RecurringTransaction.class))).thenAnswer(i -> {
            RecurringTransaction t = i.getArgument(0);
            if (t.getId() == null) {
                ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
            }
            return t;
        });
        when(incomeRepository.save(any(Income.class))).thenAnswer(i -> {
            Income e = i.getArgument(0);
            if (e.getId() == null) {
                ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
            }
            return e;
        });
        when(expenseRepository.save(any(Expense.class))).thenAnswer(i -> {
            Expense e = i.getArgument(0);
            if (e.getId() == null) {
                ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
            }
            return e;
        });
    }

    private DemoWallet wallet(String usd) {
        DemoWallet w = DemoWallet.builder().user(user)
            .balanceUsd(new BigDecimal(usd)).balanceKhr(BigDecimal.ZERO).build();
        when(demoWalletService.getOrCreateWalletEntity(user)).thenReturn(w);
        return w;
    }

    private RecurringTransaction template(RecurringType type, RecurringFrequency freq,
                                          LocalDate nextRun, LocalDate endDate, boolean fromWallet, String amount) {
        RecurringTransaction t = RecurringTransaction.builder()
            .user(user).type(type).title("Rent").amount(new BigDecimal(amount)).currency(Currency.USD)
            .category("HOUSING").frequency(freq).fromWallet(fromWallet)
            .nextRunDate(nextRun).startDate(nextRun).endDate(endDate).active(true).build();
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        return t;
    }

    // ---- create ----

    @Test
    void createSetsFirstRunToStartDateAndDefaultsCurrency() {
        var resp = service.create(new RecurringTransactionCreateRequest(
            RecurringType.INCOME, "Allowance", new BigDecimal("100.00"), null, "ALLOWANCE",
            RecurringFrequency.MONTHLY, null, LocalDate.of(2026, 7, 1), null));

        assertThat(resp.nextRunDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(resp.currency()).isEqualTo(Currency.USD);
        assertThat(resp.active()).isTrue();
        verify(recurringRepository).save(any(RecurringTransaction.class));
    }

    @Test
    void createRejectsEndDateBeforeStartDate() {
        assertThatThrownBy(() -> service.create(new RecurringTransactionCreateRequest(
            RecurringType.EXPENSE, "Rent", new BigDecimal("50.00"), Currency.USD, "HOUSING",
            RecurringFrequency.MONTHLY, true, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 1))))
            .isInstanceOf(ApiException.class);
    }

    // ---- processDue ----

    @Test
    void materialisesIncomeAndCreditsTheWallet() {
        DemoWallet w = wallet("0");
        RecurringTransaction t = template(RecurringType.INCOME, RecurringFrequency.MONTHLY,
            LocalDate.of(2026, 7, 1), null, false, "100.00");
        when(recurringRepository.findDue(any())).thenReturn(List.of(t));

        int count = service.processDue(LocalDate.of(2026, 7, 15));

        assertThat(count).isEqualTo(1);
        verify(incomeRepository).save(any(Income.class));
        verify(ledgerService).credit(eq(w), eq(TransactionType.INCOME), eq(Currency.USD),
            eq(new BigDecimal("100.00")), any(), eq("INCOME"), any());
        assertThat(t.getLastRunDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(t.getNextRunDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    void catchesUpEveryMissedRun() {
        wallet("0");
        RecurringTransaction t = template(RecurringType.INCOME, RecurringFrequency.DAILY,
            LocalDate.of(2026, 7, 1), null, false, "10.00");
        when(recurringRepository.findDue(any())).thenReturn(List.of(t));

        int count = service.processDue(LocalDate.of(2026, 7, 3)); // 1st, 2nd, 3rd

        assertThat(count).isEqualTo(3);
        verify(incomeRepository, times(3)).save(any(Income.class));
        assertThat(t.getNextRunDate()).isEqualTo(LocalDate.of(2026, 7, 4));
    }

    @Test
    void walletFundedExpenseDebitsWhenThereAreFunds() {
        DemoWallet w = wallet("100.00");
        RecurringTransaction t = template(RecurringType.EXPENSE, RecurringFrequency.MONTHLY,
            LocalDate.of(2026, 7, 1), null, true, "20.00");
        when(recurringRepository.findDue(any())).thenReturn(List.of(t));

        service.processDue(LocalDate.of(2026, 7, 10));

        verify(expenseRepository).save(any(Expense.class));
        verify(ledgerService).debit(eq(w), eq(TransactionType.EXPENSE), eq(Currency.USD),
            eq(new BigDecimal("20.00")), any(), eq("EXPENSE"), any());
    }

    @Test
    void walletFundedExpenseIsStillTrackedButSkipsDebitWhenShort() {
        wallet("5.00"); // less than the 20.00 expense
        RecurringTransaction t = template(RecurringType.EXPENSE, RecurringFrequency.MONTHLY,
            LocalDate.of(2026, 7, 1), null, true, "20.00");
        when(recurringRepository.findDue(any())).thenReturn(List.of(t));

        service.processDue(LocalDate.of(2026, 7, 10));

        verify(expenseRepository).save(any(Expense.class)); // expense still recorded
        verify(ledgerService, never()).debit(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void stopsAndDeactivatesPastTheEndDate() {
        wallet("0");
        RecurringTransaction t = template(RecurringType.INCOME, RecurringFrequency.DAILY,
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), false, "10.00");
        when(recurringRepository.findDue(any())).thenReturn(List.of(t));

        int count = service.processDue(LocalDate.of(2026, 7, 10));

        assertThat(count).isEqualTo(2); // 1st and 2nd only
        assertThat(t.isActive()).isFalse();
    }
}

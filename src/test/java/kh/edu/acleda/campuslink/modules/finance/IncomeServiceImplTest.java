package kh.edu.acleda.campuslink.modules.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.IncomeCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.CashFlowResponse;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.entity.Income;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.IncomeRepository;
import kh.edu.acleda.campuslink.modules.finance.service.DemoWalletService;
import kh.edu.acleda.campuslink.modules.finance.service.LedgerService;
import kh.edu.acleda.campuslink.modules.finance.service.impl.IncomeServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IncomeServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID INCOME_ID = UUID.randomUUID();

    @Mock private IncomeRepository incomeRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private DemoWalletService demoWalletService;
    @Mock private LedgerService ledgerService;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private IncomeServiceImpl service;
    private User user;
    private Income income;

    @BeforeEach
    void setUp() {
        service = new IncomeServiceImpl(incomeRepository, expenseRepository, demoWalletService,
            ledgerService, userRepository, currentUserProvider);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        income = Income.builder().user(user).title("Allowance").amount(new BigDecimal("100.00"))
            .source("ALLOWANCE").incomeDate(LocalDate.of(2026, 7, 1)).build();
        ReflectionTestUtils.setField(income, "id", INCOME_ID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(incomeRepository.save(any(Income.class))).thenAnswer(i -> {
            Income saved = i.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", INCOME_ID);
            }
            return saved;
        });
    }

    @Test
    void createPersistsTheIncomeAndCreditsTheWallet() {
        DemoWallet wallet = DemoWallet.builder().user(user)
            .balanceUsd(new BigDecimal("0.00"))
            .balanceKhr(new BigDecimal("0.00"))
            .build();
        when(demoWalletService.getOrCreateWalletEntity()).thenReturn(wallet);

        var response = service.create(new IncomeCreateRequest(
            "Allowance", new BigDecimal("100.00"), "ALLOWANCE", null, LocalDate.of(2026, 7, 1), null));

        verify(incomeRepository).save(any(Income.class));
        assertThat(response.amount()).isEqualByComparingTo("100.00");
        assertThat(response.source()).isEqualTo("ALLOWANCE");
        // Income always credits the wallet.
        verify(ledgerService).credit(eq(wallet), eq(TransactionType.INCOME), eq(Currency.USD),
            eq(new BigDecimal("100.00")), any(), eq("INCOME"), any());
    }

    @Test
    void getMineWithAMonthUsesTheDateRangeQuery() {
        when(incomeRepository.findByUserAndDateRange(eq(USER_ID), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(income)));

        assertThat(service.getMine(null, 7, 2026, PageRequest.of(0, 20)).content()).hasSize(1);
        verify(incomeRepository).findByUserAndDateRange(eq(USER_ID), any(), any(), any(), any());
    }

    @Test
    void getMineWithoutAMonthUsesThePlainList() {
        when(incomeRepository.findByUser(eq(USER_ID), any(), any())).thenReturn(new PageImpl<>(List.of(income)));

        assertThat(service.getMine(null, null, null, PageRequest.of(0, 20)).content()).hasSize(1);
        verify(incomeRepository).findByUser(eq(USER_ID), any(), any());
    }

    @Test
    void anotherUsersIncomeReadsAsNotFound() {
        when(incomeRepository.findByIdAndUserIdAndDeletedAtIsNull(INCOME_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(INCOME_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteIsASoftDelete() {
        when(incomeRepository.findByIdAndUserIdAndDeletedAtIsNull(INCOME_ID, USER_ID)).thenReturn(Optional.of(income));

        service.delete(INCOME_ID);

        assertThat(income.getDeletedAt()).isNotNull();
    }

    // ---- Cash flow ----

    @Test
    void cashFlowComputesNetAndSavingsRate() {
        when(incomeRepository.sumForMonthByCurrency(eq(USER_ID), any(), any(), any())).thenReturn(new BigDecimal("100.00"));
        when(expenseRepository.sumForMonthByCurrency(eq(USER_ID), any(), any(), any())).thenReturn(new BigDecimal("30.00"));

        CashFlowResponse r = service.cashFlow(Currency.USD, 7, 2026);

        assertThat(r.totalIncome()).isEqualByComparingTo("100.00");
        assertThat(r.totalExpense()).isEqualByComparingTo("30.00");
        assertThat(r.net()).isEqualByComparingTo("70.00");
        // 70 / 100 = 70.0%
        assertThat(r.savingsRatePercent()).isEqualByComparingTo("70.0");
    }

    /** No income means the ratio is undefined, so it is reported as 0 rather than dividing by zero. */
    @Test
    void cashFlowWithNoIncomeReportsAZeroSavingsRate() {
        when(incomeRepository.sumForMonthByCurrency(eq(USER_ID), any(), any(), any())).thenReturn(null);
        when(expenseRepository.sumForMonthByCurrency(eq(USER_ID), any(), any(), any())).thenReturn(new BigDecimal("20.00"));

        CashFlowResponse r = service.cashFlow(Currency.USD, 7, 2026);

        assertThat(r.totalIncome()).isEqualByComparingTo("0");
        assertThat(r.net()).isEqualByComparingTo("-20.00");
        assertThat(r.savingsRatePercent()).isEqualByComparingTo("0");
    }

    /** Overspending is a true, useful signal: net and savings rate both go negative. */
    @Test
    void cashFlowGoesNegativeWhenOverspent() {
        when(incomeRepository.sumForMonthByCurrency(eq(USER_ID), any(), any(), any())).thenReturn(new BigDecimal("50.00"));
        when(expenseRepository.sumForMonthByCurrency(eq(USER_ID), any(), any(), any())).thenReturn(new BigDecimal("80.00"));

        CashFlowResponse r = service.cashFlow(Currency.USD, 7, 2026);

        assertThat(r.net()).isEqualByComparingTo("-30.00");
        // -30 / 50 = -60.0%
        assertThat(r.savingsRatePercent()).isEqualByComparingTo("-60.0");
    }
}

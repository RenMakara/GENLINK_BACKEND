package kh.edu.acleda.campuslink.modules.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.ExpenseCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.request.IncomeCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.request.SavingGoalCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.ExpenseResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.IncomeResponse;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionDirection;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoTransactionRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoWalletRepository;
import kh.edu.acleda.campuslink.modules.finance.service.ExpenseService;
import kh.edu.acleda.campuslink.modules.finance.service.IncomeService;
import kh.edu.acleda.campuslink.modules.finance.service.LedgerService;
import kh.edu.acleda.campuslink.modules.finance.service.SavingGoalService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class FinanceTransactionConsistencyIntegrationTest {

    @Autowired private IncomeService incomeService;
    @Autowired private ExpenseService expenseService;
    @Autowired private SavingGoalService savingGoalService;
    @Autowired private LedgerService ledgerService;
    @Autowired private DemoTransactionRepository transactionRepository;
    @Autowired private DemoWalletRepository walletRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(Currency.class)
    void incomeCreateUpdateDeleteKeepsMatchingWalletBalanceCorrect(Currency currency) {
        DemoWallet wallet = authenticatedWallet("income-" + currency, BigDecimal.ZERO, BigDecimal.ZERO);

        IncomeResponse created = incomeService.create(incomeRequest("100.00", currency));
        assertBalance(wallet.getId(), currency, "100.00");

        incomeService.update(created.incomeId(), incomeRequest("60.00", currency));
        assertBalance(wallet.getId(), currency, "60.00");

        incomeService.delete(created.incomeId());
        assertBalance(wallet.getId(), currency, "0.00");
    }

    @ParameterizedTest
    @EnumSource(Currency.class)
    void walletPaidExpenseCreateUpdateDeleteKeepsMatchingWalletBalanceCorrect(Currency currency) {
        DemoWallet wallet = authenticatedWallet("expense-" + currency,
            new BigDecimal("200.00"), new BigDecimal("200.00"));

        ExpenseResponse created = expenseService.create(expenseRequest("50.00", currency, true));
        assertThat(created.paidFromWallet()).isTrue();
        assertBalance(wallet.getId(), currency, "150.00");

        ExpenseResponse updated = expenseService.update(
            created.expenseId(), expenseRequest("20.00", currency, true));
        assertThat(updated.paidFromWallet()).isTrue();
        assertBalance(wallet.getId(), currency, "180.00");

        expenseService.delete(created.expenseId());
        assertBalance(wallet.getId(), currency, "200.00");
    }

    @Test
    void insufficientWalletExpenseCreateRollsBackExpenseWalletAndLedger() {
        DemoWallet wallet = authenticatedWallet("expense-rollback", new BigDecimal("10.00"), BigDecimal.ZERO);
        long ledgerBefore = ledgerCount(wallet.getId());

        assertThatThrownBy(() -> expenseService.create(expenseRequest("20.00", Currency.USD, true)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("too low");

        assertBalance(wallet.getId(), Currency.USD, "10.00");
        assertThat(ledgerCount(wallet.getId())).isEqualTo(ledgerBefore);
        assertThat(expenseService.getMine(null, null, null, Currency.USD, PageRequest.of(0, 20))
            .totalElements()).isZero();
    }

    @Test
    void insufficientWalletIncomeUpdateAndDeleteRollBackPersistedState() {
        DemoWallet wallet = authenticatedWallet("income-rollback", BigDecimal.ZERO, BigDecimal.ZERO);
        IncomeResponse created = incomeService.create(incomeRequest("100.00", Currency.USD));
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            ledgerService.debit(wallet, TransactionType.EXPENSE, Currency.USD, new BigDecimal("90.00"),
                "Spend income", "EXPENSE", UUID.randomUUID()));
        long ledgerBefore = ledgerCount(wallet.getId());

        assertThatThrownBy(() -> incomeService.update(created.incomeId(), incomeRequest("1.00", Currency.USD)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("too low");
        assertThatThrownBy(() -> incomeService.delete(created.incomeId()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("too low");

        IncomeResponse persisted = incomeService.getById(created.incomeId());
        assertThat(persisted.amount()).isEqualByComparingTo("100.00");
        assertBalance(wallet.getId(), Currency.USD, "10.00");
        assertThat(ledgerCount(wallet.getId())).isEqualTo(ledgerBefore);
    }

    @Test
    void failedSecondLegOfExpenseCurrencyChangeRollsBackFirstLegAndExpense() {
        DemoWallet wallet = authenticatedWallet("expense-currency-rollback",
            new BigDecimal("100.00"), BigDecimal.ZERO);
        ExpenseResponse created = expenseService.create(expenseRequest("50.00", Currency.USD, true));
        long ledgerBefore = ledgerCount(wallet.getId());

        assertThatThrownBy(() -> expenseService.update(created.expenseId(),
            expenseRequest("10000.00", Currency.KHR, true)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("too low");

        ExpenseResponse persisted = expenseService.getById(created.expenseId());
        assertThat(persisted.amount()).isEqualByComparingTo("50.00");
        assertThat(persisted.currency()).isEqualTo(Currency.USD);
        assertThat(persisted.paidFromWallet()).isTrue();
        assertBalance(wallet.getId(), Currency.USD, "50.00");
        assertBalance(wallet.getId(), Currency.KHR, "0.00");
        assertThat(ledgerCount(wallet.getId())).isEqualTo(ledgerBefore);
    }

    @Test
    void databaseFailureAfterWalletMutationRollsBackWalletLedgerAndExpense() {
        DemoWallet wallet = authenticatedWallet("expense-db-rollback",
            new BigDecimal("100.00"), BigDecimal.ZERO);
        ExpenseResponse created = expenseService.create(expenseRequest("10.00", Currency.USD, true));
        long ledgerBefore = ledgerCount(wallet.getId());
        String titleTooLongForColumn = "x".repeat(161);

        assertThatThrownBy(() -> expenseService.update(created.expenseId(), new ExpenseCreateRequest(
            titleTooLongForColumn, new BigDecimal("20.00"), "FOOD", null,
            LocalDate.of(2026, 8, 1), "WALLET", true, Currency.USD)))
            .isInstanceOf(RuntimeException.class);

        ExpenseResponse persisted = expenseService.getById(created.expenseId());
        assertThat(persisted.title()).isEqualTo("Lunch");
        assertThat(persisted.amount()).isEqualByComparingTo("10.00");
        assertBalance(wallet.getId(), Currency.USD, "90.00");
        assertThat(ledgerCount(wallet.getId())).isEqualTo(ledgerBefore);
    }

    @ParameterizedTest
    @EnumSource(Currency.class)
    void togglingPaidFromWalletIsAtomicInBothDirections(Currency currency) {
        DemoWallet wallet = authenticatedWallet("expense-toggle-" + currency,
            new BigDecimal("200.00"), new BigDecimal("200.00"));
        ExpenseResponse created = expenseService.create(expenseRequest("50.00", currency, false));
        assertBalance(wallet.getId(), currency, "200.00");

        ExpenseResponse paid = expenseService.update(created.expenseId(),
            expenseRequest("50.00", currency, true));
        assertThat(paid.paidFromWallet()).isTrue();
        assertBalance(wallet.getId(), currency, "150.00");

        ExpenseResponse unpaid = expenseService.update(created.expenseId(),
            expenseRequest("50.00", currency, false));
        assertThat(unpaid.paidFromWallet()).isFalse();
        assertBalance(wallet.getId(), currency, "200.00");

        expenseService.delete(created.expenseId());
        assertBalance(wallet.getId(), currency, "200.00");
    }

    @ParameterizedTest
    @EnumSource(Currency.class)
    void listCurrencyFiltersAreAppliedBeforePageMetadataIsCalculated(Currency currency) {
        Currency other = currency == Currency.USD ? Currency.KHR : Currency.USD;
        authenticatedWallet("filter-" + currency,
            new BigDecimal("1000.00"), new BigDecimal("1000000.00"));

        incomeService.create(incomeRequest("10.00", currency));
        incomeService.create(incomeRequest("20.00", currency));
        incomeService.create(incomeRequest("30.00", other));
        expenseService.create(expenseRequest("1.00", currency, false));
        expenseService.create(expenseRequest("2.00", currency, false));
        expenseService.create(expenseRequest("3.00", other, false));
        savingGoalService.create(new SavingGoalCreateRequest("Selected", new BigDecimal("100"), null, currency));
        savingGoalService.create(new SavingGoalCreateRequest("Other", new BigDecimal("100"), null, other));

        var incomes = incomeService.getMine(null, null, null, currency, PageRequest.of(0, 1));
        var expenses = expenseService.getMine(null, null, null, currency, PageRequest.of(0, 1));

        assertThat(incomes.content()).hasSize(1).allMatch(row -> row.currency() == currency);
        assertThat(incomes.totalElements()).isEqualTo(2);
        assertThat(incomes.totalPages()).isEqualTo(2);
        assertThat(incomes.hasNext()).isTrue();
        assertThat(expenses.content()).hasSize(1).allMatch(row -> row.currency() == currency);
        assertThat(expenses.totalElements()).isEqualTo(2);
        assertThat(expenses.totalPages()).isEqualTo(2);
        assertThat(expenses.hasNext()).isTrue();
        assertThat(savingGoalService.getMine(null, currency))
            .extracting(goal -> goal.currency())
            .containsOnly(currency);
    }

    private IncomeCreateRequest incomeRequest(String amount, Currency currency) {
        return new IncomeCreateRequest("Allowance", new BigDecimal(amount), "ALLOWANCE", null,
            LocalDate.of(2026, 8, 1), currency);
    }

    private ExpenseCreateRequest expenseRequest(String amount, Currency currency, boolean paidFromWallet) {
        return new ExpenseCreateRequest("Lunch", new BigDecimal(amount), "FOOD", null,
            LocalDate.of(2026, 8, 1), paidFromWallet ? "WALLET" : "CASH", paidFromWallet, currency);
    }

    private DemoWallet authenticatedWallet(String prefix, BigDecimal usd, BigDecimal khr) {
        String suffix = UUID.randomUUID().toString();
        User user = new User();
        user.setEmail(prefix + "-" + suffix + "@example.com");
        String usernamePrefix = prefix.length() > 20 ? prefix.substring(0, 20) : prefix;
        user.setUsername(usernamePrefix + "-" + suffix.substring(0, 8));
        user.setFullName("Finance " + suffix);
        user = userRepository.saveAndFlush(user);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of()));
        return walletRepository.saveAndFlush(DemoWallet.builder()
            .user(user)
            .balanceUsd(usd)
            .balanceKhr(khr)
            .build());
    }

    private void assertBalance(UUID walletId, Currency currency, String expected) {
        DemoWallet wallet = walletRepository.findById(walletId).orElseThrow();
        BigDecimal balance = currency == Currency.KHR ? wallet.getBalanceKhr() : wallet.getBalanceUsd();
        assertThat(balance).isEqualByComparingTo(expected);
    }

    private long ledgerCount(UUID walletId) {
        return transactionRepository.countByWalletIdAndDirection(walletId, TransactionDirection.CREDIT)
            + transactionRepository.countByWalletIdAndDirection(walletId, TransactionDirection.DEBIT);
    }
}

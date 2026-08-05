package kh.edu.acleda.campuslink.modules.finance;

import java.math.BigDecimal;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoTransaction;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionDirection;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionStatus;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoTransactionRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoWalletRepository;
import kh.edu.acleda.campuslink.modules.finance.service.impl.LedgerServiceImpl;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LedgerServiceImplTest {

    @Mock private DemoTransactionRepository transactionRepository;
    @Mock private DemoWalletRepository walletRepository;

    private LedgerServiceImpl service;
    private User user;
    private DemoWallet wallet;

    @BeforeEach
    void setUp() {
        service = new LedgerServiceImpl(transactionRepository, walletRepository);
        user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        wallet = DemoWallet.builder().user(user)
            .balanceUsd(new BigDecimal("20.00"))
            .balanceKhr(new BigDecimal("10000.00"))
            .build();
        ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());
        when(transactionRepository.save(any(DemoTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(walletRepository.save(any(DemoWallet.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void creditAddsToTheBalanceAndRecordsACreditRow() {
        UUID incomeId = UUID.randomUUID();

        DemoTransaction t = service.credit(wallet, TransactionType.INCOME, Currency.USD, new BigDecimal("30.00"),
            "Allowance", "INCOME", incomeId);

        assertThat(wallet.getBalanceUsd()).isEqualByComparingTo("50.00");
        assertThat(wallet.getBalanceKhr()).isEqualByComparingTo("10000.00");
        assertThat(t.getDirection()).isEqualTo(TransactionDirection.CREDIT);
        assertThat(t.getCurrency()).isEqualTo(Currency.USD);
        assertThat(t.getUser()).isEqualTo(user);
        assertThat(t.getRelatedType()).isEqualTo("INCOME");
        assertThat(t.getRelatedId()).isEqualTo(incomeId);
        assertThat(t.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        verify(walletRepository).save(wallet);
    }

    @Test
    void debitSubtractsFromTheBalanceAndRecordsADebitRow() {
        DemoTransaction t = service.debit(wallet, TransactionType.EXPENSE, Currency.KHR, new BigDecimal("5000.00"),
            "Coffee", "EXPENSE", UUID.randomUUID());

        assertThat(wallet.getBalanceKhr()).isEqualByComparingTo("5000.00");
        assertThat(wallet.getBalanceUsd()).isEqualByComparingTo("20.00");
        assertThat(t.getDirection()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(t.getCurrency()).isEqualTo(Currency.KHR);
    }

    /** The single insufficient-funds guard: nothing is written and the balance is untouched. */
    @Test
    void debitRejectsWhenTheBalanceIsTooLowAndMovesNothing() {
        assertThatThrownBy(() -> service.debit(wallet, TransactionType.EXPENSE, Currency.USD, new BigDecimal("50.00"),
            "Too big", "EXPENSE", UUID.randomUUID()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("too low");

        assertThat(wallet.getBalanceUsd()).isEqualByComparingTo("20.00");
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    /** The low-level record writes a row with an explicit direction and does NOT move the balance. */
    @Test
    void recordWritesARowWithoutMovingTheBalance() {
        DemoTransaction t = service.record(wallet, TransactionType.DEPOSIT, Currency.USD, TransactionDirection.CREDIT,
            new BigDecimal("10.00"), "Top-up", null, null);

        assertThat(wallet.getBalanceUsd()).isEqualByComparingTo("20.00");
        assertThat(t.getCurrency()).isEqualTo(Currency.USD);
        assertThat(t.getRelatedType()).isNull();
        assertThat(t.getUser()).isEqualTo(user);
        verify(walletRepository, never()).save(any());
    }
}

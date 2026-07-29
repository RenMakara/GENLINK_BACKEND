package kh.edu.acleda.campuslink.modules.finance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.WalletPaymentRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.request.WalletTopUpRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.WalletResponse;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoTransaction;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionDirection;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoTransactionRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoWalletRepository;
import kh.edu.acleda.campuslink.modules.finance.service.LedgerService;
import kh.edu.acleda.campuslink.modules.finance.service.impl.DemoWalletServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DemoWalletServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WALLET_ID = UUID.randomUUID();

    @Mock private DemoWalletRepository demoWalletRepository;
    @Mock private DemoTransactionRepository demoTransactionRepository;
    @Mock private LedgerService ledgerService;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private DemoWalletServiceImpl service;
    private User user;
    private DemoWallet wallet;

    @BeforeEach
    void setUp() {
        service = new DemoWalletServiceImpl(demoWalletRepository, demoTransactionRepository,
            ledgerService, userRepository, currentUserProvider);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);

        wallet = DemoWallet.builder().user(user).balance(new BigDecimal("50.00")).currency("USD").build();
        ReflectionTestUtils.setField(wallet, "id", WALLET_ID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(demoWalletRepository.save(any(DemoWallet.class))).thenAnswer(i -> i.getArgument(0));
        when(demoWalletRepository.saveAndFlush(any(DemoWallet.class))).thenAnswer(i -> {
            DemoWallet w = i.getArgument(0);
            if (w.getId() == null) {
                ReflectionTestUtils.setField(w, "id", WALLET_ID);
            }
            return w;
        });
        when(demoTransactionRepository.save(any(DemoTransaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void getOrCreateOpensAZeroBalanceWalletWhenNoneExists() {
        when(demoWalletRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        WalletResponse r = service.getOrCreate();

        assertThat(r.balance()).isEqualByComparingTo("0");
        assertThat(r.currency()).isEqualTo("USD");
    }

    @Test
    void getOrCreateReturnsTheExistingWallet() {
        when(demoWalletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));

        assertThat(service.getOrCreate().balance()).isEqualByComparingTo("50.00");
        verify(demoWalletRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentCreateLosesTheRaceAndRereadsTheWinnersWallet() {
        when(demoWalletRepository.findByUserId(USER_ID))
            .thenReturn(Optional.empty())   // first check: none yet
            .thenReturn(Optional.of(wallet)); // re-read after the constraint violation
        when(demoWalletRepository.saveAndFlush(any(DemoWallet.class)))
            .thenThrow(new DataIntegrityViolationException("uk_demo_wallets_user"));

        assertThat(service.getOrCreate().walletId()).isEqualTo(WALLET_ID);
    }

    @Test
    void topUpAddsToBalanceAndRecordsADepositTransaction() {
        when(demoWalletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));

        WalletResponse r = service.topUp(new WalletTopUpRequest(new BigDecimal("25.00"), "pocket money"));

        assertThat(r.balance()).isEqualByComparingTo("75.00");
        verify(ledgerService).record(eq(wallet), eq(TransactionType.DEPOSIT),
            eq(TransactionDirection.CREDIT), any(), any());
    }

    @Test
    void payDebitsBalanceAndRecordsAKhqrTransaction() {
        when(demoWalletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));

        WalletResponse r = service.pay(new WalletPaymentRequest(new BigDecimal("20.00"), "Brown Coffee", null));

        assertThat(r.balance()).isEqualByComparingTo("30.00");
        verify(ledgerService).record(eq(wallet), eq(TransactionType.MOCK_KHQR_PAYMENT),
            eq(TransactionDirection.DEBIT), any(), any());
    }

    @Test
    void payRejectsWhenBalanceIsTooLowAndMovesNothing() {
        wallet.setBalance(new BigDecimal("5.00"));
        when(demoWalletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.pay(new WalletPaymentRequest(new BigDecimal("20.00"), "shop", null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("too low");

        assertThat(wallet.getBalance()).isEqualByComparingTo("5.00");
        verify(demoWalletRepository, never()).save(any());
        verify(ledgerService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void transactionsIsAnEmptyPageWhenTheWalletDoesNotExistYet() {
        when(demoWalletRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThat(service.transactions(PageRequest.of(0, 20)).content()).isEmpty();
        verify(demoTransactionRepository, never()).findByWalletIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void transactionsMapsHistoryWhenTheWalletExists() {
        when(demoWalletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));
        DemoTransaction t = DemoTransaction.builder().wallet(wallet)
            .transactionType(TransactionType.DEPOSIT).amount(new BigDecimal("25.00"))
            .description("Simulated top-up").status(kh.edu.acleda.campuslink.modules.finance.enums.TransactionStatus.SUCCESS)
            .build();
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        when(demoTransactionRepository.findByWalletIdOrderByCreatedAtDesc(any(), any()))
            .thenReturn(new PageImpl<>(List.of(t)));

        assertThat(service.transactions(PageRequest.of(0, 20)).content()).singleElement()
            .satisfies(r -> assertThat(r.amount()).isEqualByComparingTo("25.00"));
    }
}

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
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionDirection;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.payment.PaymentProvider;
import kh.edu.acleda.campuslink.modules.finance.payment.PaymentResult;
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
    @Mock private PaymentProvider paymentProvider;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private DemoWalletServiceImpl service;
    private User user;
    private DemoWallet wallet;

    @BeforeEach
    void setUp() {
        service = new DemoWalletServiceImpl(demoWalletRepository, demoTransactionRepository,
            ledgerService, paymentProvider, userRepository, currentUserProvider);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);

        wallet = DemoWallet.builder().user(user)
            .balanceUsd(new BigDecimal("50.00"))
            .balanceKhr(new BigDecimal("0.00"))
            .build();
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
        // Default: the payment provider approves, returning its (mock) identity + reference.
        when(paymentProvider.charge(any())).thenReturn(PaymentResult.success("MOCK_KHQR", "MOCK-TEST1234"));
    }

    @Test
    void getOrCreateOpensAZeroBalanceWalletWhenNoneExists() {
        when(demoWalletRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        WalletResponse r = service.getOrCreate();

        assertThat(r.balanceUsd()).isEqualByComparingTo("0");
        assertThat(r.balanceKhr()).isEqualByComparingTo("0");
    }

    @Test
    void getOrCreateReturnsTheExistingWallet() {
        when(demoWalletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));

        assertThat(service.getOrCreate().balanceUsd()).isEqualByComparingTo("50.00");
        verify(demoWalletRepository, never()).saveAndFlush(any());
    }

    @Test
    void firstAccessLocksTheUserRowBeforeCreatingTheWallet() {
        when(demoWalletRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        assertThat(service.getOrCreate().balanceUsd()).isZero();
        verify(userRepository).findByIdForUpdate(USER_ID);
        verify(demoWalletRepository).saveAndFlush(any(DemoWallet.class));
    }

    /** Balance movement lives in LedgerService now, so this asserts the delegation. */
    @Test
    void topUpCreditsTheWalletThroughTheLedger() {
        when(demoWalletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));

        service.topUp(new WalletTopUpRequest(new BigDecimal("25.00"), "pocket money", null));

        verify(ledgerService).credit(eq(wallet), eq(TransactionType.DEPOSIT), eq(Currency.USD),
            eq(new BigDecimal("25.00")), any());
    }

    @Test
    void payChargesTheProviderThenDebitsWithProviderInfo() {
        when(demoWalletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));

        service.pay(new WalletPaymentRequest(new BigDecimal("20.00"), "Brown Coffee", null, Currency.KHR));

        // Provider-neutral type; the provider identity + reference ride along on the ledger row.
        verify(ledgerService).debit(eq(wallet), eq(TransactionType.KHQR_PAYMENT), eq(Currency.KHR),
            eq(new BigDecimal("20.00")), any(), any(), any(), eq("MOCK_KHQR"), eq("MOCK-TEST1234"));
    }

    /** A provider decline is a business error; the ledger is never touched. */
    @Test
    void payThrowsWhenTheProviderDeclinesAndNeverDebits() {
        when(demoWalletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));
        when(paymentProvider.charge(any())).thenReturn(PaymentResult.failed("MOCK_KHQR", "card blocked"));

        assertThatThrownBy(() -> service.pay(
            new WalletPaymentRequest(new BigDecimal("20.00"), "shop", null, Currency.USD)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("declined");

        verify(ledgerService, never()).debit(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    /** The insufficient-funds guard lives in the ledger now; the wallet service just propagates. */
    @Test
    void payPropagatesTheLedgersInsufficientFundsError() {
        when(demoWalletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));
        org.mockito.Mockito.doThrow(new ApiException("Demo wallet balance is too low for this payment",
                "DEMO_WALLET_INSUFFICIENT_BALANCE"))
            .when(ledgerService).debit(any(), any(), any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.pay(new WalletPaymentRequest(new BigDecimal("20.00"), "shop", null, null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("too low");
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
            .currency(Currency.USD)
            .description("Simulated top-up").status(kh.edu.acleda.campuslink.modules.finance.enums.TransactionStatus.SUCCESS)
            .build();
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        when(demoTransactionRepository.findByWalletIdOrderByCreatedAtDesc(any(), any()))
            .thenReturn(new PageImpl<>(List.of(t)));

        assertThat(service.transactions(PageRequest.of(0, 20)).content()).singleElement()
            .satisfies(r -> assertThat(r.amount()).isEqualByComparingTo("25.00"));
    }
}

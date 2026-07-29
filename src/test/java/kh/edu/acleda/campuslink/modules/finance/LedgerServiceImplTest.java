package kh.edu.acleda.campuslink.modules.finance;

import java.math.BigDecimal;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoTransaction;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionDirection;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionStatus;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoTransactionRepository;
import kh.edu.acleda.campuslink.modules.finance.service.impl.LedgerServiceImpl;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceImplTest {

    @Mock private DemoTransactionRepository transactionRepository;

    private LedgerServiceImpl service;
    private User user;
    private DemoWallet wallet;

    @BeforeEach
    void setUp() {
        service = new LedgerServiceImpl(transactionRepository);
        user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        wallet = DemoWallet.builder().user(user).balance(new BigDecimal("20.00")).currency("USD").build();
        ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());
        when(transactionRepository.save(any(DemoTransaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void recordCapturesDirectionOwnerAndTheSourceLink() {
        UUID expenseId = UUID.randomUUID();

        DemoTransaction t = service.record(wallet, TransactionType.EXPENSE, TransactionDirection.DEBIT,
            new BigDecimal("4.50"), "Coffee", "EXPENSE", expenseId);

        assertThat(t.getUser()).isEqualTo(user);
        assertThat(t.getDirection()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(t.getTransactionType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(t.getRelatedType()).isEqualTo("EXPENSE");
        assertThat(t.getRelatedId()).isEqualTo(expenseId);
        assertThat(t.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        verify(transactionRepository).save(any(DemoTransaction.class));
    }

    /** The convenience overload is for wallet-native rows with no source entity to link. */
    @Test
    void recordWithoutASourceLeavesTheRelatedLinkNull() {
        DemoTransaction t = service.record(wallet, TransactionType.DEPOSIT, TransactionDirection.CREDIT,
            new BigDecimal("10.00"), "Top-up");

        assertThat(t.getRelatedType()).isNull();
        assertThat(t.getRelatedId()).isNull();
        assertThat(t.getUser()).isEqualTo(user);
        assertThat(t.getDirection()).isEqualTo(TransactionDirection.CREDIT);
    }
}

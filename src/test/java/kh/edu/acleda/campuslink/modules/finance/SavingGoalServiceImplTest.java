package kh.edu.acleda.campuslink.modules.finance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.SavingDepositRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.request.SavingGoalCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.SavingGoalResponse;
import kh.edu.acleda.campuslink.modules.finance.entity.SavingDeposit;
import kh.edu.acleda.campuslink.modules.finance.entity.SavingGoal;
import kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus;
import kh.edu.acleda.campuslink.modules.finance.repository.SavingDepositRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.SavingGoalRepository;
import kh.edu.acleda.campuslink.modules.finance.service.impl.SavingGoalServiceImpl;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SavingGoalServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID GOAL_ID = UUID.randomUUID();

    @Mock private SavingGoalRepository savingGoalRepository;
    @Mock private SavingDepositRepository savingDepositRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private SavingGoalServiceImpl service;
    private User user;
    private SavingGoal goal;

    @BeforeEach
    void setUp() {
        service = new SavingGoalServiceImpl(savingGoalRepository, savingDepositRepository,
            userRepository, currentUserProvider);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);

        goal = SavingGoal.builder().user(user).title("Laptop")
            .targetAmount(new BigDecimal("100.00")).currentAmount(BigDecimal.ZERO)
            .status(SavingGoalStatus.IN_PROGRESS).build();
        ReflectionTestUtils.setField(goal, "id", GOAL_ID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(savingGoalRepository.save(any(SavingGoal.class))).thenAnswer(i -> i.getArgument(0));
        when(savingDepositRepository.save(any(SavingDeposit.class))).thenAnswer(i -> i.getArgument(0));
        when(savingGoalRepository.findByIdAndUserIdAndDeletedAtIsNull(GOAL_ID, USER_ID))
            .thenReturn(Optional.of(goal));
    }

    @Test
    void createStartsInProgressWithZeroSaved() {
        SavingGoalResponse r = service.create(
            new SavingGoalCreateRequest("Laptop", new BigDecimal("100.00"), null));

        assertThat(r.status()).isEqualTo(SavingGoalStatus.IN_PROGRESS);
        assertThat(r.currentAmount()).isEqualByComparingTo("0");
        assertThat(r.remainingAmount()).isEqualByComparingTo("100.00");
        assertThat(r.progressPercent()).isZero();
    }

    @Test
    void depositAddsToCurrentAndRecordsHistoryRow() {
        SavingGoalResponse r = service.deposit(GOAL_ID,
            new SavingDepositRequest(new BigDecimal("40.00"), "allowance", null));

        verify(savingDepositRepository).save(any(SavingDeposit.class));
        assertThat(r.currentAmount()).isEqualByComparingTo("40.00");
        assertThat(r.progressPercent()).isEqualTo(40);
        assertThat(r.status()).isEqualTo(SavingGoalStatus.IN_PROGRESS);
    }

    @Test
    void depositCompletesTheGoalWhenTargetReached() {
        SavingGoalResponse r = service.deposit(GOAL_ID,
            new SavingDepositRequest(new BigDecimal("100.00"), null, null));

        assertThat(r.status()).isEqualTo(SavingGoalStatus.COMPLETED);
        assertThat(r.remainingAmount()).isEqualByComparingTo("0");
        assertThat(r.progressPercent()).isEqualTo(100);
    }

    @Test
    void overfundingCapsProgressAtHundredAndRemainingAtZero() {
        SavingGoalResponse r = service.deposit(GOAL_ID,
            new SavingDepositRequest(new BigDecimal("150.00"), null, null));

        assertThat(r.progressPercent()).isEqualTo(100);
        assertThat(r.remainingAmount()).isEqualByComparingTo("0");
        assertThat(r.status()).isEqualTo(SavingGoalStatus.COMPLETED);
    }

    @Test
    void depositIntoCancelledGoalIsRejected() {
        goal.setStatus(SavingGoalStatus.CANCELLED);

        assertThatThrownBy(() -> service.deposit(GOAL_ID,
            new SavingDepositRequest(new BigDecimal("10.00"), null, null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("cancelled");
    }

    @Test
    void loweringTargetBelowSavedCompletesTheGoal() {
        goal.setCurrentAmount(new BigDecimal("60.00"));

        SavingGoalResponse r = service.update(GOAL_ID,
            new SavingGoalCreateRequest("Laptop", new BigDecimal("50.00"), null));

        assertThat(r.status()).isEqualTo(SavingGoalStatus.COMPLETED);
    }

    @Test
    void updatingSomeoneElsesGoalReadsAsNotFound() {
        when(savingGoalRepository.findByIdAndUserIdAndDeletedAtIsNull(GOAL_ID, USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(GOAL_ID,
            new SavingGoalCreateRequest("x", new BigDecimal("1"), null)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteSoftDeletes() {
        service.delete(GOAL_ID);

        assertThat(goal.getDeletedAt()).isNotNull();
        verify(savingGoalRepository).save(goal);
    }

    @Test
    void getMineWithoutStatusReturnsAll() {
        when(savingGoalRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(USER_ID))
            .thenReturn(List.of(goal));

        assertThat(service.getMine(null)).hasSize(1);
    }

    @Test
    void getMineWithStatusFiltersByStatus() {
        when(savingGoalRepository.findByUserIdAndDeletedAtIsNullAndStatusOrderByCreatedAtDesc(
            USER_ID, SavingGoalStatus.COMPLETED)).thenReturn(List.of());

        assertThat(service.getMine(SavingGoalStatus.COMPLETED)).isEmpty();
        verify(savingGoalRepository)
            .findByUserIdAndDeletedAtIsNullAndStatusOrderByCreatedAtDesc(USER_ID, SavingGoalStatus.COMPLETED);
    }

    @Test
    void depositsReturnsHistoryForTheGoal() {
        SavingDeposit d = SavingDeposit.builder().savingGoal(goal)
            .amount(new BigDecimal("40.00")).note("allowance")
            .depositDate(java.time.LocalDate.now()).build();
        ReflectionTestUtils.setField(d, "id", UUID.randomUUID());
        when(savingDepositRepository.findBySavingGoalIdOrderByDepositDateDescCreatedAtDesc(GOAL_ID))
            .thenReturn(List.of(d));

        assertThat(service.deposits(GOAL_ID)).singleElement()
            .satisfies(r -> assertThat(r.amount()).isEqualByComparingTo("40.00"));
    }
}

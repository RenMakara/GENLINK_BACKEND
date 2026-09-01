package kh.edu.acleda.campuslink.modules.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.bff.service.MobileHomeBffServiceImpl;
import kh.edu.acleda.campuslink.modules.community.repository.CommunityMemberRepository;
import kh.edu.acleda.campuslink.modules.community.repository.CommunityRepository;
import kh.edu.acleda.campuslink.modules.community.repository.EventRsvpRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PostRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.DailyChallengeRepository;
import kh.edu.acleda.campuslink.modules.finance.entity.Budget;
import kh.edu.acleda.campuslink.modules.finance.entity.SavingGoal;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus;
import kh.edu.acleda.campuslink.modules.finance.repository.BudgetRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.SavingGoalRepository;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileHomeResponse;
import kh.edu.acleda.campuslink.modules.productivity.repository.HabitCheckInRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.HabitRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.MotivationContentRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.TaskRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MobileHomeBffServiceImplTest {

    private static final String EMAIL = "sok@example.com";
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private UserRepository userRepository;
    @Mock private HabitRepository habitRepository;
    @Mock private HabitCheckInRepository habitCheckInRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private MotivationContentRepository motivationContentRepository;
    @Mock private DailyChallengeRepository dailyChallengeRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private SavingGoalRepository savingGoalRepository;
    @Mock private EventRsvpRepository eventRsvpRepository;
    @Mock private CommunityRepository communityRepository;
    @Mock private CommunityMemberRepository communityMemberRepository;
    @Mock private PostRepository postRepository;

    private MobileHomeBffServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new MobileHomeBffServiceImpl(userRepository, habitRepository, habitCheckInRepository,
            taskRepository, motivationContentRepository, dailyChallengeRepository, budgetRepository,
            expenseRepository, savingGoalRepository, eventRsvpRepository, communityRepository,
            communityMemberRepository, postRepository);
        user = new User();
        user.setEmail(EMAIL);
        user.setFullName("Sok");
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", USER_ID);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @Test
    void homeFinanceSnapshotUsesOnlyTheRequestedCurrency() {
        when(budgetRepository.findByUserIdAndMonthAndYearAndCurrencyAndDeletedAtIsNull(
            eq(USER_ID), any(), any(), eq(Currency.KHR)))
            .thenReturn(Optional.of(Budget.builder()
                .currency(Currency.KHR).totalBudget(new BigDecimal("100000")).build()));
        when(expenseRepository.sumForMonthByCurrency(eq(USER_ID), eq(Currency.KHR), any(), any()))
            .thenReturn(new BigDecimal("25000"));
        when(savingGoalRepository.findByUser(eq(USER_ID), eq(SavingGoalStatus.IN_PROGRESS), eq(Currency.KHR)))
            .thenReturn(List.of(SavingGoal.builder().currency(Currency.KHR)
                .targetAmount(new BigDecimal("50000")).currentAmount(new BigDecimal("10000")).build()));

        MobileHomeResponse response = service.getHome(EMAIL, Language.ENGLISH, Currency.KHR);

        MobileHomeResponse.FinanceSnapshot snapshot = response.financeSnapshot();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.currency()).isEqualTo(Currency.KHR);
        assertThat(snapshot.totalBudget()).isEqualByComparingTo("100000");
        assertThat(snapshot.spentAmount()).isEqualByComparingTo("25000");
        assertThat(snapshot.savingTarget()).isEqualByComparingTo("50000");
        verify(budgetRepository).findByUserIdAndMonthAndYearAndCurrencyAndDeletedAtIsNull(
            eq(USER_ID), any(), any(), eq(Currency.KHR));
        verify(savingGoalRepository).findByUser(USER_ID, SavingGoalStatus.IN_PROGRESS, Currency.KHR);
    }
}

package kh.edu.acleda.campuslink.modules.finance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.BudgetCategoryRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.request.BudgetCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.BudgetResponse;
import kh.edu.acleda.campuslink.modules.finance.entity.Budget;
import kh.edu.acleda.campuslink.modules.finance.entity.BudgetCategory;
import kh.edu.acleda.campuslink.modules.finance.enums.BudgetStatus;
import kh.edu.acleda.campuslink.modules.finance.repository.BudgetCategoryRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.BudgetRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.finance.service.impl.BudgetServiceImpl;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID BUDGET_ID = UUID.randomUUID();

    @Mock private BudgetRepository budgetRepository;
    @Mock private BudgetCategoryRepository budgetCategoryRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private BudgetServiceImpl service;
    private User user;
    private Budget budget;

    @BeforeEach
    void setUp() {
        service = new BudgetServiceImpl(budgetRepository, budgetCategoryRepository, expenseRepository,
            userRepository, currentUserProvider);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);

        budget = Budget.builder().user(user).month(7).year(2026)
            .totalBudget(new BigDecimal("300.00")).spentAmount(BigDecimal.ZERO)
            .remainingAmount(new BigDecimal("300.00")).status(BudgetStatus.SAFE).build();
        ReflectionTestUtils.setField(budget, "id", BUDGET_ID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(budgetRepository.save(any(Budget.class))).thenAnswer(i -> {
            Budget b = i.getArgument(0);
            if (b.getId() == null) {
                ReflectionTestUtils.setField(b, "id", BUDGET_ID);
            }
            return b;
        });
        when(budgetRepository.findByIdAndUserIdAndDeletedAtIsNull(BUDGET_ID, USER_ID))
            .thenReturn(Optional.of(budget));
        when(expenseRepository.sumForMonthByCurrency(any(), any(), any(), any())).thenReturn(null);
        when(expenseRepository.sumByCategoryAndCurrency(any(), any(), any(), any())).thenReturn(List.of());
        when(budgetCategoryRepository.findByBudgetId(BUDGET_ID)).thenReturn(List.of());
    }

    @Test
    void createRejectsADuplicateBudgetForTheMonth() {
        when(budgetRepository.existsByUserIdAndMonthAndYearAndDeletedAtIsNull(USER_ID, 7, 2026)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new BudgetCreateRequest(7, 2026, new BigDecimal("300"), null, null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void createPersistsBudgetAndCategories() {
        service.create(new BudgetCreateRequest(7, 2026, new BigDecimal("300.00"),
            List.of(new BudgetCategoryRequest("FOOD", new BigDecimal("100.00"))), null));

        verify(budgetRepository).save(any(Budget.class));
        verify(budgetCategoryRepository).save(any(BudgetCategory.class));
    }

    @Test
    void createRejectsAnInvalidMonth() {
        assertThatThrownBy(() -> service.create(new BudgetCreateRequest(0, 2026, new BigDecimal("300"), null, null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("between 1 and 12");
    }

    /** Spend comes from expenses, not the stored counter. */
    @Test
    void spendAndRemainingAreComputedLiveFromExpenses() {
        when(expenseRepository.sumForMonthByCurrency(eq(USER_ID), any(), any(), any())).thenReturn(new BigDecimal("120.00"));

        BudgetResponse response = service.getById(BUDGET_ID);

        assertThat(response.spentAmount()).isEqualByComparingTo("120.00");
        assertThat(response.remainingAmount()).isEqualByComparingTo("180.00");
        assertThat(response.status()).isEqualTo(BudgetStatus.SAFE);
    }

    @Test
    void statusIsWarningAtEightyPercent() {
        when(expenseRepository.sumForMonthByCurrency(any(), any(), any(), any())).thenReturn(new BigDecimal("240.00"));

        assertThat(service.getById(BUDGET_ID).status()).isEqualTo(BudgetStatus.WARNING);
    }

    @Test
    void statusIsLimitReachedAtOrOverTotal() {
        when(expenseRepository.sumForMonthByCurrency(any(), any(), any(), any())).thenReturn(new BigDecimal("300.00"));

        assertThat(service.getById(BUDGET_ID).status()).isEqualTo(BudgetStatus.LIMIT_REACHED);
    }

    @Test
    void categoryBreakdownSubtractsSpentFromAllocated() {
        when(budgetCategoryRepository.findByBudgetId(BUDGET_ID)).thenReturn(List.of(
            BudgetCategory.builder().budget(budget).category("FOOD")
                .allocatedAmount(new BigDecimal("100.00")).build()));
        when(expenseRepository.sumByCategoryAndCurrency(eq(USER_ID), any(), any(), any())).thenReturn(List.<Object[]>of(
            new Object[]{"FOOD", new BigDecimal("30.00")}));

        BudgetResponse.CategoryBudget cat = service.getById(BUDGET_ID).categories().get(0);

        assertThat(cat.spentAmount()).isEqualByComparingTo("30.00");
        assertThat(cat.remainingAmount()).isEqualByComparingTo("70.00");
    }

    /** The list omits the category breakdown, so it does not query per-category spend. */
    @Test
    void listDoesNotComputeCategoryBreakdown() {
        when(budgetRepository.findByUserIdAndDeletedAtIsNull(eq(USER_ID), any()))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(budget)));

        service.getMine(org.springframework.data.domain.PageRequest.of(0, 20));

        verify(expenseRepository, never()).sumByCategoryAndCurrency(any(), any(), any(), any());
    }

    @Test
    void updatingSomeoneElsesBudgetReadsAsNotFound() {
        when(budgetRepository.findByIdAndUserIdAndDeletedAtIsNull(BUDGET_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(BUDGET_ID, new BudgetCreateRequest(7, 2026, new BigDecimal("1"), null, null)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateReplacesCategories() {
        service.update(BUDGET_ID, new BudgetCreateRequest(7, 2026, new BigDecimal("400.00"),
            List.of(new BudgetCategoryRequest("TRANSPORT", new BigDecimal("50.00"))), null));

        verify(budgetCategoryRepository).deleteByBudgetId(BUDGET_ID);
        verify(budgetCategoryRepository).save(any(BudgetCategory.class));
    }
}

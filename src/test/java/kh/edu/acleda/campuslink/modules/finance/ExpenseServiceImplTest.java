package kh.edu.acleda.campuslink.modules.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.ExpenseCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.MonthlySummaryResponse;
import kh.edu.acleda.campuslink.modules.finance.entity.Expense;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.finance.service.impl.ExpenseServiceImpl;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpenseServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EXPENSE_ID = UUID.randomUUID();

    @Mock private ExpenseRepository expenseRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private ExpenseServiceImpl service;
    private User user;
    private Expense expense;

    @BeforeEach
    void setUp() {
        service = new ExpenseServiceImpl(expenseRepository, userRepository, currentUserProvider);
        user = new User();
        user.setEmail("user@example.com");
        ReflectionTestUtils.setField(user, "id", USER_ID);

        expense = Expense.builder().user(user).title("Coffee").amount(new BigDecimal("2.50"))
            .category("FOOD").expenseDate(LocalDate.now()).build();
        ReflectionTestUtils.setField(expense, "id", EXPENSE_ID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(i -> {
            Expense e = i.getArgument(0);
            if (e.getId() == null) {
                ReflectionTestUtils.setField(e, "id", EXPENSE_ID);
            }
            return e;
        });
    }

    @Test
    void createPersistsTheExpense() {
        var response = service.create(new ExpenseCreateRequest(
            "Coffee", new BigDecimal("2.50"), "FOOD", "Morning", LocalDate.now(), "CASH"));

        verify(expenseRepository).save(any(Expense.class));
        assertThat(response.title()).isEqualTo("Coffee");
        assertThat(response.amount()).isEqualByComparingTo("2.50");
    }

    /** month + year together route to the date-range query. */
    @Test
    void listWithMonthAndYearUsesTheDateRangeQuery() {
        when(expenseRepository.findByUserAndDateRange(eq(USER_ID), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(expense)));

        service.getMine(null, 3, 2026, PageRequest.of(0, 20));

        verify(expenseRepository).findByUserAndDateRange(eq(USER_ID), isNull(),
            eq(LocalDate.of(2026, 3, 1)), eq(LocalDate.of(2026, 4, 1)), any());
        verify(expenseRepository, never()).findByUser(any(), any(), any());
    }

    /** month without year cannot bound a range, so it falls back to the plain list. */
    @Test
    void listWithMonthButNoYearFallsBackToPlainList() {
        when(expenseRepository.findByUser(eq(USER_ID), any(), any()))
            .thenReturn(new PageImpl<>(List.of(expense)));

        service.getMine("FOOD", 3, null, PageRequest.of(0, 20));

        verify(expenseRepository).findByUser(eq(USER_ID), eq("FOOD"), any());
        verify(expenseRepository, never()).findByUserAndDateRange(any(), any(), any(), any(), any());
    }

    @Test
    void summaryMapsCategoriesAndReportsNullAsUncategorized() {
        when(expenseRepository.sumByCategory(eq(USER_ID), any(), any())).thenReturn(List.of(
            new Object[]{"FOOD", new BigDecimal("10.00")},
            new Object[]{null, new BigDecimal("5.00")}));
        when(expenseRepository.sumForMonth(eq(USER_ID), any(), any())).thenReturn(new BigDecimal("15.00"));

        MonthlySummaryResponse summary = service.monthlySummary(3, 2026);

        assertThat(summary.total()).isEqualByComparingTo("15.00");
        assertThat(summary.byCategory()).hasSize(2);
        assertThat(summary.byCategory().get(1).category()).isEqualTo("UNCATEGORIZED");
    }

    /** A month with no expenses returns zero, not null. */
    @Test
    void summaryReturnsZeroWhenThereAreNoExpenses() {
        when(expenseRepository.sumByCategory(any(), any(), any())).thenReturn(List.of());
        when(expenseRepository.sumForMonth(any(), any(), any())).thenReturn(null);

        assertThat(service.monthlySummary(3, 2026).total()).isEqualByComparingTo("0");
    }

    @Test
    void summaryRejectsAnInvalidMonth() {
        assertThatThrownBy(() -> service.monthlySummary(13, 2026))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("between 1 and 12");
    }

    @Test
    void deleteIsASoftDelete() {
        when(expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(EXPENSE_ID, USER_ID))
            .thenReturn(Optional.of(expense));

        service.delete(EXPENSE_ID);

        assertThat(expense.getDeletedAt()).isNotNull();
    }

    @Test
    void updatingSomeoneElsesExpenseReadsAsNotFound() {
        when(expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(EXPENSE_ID, USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(EXPENSE_ID, new ExpenseCreateRequest(
            "Hack", new BigDecimal("1.00"), null, null, LocalDate.now(), null)))
            .isInstanceOf(NotFoundException.class);
    }
}

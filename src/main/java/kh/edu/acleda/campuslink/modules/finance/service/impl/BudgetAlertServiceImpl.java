package kh.edu.acleda.campuslink.modules.finance.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.Budget;
import kh.edu.acleda.campuslink.modules.finance.entity.Expense;
import kh.edu.acleda.campuslink.modules.finance.enums.BudgetStatus;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.repository.BudgetRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.finance.service.BudgetAlertService;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class BudgetAlertServiceImpl implements BudgetAlertService {

    private static final BigDecimal WARNING_THRESHOLD = new BigDecimal("0.80");
    private static final String NOTIFICATION_TYPE = "FINANCE_BUDGET";
    private static final String APPROACHING_TEMPLATE = "FINANCE_BUDGET_APPROACHING";
    private static final String EXCEEDED_TEMPLATE = "FINANCE_BUDGET_EXCEEDED";

    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;
    private final NotificationEmitter notificationEmitter;

    public BudgetAlertServiceImpl(BudgetRepository budgetRepository,
                                  ExpenseRepository expenseRepository,
                                  NotificationEmitter notificationEmitter) {
        this.budgetRepository = budgetRepository;
        this.expenseRepository = expenseRepository;
        this.notificationEmitter = notificationEmitter;
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetStatus statusBeforeExpenseMutation(UUID userId, LocalDate expenseDate, Currency currency) {
        if (userId == null || expenseDate == null || currency == null) {
            return null;
        }
        return findBudget(userId, expenseDate, currency)
            .map(budget -> statusFor(currentSpend(userId, expenseDate, currency), budget.getTotalBudget()))
            .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public void evaluateAfterExpenseMutation(Expense expense, BudgetStatus statusBefore) {
        if (expense == null || expense.getId() == null || expense.getUser() == null
            || expense.getUser().getId() == null || expense.getExpenseDate() == null
            || expense.getCurrency() == null) {
            log.warn("Skipping budget alert because expense details are incomplete: expense={}",
                expense == null ? null : expense.getId());
            return;
        }

        UUID userId = expense.getUser().getId();
        LocalDate expenseDate = expense.getExpenseDate();
        Currency currency = expense.getCurrency();
        Optional<Budget> budget = findBudget(userId, expenseDate, currency);
        if (budget.isEmpty() || budget.get().getId() == null) {
            return;
        }

        Budget currentBudget = budget.get();
        BigDecimal spentAfter = currentSpend(userId, expenseDate, currency);
        BudgetStatus statusAfter = statusFor(spentAfter, currentBudget.getTotalBudget());

        if (statusAfter == BudgetStatus.LIMIT_REACHED && statusBefore != BudgetStatus.LIMIT_REACHED) {
            emitExceeded(userId, currentBudget);
        } else if (statusAfter == BudgetStatus.WARNING && !isWarningOrLimit(statusBefore)) {
            emitApproaching(userId, currentBudget, spentAfter);
        }
    }

    private Optional<Budget> findBudget(UUID userId, LocalDate expenseDate, Currency currency) {
        return budgetRepository.findByUserIdAndMonthAndYearAndCurrencyAndDeletedAtIsNull(
            userId, expenseDate.getMonthValue(), expenseDate.getYear(), currency);
    }

    private BigDecimal currentSpend(UUID userId, LocalDate expenseDate, Currency currency) {
        LocalDate start = expenseDate.withDayOfMonth(1);
        LocalDate end = start.plusMonths(1);
        BigDecimal total = expenseRepository.sumForMonthByCurrency(userId, currency, start, end);
        return total == null ? BigDecimal.ZERO : total;
    }

    private BudgetStatus statusFor(BigDecimal spent, BigDecimal total) {
        if (total == null || total.signum() <= 0 || spent.compareTo(total) >= 0) {
            return BudgetStatus.LIMIT_REACHED;
        }
        if (spent.compareTo(total.multiply(WARNING_THRESHOLD)) >= 0) {
            return BudgetStatus.WARNING;
        }
        return BudgetStatus.SAFE;
    }

    private boolean isWarningOrLimit(BudgetStatus status) {
        return status == BudgetStatus.WARNING || status == BudgetStatus.LIMIT_REACHED;
    }

    private void emitApproaching(UUID userId,
                                 Budget budget,
                                 BigDecimal spent) {
        String percent = spent.multiply(BigDecimal.valueOf(100))
            .divide(budget.getTotalBudget(), 2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString();
        notificationEmitter.emit(new NotificationRequest(
            userId,
            APPROACHING_TEMPLATE,
            NOTIFICATION_TYPE,
            Map.of("budgetName", budgetName(budget), "percent", percent),
            "BUDGET",
            budget.getId(),
            "budget-approaching:" + budget.getId()));
    }

    private void emitExceeded(UUID userId, Budget budget) {
        notificationEmitter.emit(new NotificationRequest(
            userId,
            EXCEEDED_TEMPLATE,
            NOTIFICATION_TYPE,
            Map.of("budgetName", budgetName(budget)),
            "BUDGET",
            budget.getId(),
            "budget-exceeded:" + budget.getId()));
    }

    private String budgetName(Budget budget) {
        String month = Month.of(budget.getMonth()).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        return month + " " + budget.getYear() + " budget (" + budget.getCurrency().name() + ")";
    }
}

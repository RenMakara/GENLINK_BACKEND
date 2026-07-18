package kh.edu.acleda.campuslink.modules.finance.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import kh.edu.acleda.campuslink.modules.finance.service.BudgetService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetServiceImpl implements BudgetService {

    /** Spend at or above this fraction of the total flips the status to WARNING. */
    private static final BigDecimal WARNING_THRESHOLD = new BigDecimal("0.80");

    private final BudgetRepository budgetRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public BudgetServiceImpl(BudgetRepository budgetRepository,
                             BudgetCategoryRepository budgetCategoryRepository,
                             ExpenseRepository expenseRepository,
                             UserRepository userRepository,
                             CurrentUserProvider currentUserProvider) {
        this.budgetRepository = budgetRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public BudgetResponse create(BudgetCreateRequest request) {
        validateMonth(request.month());
        User user = loadCurrentUser();
        UUID userId = user.getId();

        // UNIQUE(user, month, year): give a clear error rather than a constraint 500.
        if (budgetRepository.existsByUserIdAndMonthAndYearAndDeletedAtIsNull(
                userId, request.month(), request.year())) {
            throw new ApiException("A budget already exists for this month", "BUDGET_ALREADY_EXISTS");
        }

        Budget budget = budgetRepository.save(Budget.builder()
            .user(user)
            .month(request.month())
            .year(request.year())
            .totalBudget(request.totalBudget())
            // Stored counters satisfy the NOT NULL columns but are not read back -
            // spend is recomputed from expenses on every read, so it cannot drift.
            .spentAmount(BigDecimal.ZERO)
            .remainingAmount(request.totalBudget())
            .status(BudgetStatus.SAFE)
            .build());

        saveCategories(budget, request.categories());
        return toResponse(budget, true);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BudgetResponse> getMine(Pageable pageable) {
        UUID userId = currentUserProvider.getCurrentUserId();
        return PageResponse.from(budgetRepository.findByUserIdAndDeletedAtIsNull(userId, pageable),
            budget -> toResponse(budget, false));
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetResponse getById(UUID id) {
        return toResponse(loadOwned(id), true);
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetResponse getForMonth(int month, int year) {
        validateMonth(month);
        Budget budget = budgetRepository
            .findByUserIdAndMonthAndYearAndDeletedAtIsNull(currentUserProvider.getCurrentUserId(), month, year)
            .orElseThrow(() -> new NotFoundException("No budget set for this month"));
        return toResponse(budget, true);
    }

    @Override
    @Transactional
    public BudgetResponse update(UUID id, BudgetCreateRequest request) {
        validateMonth(request.month());
        Budget budget = loadOwned(id);
        budget.setMonth(request.month());
        budget.setYear(request.year());
        budget.setTotalBudget(request.totalBudget());
        budgetRepository.save(budget);

        // Categories are replaced wholesale, matching the full-object edit from the UI.
        budgetCategoryRepository.deleteByBudgetId(budget.getId());
        budgetCategoryRepository.flush();
        saveCategories(budget, request.categories());
        return toResponse(budget, true);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Budget budget = loadOwned(id);
        budget.setDeletedAt(Instant.now());
        budgetRepository.save(budget);
    }

    private void saveCategories(Budget budget, List<BudgetCategoryRequest> categories) {
        if (categories == null || categories.isEmpty()) {
            return;
        }
        for (BudgetCategoryRequest c : categories) {
            budgetCategoryRepository.save(BudgetCategory.builder()
                .budget(budget)
                .category(c.category())
                .allocatedAmount(c.allocatedAmount())
                .spentAmount(BigDecimal.ZERO)
                .remainingAmount(c.allocatedAmount())
                .build());
        }
    }

    /**
     * Builds the response with spend computed live from expenses for the budget's
     * month. withCategories is false for the list (one grouped query per budget is
     * enough there) and true for detail views.
     */
    private BudgetResponse toResponse(Budget budget, boolean withCategories) {
        UUID userId = budget.getUser().getId();
        LocalDate start = LocalDate.of(budget.getYear(), budget.getMonth(), 1);
        LocalDate end = start.plusMonths(1);

        BigDecimal spent = orZero(expenseRepository.sumForMonth(userId, start, end));
        BigDecimal remaining = budget.getTotalBudget().subtract(spent);
        BudgetStatus status = statusFor(spent, budget.getTotalBudget());

        List<BudgetResponse.CategoryBudget> categories = List.of();
        if (withCategories) {
            categories = categoryBreakdown(budget.getId(), userId, start, end);
        }
        return new BudgetResponse(budget.getId(), budget.getMonth(), budget.getYear(),
            budget.getTotalBudget(), spent, remaining, status, categories);
    }

    private List<BudgetResponse.CategoryBudget> categoryBreakdown(UUID budgetId, UUID userId,
                                                                  LocalDate start, LocalDate end) {
        Map<String, BigDecimal> spentByCategory = new HashMap<>();
        for (Object[] row : expenseRepository.sumByCategory(userId, start, end)) {
            if (row[0] != null) {
                spentByCategory.put((String) row[0], (BigDecimal) row[1]);
            }
        }
        return budgetCategoryRepository.findByBudgetId(budgetId).stream()
            .map(bc -> {
                BigDecimal spent = orZero(spentByCategory.get(bc.getCategory()));
                return new BudgetResponse.CategoryBudget(
                    bc.getCategory(), bc.getAllocatedAmount(), spent,
                    bc.getAllocatedAmount().subtract(spent));
            })
            .toList();
    }

    private BudgetStatus statusFor(BigDecimal spent, BigDecimal total) {
        if (total.signum() <= 0 || spent.compareTo(total) >= 0) {
            return BudgetStatus.LIMIT_REACHED;
        }
        if (spent.compareTo(total.multiply(WARNING_THRESHOLD)) >= 0) {
            return BudgetStatus.WARNING;
        }
        return BudgetStatus.SAFE;
    }

    private void validateMonth(int month) {
        if (month < 1 || month > 12) {
            throw new ApiException("Month must be between 1 and 12", "VALIDATION_ERROR");
        }
    }

    private Budget loadOwned(UUID id) {
        return budgetRepository.findByIdAndUserIdAndDeletedAtIsNull(id, currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Budget not found"));
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

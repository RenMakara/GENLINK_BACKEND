package kh.edu.acleda.campuslink.modules.bff.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.entity.Budget;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.entity.Expense;
import kh.edu.acleda.campuslink.modules.finance.entity.SavingGoal;
import kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus;
import kh.edu.acleda.campuslink.modules.finance.repository.BudgetRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoWalletRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.SavingGoalRepository;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileFinanceDashboardResponse;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileFinanceDashboardResponse.CategorySpend;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileFinanceDashboardResponse.MonthOverview;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileFinanceDashboardResponse.RecentExpense;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileFinanceDashboardResponse.SavingProgress;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileFinanceDashboardResponse.WalletSummary;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates the Finance screen. Reads across the finance repositories directly - the
 * BFF layer is exactly where cross-entity coupling belongs, so it does not leak into
 * the finance services (which are current-user scoped and would not fit the by-email
 * BFF entry point).
 *
 * <p>Spend is computed live from expenses, never read from Budget.spentAmount: that
 * cached column is set at create and never maintained (spend is derived on read), so
 * reading it would always show 0.
 */
@Service
public class MobileFinanceBffServiceImpl implements MobileFinanceBffService {

    private static final BigDecimal WARNING_THRESHOLD = new BigDecimal("0.80");
    private static final int TOP_CATEGORIES = 5;
    private static final int RECENT_EXPENSES = 5;
    private static final String UNCATEGORIZED = "UNCATEGORIZED";

    private static final List<String> QUICK_ACTIONS =
        List.of("ADD_EXPENSE", "SET_BUDGET", "NEW_SAVING_GOAL", "TOP_UP_WALLET");

    // English only for now. A bilingual finance-tip seed is a follow-up (see FINANCE_PLAN).
    private static final List<String> TIPS = List.of(
        "Track every expense for a week - awareness alone cuts spending.",
        "Pay yourself first: move money to savings before you spend.",
        "A budget is a plan for your money, not a punishment.",
        "Small daily coffees add up - review your top category this month.",
        "Set one specific saving goal; a target you can see is easier to hit.",
        "Keep one month of expenses as an emergency buffer.",
        "Review subscriptions monthly and cancel what you no longer use.");

    private final UserRepository userRepository;
    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;
    private final SavingGoalRepository savingGoalRepository;
    private final DemoWalletRepository demoWalletRepository;

    public MobileFinanceBffServiceImpl(UserRepository userRepository,
                                       BudgetRepository budgetRepository,
                                       ExpenseRepository expenseRepository,
                                       SavingGoalRepository savingGoalRepository,
                                       DemoWalletRepository demoWalletRepository) {
        this.userRepository = userRepository;
        this.budgetRepository = budgetRepository;
        this.expenseRepository = expenseRepository;
        this.savingGoalRepository = savingGoalRepository;
        this.demoWalletRepository = demoWalletRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public MobileFinanceDashboardResponse getFinanceDashboard(String email, Currency currency) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new NotFoundException("User not found"));
        Currency selectedCurrency = currency == null ? Currency.USD : currency;
        UUID userId = user.getId();
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1);

        Budget budget = budgetRepository
            .findByUserIdAndMonthAndYearAndCurrencyAndDeletedAtIsNull(
                userId, today.getMonthValue(), today.getYear(), selectedCurrency)
            .orElse(null);
        BigDecimal spent = orZero(expenseRepository.sumForMonthByCurrency(
            userId, selectedCurrency, monthStart, monthEnd));

        return new MobileFinanceDashboardResponse(
            selectedCurrency,
            monthOverview(today, selectedCurrency, budget, spent),
            topCategories(userId, selectedCurrency, monthStart, monthEnd),
            savingProgress(userId, selectedCurrency),
            recentExpenses(userId, selectedCurrency),
            walletSummary(userId),
            TIPS.get(today.getDayOfYear() % TIPS.size()),
            QUICK_ACTIONS
        );
    }

    private MonthOverview monthOverview(LocalDate today, Currency currency, Budget budget, BigDecimal spent) {
        if (budget == null) {
            return new MonthOverview(today.getMonthValue(), today.getYear(), currency,
                spent, null, null, null);
        }
        BigDecimal total = budget.getTotalBudget();
        return new MonthOverview(today.getMonthValue(), today.getYear(), currency, spent,
            total, total.subtract(spent), statusFor(spent, total));
    }

    private List<CategorySpend> topCategories(UUID userId, Currency currency, LocalDate start, LocalDate end) {
        return expenseRepository.sumByCategoryAndCurrency(userId, currency, start, end).stream()
            .limit(TOP_CATEGORIES)
            .map(row -> new CategorySpend(
                row[0] == null ? UNCATEGORIZED : (String) row[0], currency, (BigDecimal) row[1]))
            .toList();
    }

    private SavingProgress savingProgress(UUID userId, Currency currency) {
        List<SavingGoal> goals = savingGoalRepository
            .findByUser(userId, SavingGoalStatus.IN_PROGRESS, currency);
        BigDecimal target = goals.stream().map(SavingGoal::getTargetAmount)
            .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saved = goals.stream().map(SavingGoal::getCurrentAmount)
            .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SavingProgress(currency, target, saved, percent(saved, target), goals.size());
    }

    private List<RecentExpense> recentExpenses(UUID userId, Currency currency) {
        PageRequest recent = PageRequest.of(0, RECENT_EXPENSES, Sort.by(Sort.Direction.DESC, "expenseDate"));
        return expenseRepository.findByUser(userId, null, currency, recent).getContent().stream()
            .map(this::toRecentExpense)
            .toList();
    }

    private RecentExpense toRecentExpense(Expense e) {
        return new RecentExpense(e.getId(), e.getTitle(), e.getCategory(), e.getCurrency(),
            e.getAmount(), e.getExpenseDate());
    }

    private WalletSummary walletSummary(UUID userId) {
        DemoWallet wallet = demoWalletRepository.findByUserId(userId).orElse(null);
        return wallet == null ? null : new WalletSummary(wallet.getBalanceUsd(), wallet.getBalanceKhr());
    }

    private String statusFor(BigDecimal spent, BigDecimal total) {
        if (total.signum() <= 0 || spent.compareTo(total) >= 0) {
            return "LIMIT_REACHED";
        }
        if (spent.compareTo(total.multiply(WARNING_THRESHOLD)) >= 0) {
            return "WARNING";
        }
        return "SAFE";
    }

    private int percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() <= 0) {
            return 0;
        }
        return part.multiply(BigDecimal.valueOf(100))
            .divide(whole, 0, RoundingMode.DOWN)
            .min(BigDecimal.valueOf(100))
            .intValue();
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

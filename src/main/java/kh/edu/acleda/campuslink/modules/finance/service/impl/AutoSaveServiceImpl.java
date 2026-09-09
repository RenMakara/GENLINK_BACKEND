package kh.edu.acleda.campuslink.modules.finance.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.AutoSaveRuleCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.AutoSaveRuleResponse;
import kh.edu.acleda.campuslink.modules.finance.entity.AutoSaveRule;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.entity.SavingDeposit;
import kh.edu.acleda.campuslink.modules.finance.entity.SavingGoal;
import kh.edu.acleda.campuslink.modules.finance.enums.AutoSaveType;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.repository.AutoSaveRuleRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.IncomeRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.SavingDepositRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.SavingGoalRepository;
import kh.edu.acleda.campuslink.modules.finance.service.AutoSaveService;
import kh.edu.acleda.campuslink.modules.finance.service.DemoWalletService;
import kh.edu.acleda.campuslink.modules.finance.service.LedgerService;
import kh.edu.acleda.campuslink.modules.finance.service.SavingGoalCompletionNotifier;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutoSaveServiceImpl implements AutoSaveService {

    private static final Logger log = LoggerFactory.getLogger(AutoSaveServiceImpl.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AutoSaveRuleRepository autoSaveRuleRepository;
    private final SavingGoalRepository savingGoalRepository;
    private final SavingDepositRepository savingDepositRepository;
    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final DemoWalletService demoWalletService;
    private final LedgerService ledgerService;
    private final SavingGoalCompletionNotifier savingGoalCompletionNotifier;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public AutoSaveServiceImpl(AutoSaveRuleRepository autoSaveRuleRepository,
                               SavingGoalRepository savingGoalRepository,
                               SavingDepositRepository savingDepositRepository,
                               IncomeRepository incomeRepository,
                               ExpenseRepository expenseRepository,
                               DemoWalletService demoWalletService,
                               LedgerService ledgerService,
                               SavingGoalCompletionNotifier savingGoalCompletionNotifier,
                               UserRepository userRepository,
                               CurrentUserProvider currentUserProvider) {
        this.autoSaveRuleRepository = autoSaveRuleRepository;
        this.savingGoalRepository = savingGoalRepository;
        this.savingDepositRepository = savingDepositRepository;
        this.incomeRepository = incomeRepository;
        this.expenseRepository = expenseRepository;
        this.demoWalletService = demoWalletService;
        this.ledgerService = ledgerService;
        this.savingGoalCompletionNotifier = savingGoalCompletionNotifier;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public AutoSaveRuleResponse create(AutoSaveRuleCreateRequest request) {
        User user = loadCurrentUser();
        SavingGoal goal = savingGoalRepository
            .findByIdAndUserIdAndDeletedAtIsNull(request.savingGoalId(), user.getId())
            .orElseThrow(() -> new NotFoundException("Saving goal not found"));

        Currency currency = request.currency() == null ? Currency.USD : request.currency();
        if (currency != goal.getCurrency()) {
            throw new ApiException("Auto-save currency must match the goal's currency", "CURRENCY_MISMATCH");
        }
        BigDecimal percent = null;
        if (request.type() == AutoSaveType.PERCENT_OF_INCOME) {
            percent = request.percent();
            if (percent == null || percent.signum() <= 0 || percent.compareTo(HUNDRED) > 0) {
                throw new ApiException("percent must be between 0 and 100 for PERCENT_OF_INCOME",
                    "VALIDATION_ERROR");
            }
        }

        AutoSaveRule rule = autoSaveRuleRepository.save(AutoSaveRule.builder()
            .user(user)
            .savingGoal(goal)
            .type(request.type())
            .percent(percent)
            .currency(currency)
            // Runs monthly; the first run is the 1st of next month, covering this month's activity.
            .nextRunDate(LocalDate.now().withDayOfMonth(1).plusMonths(1))
            .active(true)
            .build());
        return toResponse(rule);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AutoSaveRuleResponse> getMine() {
        return autoSaveRuleRepository
            .findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(currentUserProvider.getCurrentUserId())
            .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        AutoSaveRule rule = autoSaveRuleRepository
            .findByIdAndUserIdAndDeletedAtIsNull(id, currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Auto-save rule not found"));
        rule.setDeletedAt(Instant.now());
        rule.setActive(false);
        autoSaveRuleRepository.save(rule);
    }

    @Override
    @Transactional
    public int processDue(LocalDate asOf) {
        int applied = 0;
        for (AutoSaveRule rule : autoSaveRuleRepository.findDue(asOf)) {
            if (applyCatchUp(rule, asOf)) {
                applied++;
            }
        }
        return applied;
    }

    /** Run the rule for each due month up to {@code asOf}; true if any run moved money. */
    private boolean applyCatchUp(AutoSaveRule rule, LocalDate asOf) {
        boolean moved = false;
        while (rule.isActive() && !rule.getNextRunDate().isAfter(asOf)) {
            LocalDate runDate = rule.getNextRunDate();
            BigDecimal amount = amountFor(rule, runDate);
            if (amount.signum() > 0 && applyDeposit(rule, amount, runDate)) {
                moved = true;
            }
            rule.setLastRunDate(runDate);
            rule.setNextRunDate(runDate.plusMonths(1));
        }
        autoSaveRuleRepository.save(rule);
        return moved;
    }

    /** How much the rule saves for the calendar month immediately before {@code runDate}. */
    private BigDecimal amountFor(AutoSaveRule rule, LocalDate runDate) {
        LocalDate windowStart = runDate.withDayOfMonth(1).minusMonths(1);
        LocalDate windowEnd = runDate.withDayOfMonth(1);
        UUID userId = rule.getUser().getId();

        if (rule.getType() == AutoSaveType.PERCENT_OF_INCOME) {
            BigDecimal income = orZero(
                incomeRepository.sumForMonthByCurrency(userId, rule.getCurrency(), windowStart, windowEnd));
            return income.multiply(rule.getPercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        }
        // ROUND_UP: sum the spare change from rounding each expense up to the next whole unit.
        BigDecimal roundUp = BigDecimal.ZERO;
        for (BigDecimal amount : expenseRepository
                .findAmountsForMonthByCurrency(userId, rule.getCurrency(), windowStart, windowEnd)) {
            roundUp = roundUp.add(amount.setScale(0, RoundingMode.CEILING).subtract(amount));
        }
        return roundUp.setScale(2, RoundingMode.HALF_UP);
    }

    /** Move {@code amount} from the wallet into the goal, if the goal is live and funds are there. */
    private boolean applyDeposit(AutoSaveRule rule, BigDecimal amount, LocalDate runDate) {
        SavingGoal goal = rule.getSavingGoal();
        if (goal == null || goal.getDeletedAt() != null
            || goal.getStatus() == SavingGoalStatus.CANCELLED) {
            rule.setActive(false); // the goal is gone - retire the rule
            return false;
        }
        if (goal.getStatus() == SavingGoalStatus.COMPLETED) {
            rule.setActive(false); // nothing left to save toward
            return false;
        }
        SavingGoalStatus statusBefore = goal.getStatus();

        DemoWallet wallet = demoWalletService.getOrCreateWalletEntity(rule.getUser());
        BigDecimal balance = rule.getCurrency() == Currency.KHR ? wallet.getBalanceKhr() : wallet.getBalanceUsd();
        if (balance == null || balance.compareTo(amount) < 0) {
            // Funds may arrive next month; skip this run without retiring the rule.
            log.warn("Auto-save rule {} skipped (insufficient {} funds): user={} needed={}",
                rule.getId(), rule.getCurrency(), rule.getUser().getId(), amount);
            return false;
        }

        // Balance pre-checked, so this debit will not throw and poison the batch transaction.
        ledgerService.debit(wallet, TransactionType.SAVING, rule.getCurrency(), amount,
            "Auto-save: " + goal.getTitle(), "AUTO_SAVE_RULE", rule.getId());
        savingDepositRepository.save(SavingDeposit.builder()
            .savingGoal(goal).amount(amount).note("Auto-save").depositDate(runDate).build());

        goal.setCurrentAmount(goal.getCurrentAmount().add(amount));
        if (goal.getCurrentAmount().compareTo(goal.getTargetAmount()) >= 0) {
            goal.setStatus(SavingGoalStatus.COMPLETED);
            rule.setActive(false); // goal reached - stop auto-saving into it
        }
        savingGoalRepository.save(goal);
        savingGoalCompletionNotifier.notifyIfCompleted(goal, statusBefore);
        log.info("Auto-save applied: rule={} goal={} amount={} {} on {}",
            rule.getId(), goal.getId(), amount, rule.getCurrency(), runDate);
        return true;
    }

    private AutoSaveRuleResponse toResponse(AutoSaveRule rule) {
        SavingGoal goal = rule.getSavingGoal();
        return new AutoSaveRuleResponse(rule.getId(), goal == null ? null : goal.getId(),
            goal == null ? null : goal.getTitle(), rule.getType(), rule.getPercent(), rule.getCurrency(),
            rule.getNextRunDate(), rule.getLastRunDate(), rule.isActive(), rule.getCreatedAt());
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }
}

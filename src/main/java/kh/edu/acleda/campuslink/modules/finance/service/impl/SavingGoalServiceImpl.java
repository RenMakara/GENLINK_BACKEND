package kh.edu.acleda.campuslink.modules.finance.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.SavingDepositRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.request.SavingGoalCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.SavingDepositResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.SavingGoalResponse;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.entity.SavingDeposit;
import kh.edu.acleda.campuslink.modules.finance.entity.SavingGoal;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.SavingGoalStatus;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.repository.SavingDepositRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.SavingGoalRepository;
import kh.edu.acleda.campuslink.modules.finance.service.DemoWalletService;
import kh.edu.acleda.campuslink.modules.finance.service.LedgerService;
import kh.edu.acleda.campuslink.modules.finance.service.SavingGoalCompletionNotifier;
import kh.edu.acleda.campuslink.modules.finance.service.SavingGoalService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavingGoalServiceImpl implements SavingGoalService {

    private final SavingGoalRepository savingGoalRepository;
    private final SavingDepositRepository savingDepositRepository;
    private final DemoWalletService demoWalletService;
    private final LedgerService ledgerService;
    private final SavingGoalCompletionNotifier savingGoalCompletionNotifier;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public SavingGoalServiceImpl(SavingGoalRepository savingGoalRepository,
                                 SavingDepositRepository savingDepositRepository,
                                 DemoWalletService demoWalletService,
                                 LedgerService ledgerService,
                                 SavingGoalCompletionNotifier savingGoalCompletionNotifier,
                                 UserRepository userRepository,
                                 CurrentUserProvider currentUserProvider) {
        this.savingGoalRepository = savingGoalRepository;
        this.savingDepositRepository = savingDepositRepository;
        this.demoWalletService = demoWalletService;
        this.ledgerService = ledgerService;
        this.savingGoalCompletionNotifier = savingGoalCompletionNotifier;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public SavingGoalResponse create(SavingGoalCreateRequest request) {
        User user = loadCurrentUser();
        SavingGoal goal = savingGoalRepository.save(SavingGoal.builder()
            .user(user)
            .title(request.title())
            .currency(request.currency() == null ? Currency.USD : request.currency())
            .targetAmount(request.targetAmount())
            .currentAmount(BigDecimal.ZERO)
            .targetDate(request.targetDate())
            .status(SavingGoalStatus.IN_PROGRESS)
            .build());
        return toResponse(goal);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavingGoalResponse> getMine(SavingGoalStatus status, Currency currency) {
        UUID userId = currentUserProvider.getCurrentUserId();
        List<SavingGoal> goals = savingGoalRepository.findByUser(userId, status, currency);
        return goals.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SavingGoalResponse getById(UUID id) {
        return toResponse(loadOwned(id));
    }

    @Override
    @Transactional
    public SavingGoalResponse update(UUID id, SavingGoalCreateRequest request) {
        SavingGoal goal = loadOwned(id);
        SavingGoalStatus statusBefore = goal.getStatus();
        goal.setTitle(request.title());
        goal.setTargetAmount(request.targetAmount());
        goal.setTargetDate(request.targetDate());
        // A cancelled goal stays cancelled; otherwise re-derive completion from the new
        // target (lowering it can complete a goal, raising it can reopen one).
        if (goal.getStatus() != SavingGoalStatus.CANCELLED) {
            goal.setStatus(reachedTarget(goal) ? SavingGoalStatus.COMPLETED : SavingGoalStatus.IN_PROGRESS);
        }
        SavingGoal savedGoal = savingGoalRepository.save(goal);
        savingGoalCompletionNotifier.notifyIfCompleted(savedGoal, statusBefore);
        return toResponse(savedGoal);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        SavingGoal goal = loadOwned(id);
        goal.setDeletedAt(Instant.now());
        savingGoalRepository.save(goal);
    }

    @Override
    @Transactional
    public SavingGoalResponse deposit(UUID goalId, SavingDepositRequest request) {
        SavingGoal goal = loadOwned(goalId);
        if (goal.getStatus() == SavingGoalStatus.CANCELLED) {
            throw new ApiException("Cannot deposit into a cancelled goal", "GOAL_CANCELLED");
        }
        SavingGoalStatus statusBefore = goal.getStatus();

        // Opt-in: a "from wallet" deposit actually moves money out of the wallet into the goal.
        // Debit first so a too-low balance rejects before any deposit/goal change (rolled back
        // together by this @Transactional). Default keeps the old behaviour - goal grows only.
        if (Boolean.TRUE.equals(request.fromWallet())) {
            DemoWallet wallet = demoWalletService.getOrCreateWalletEntity();
            ledgerService.debit(wallet, TransactionType.SAVING, goal.getCurrency(), request.amount(),
                "Saving deposit: " + goal.getTitle(), "SAVING_GOAL", goal.getId());
        }

        savingDepositRepository.save(SavingDeposit.builder()
            .savingGoal(goal)
            .amount(request.amount())
            .note(request.note())
            .depositDate(request.depositDate() == null ? LocalDate.now() : request.depositDate())
            .build());

        // currentAmount is the running total of deposits. Deposits are append-only (no
        // edit or delete endpoint), so updating the counter in the same transaction as
        // the deposit row keeps the two in step without a recompute.
        goal.setCurrentAmount(goal.getCurrentAmount().add(request.amount()));
        if (reachedTarget(goal)) {
            goal.setStatus(SavingGoalStatus.COMPLETED);
        }
        SavingGoal savedGoal = savingGoalRepository.save(goal);
        savingGoalCompletionNotifier.notifyIfCompleted(savedGoal, statusBefore);
        return toResponse(savedGoal);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavingDepositResponse> deposits(UUID goalId) {
        loadOwned(goalId); // ownership check - another user's goal reads as not found
        return savingDepositRepository.findBySavingGoalIdOrderByDepositDateDescCreatedAtDesc(goalId).stream()
            .map(d -> new SavingDepositResponse(d.getId(), d.getAmount(), d.getNote(), d.getDepositDate()))
            .toList();
    }

    private boolean reachedTarget(SavingGoal goal) {
        return goal.getCurrentAmount().compareTo(goal.getTargetAmount()) >= 0;
    }

    private SavingGoalResponse toResponse(SavingGoal goal) {
        BigDecimal target = goal.getTargetAmount();
        BigDecimal current = goal.getCurrentAmount();
        BigDecimal remaining = target.subtract(current).max(BigDecimal.ZERO);

        int percent = 0;
        if (target.signum() > 0) {
            percent = current.multiply(BigDecimal.valueOf(100))
                .divide(target, 0, RoundingMode.DOWN)
                .min(BigDecimal.valueOf(100))
                .intValue();
        }
        return new SavingGoalResponse(goal.getId(), goal.getTitle(), target, current, remaining,
            percent, goal.getTargetDate(), goal.getStatus(), goal.getCurrency());
    }

    private SavingGoal loadOwned(UUID id) {
        return savingGoalRepository.findByIdAndUserIdAndDeletedAtIsNull(id, currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Saving goal not found"));
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }
}

package kh.edu.acleda.campuslink.modules.finance.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.RecurringTransactionCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.RecurringTransactionResponse;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.entity.Expense;
import kh.edu.acleda.campuslink.modules.finance.entity.Income;
import kh.edu.acleda.campuslink.modules.finance.entity.RecurringTransaction;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.RecurringType;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.IncomeRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.RecurringTransactionRepository;
import kh.edu.acleda.campuslink.modules.finance.service.DemoWalletService;
import kh.edu.acleda.campuslink.modules.finance.service.LedgerService;
import kh.edu.acleda.campuslink.modules.finance.service.RecurringTransactionService;
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
public class RecurringTransactionServiceImpl implements RecurringTransactionService {

    private static final Logger log = LoggerFactory.getLogger(RecurringTransactionServiceImpl.class);

    private final RecurringTransactionRepository recurringRepository;
    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final DemoWalletService demoWalletService;
    private final LedgerService ledgerService;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public RecurringTransactionServiceImpl(RecurringTransactionRepository recurringRepository,
                                           IncomeRepository incomeRepository,
                                           ExpenseRepository expenseRepository,
                                           DemoWalletService demoWalletService,
                                           LedgerService ledgerService,
                                           UserRepository userRepository,
                                           CurrentUserProvider currentUserProvider) {
        this.recurringRepository = recurringRepository;
        this.incomeRepository = incomeRepository;
        this.expenseRepository = expenseRepository;
        this.demoWalletService = demoWalletService;
        this.ledgerService = ledgerService;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public RecurringTransactionResponse create(RecurringTransactionCreateRequest request) {
        User user = loadCurrentUser();
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new ApiException("End date cannot be before start date", "VALIDATION_ERROR");
        }
        RecurringTransaction template = recurringRepository.save(RecurringTransaction.builder()
            .user(user)
            .type(request.type())
            .title(request.title().strip())
            .amount(request.amount())
            .currency(request.currency() == null ? Currency.USD : request.currency())
            .category(request.category())
            .frequency(request.frequency())
            .fromWallet(Boolean.TRUE.equals(request.fromWallet()))
            // First run is the start date; the scheduler advances it from there.
            .nextRunDate(request.startDate())
            .startDate(request.startDate())
            .endDate(request.endDate())
            .active(true)
            .build());
        return toResponse(template);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecurringTransactionResponse> getMine() {
        return recurringRepository
            .findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(currentUserProvider.getCurrentUserId())
            .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        RecurringTransaction template = recurringRepository
            .findByIdAndUserIdAndDeletedAtIsNull(id, currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Recurring transaction not found"));
        template.setDeletedAt(Instant.now());
        template.setActive(false);
        recurringRepository.save(template);
    }

    @Override
    @Transactional
    public int processDue(LocalDate asOf) {
        int occurrences = 0;
        for (RecurringTransaction template : recurringRepository.findDue(asOf)) {
            occurrences += materializeCatchUp(template, asOf);
        }
        return occurrences;
    }

    /** Fire the template for every run date up to {@code asOf}, advancing as it goes. */
    private int materializeCatchUp(RecurringTransaction template, LocalDate asOf) {
        int made = 0;
        while (template.isActive() && !template.getNextRunDate().isAfter(asOf)) {
            LocalDate runDate = template.getNextRunDate();
            if (template.getEndDate() != null && runDate.isAfter(template.getEndDate())) {
                template.setActive(false);
                break;
            }
            materializeOne(template, runDate);
            made++;
            template.setLastRunDate(runDate);
            template.setNextRunDate(template.getFrequency().next(runDate));
        }
        // Past its end date with nothing left to run: retire it so it stops being scanned.
        if (template.getEndDate() != null && template.getNextRunDate().isAfter(template.getEndDate())) {
            template.setActive(false);
        }
        recurringRepository.save(template);
        return made;
    }

    private void materializeOne(RecurringTransaction template, LocalDate runDate) {
        User user = template.getUser();
        Currency currency = template.getCurrency();
        if (template.getType() == RecurringType.INCOME) {
            Income income = incomeRepository.save(Income.builder()
                .user(user).title(template.getTitle()).amount(template.getAmount())
                .currency(currency).source(template.getCategory()).incomeDate(runDate).build());
            // Income always credits the wallet, exactly like a manual income.
            DemoWallet wallet = demoWalletService.getOrCreateWalletEntity(user);
            ledgerService.credit(wallet, TransactionType.INCOME, currency, template.getAmount(),
                "Recurring income: " + template.getTitle(), "INCOME", income.getId());
        } else {
            Expense expense = expenseRepository.save(Expense.builder()
                .user(user).title(template.getTitle()).amount(template.getAmount())
                .currency(currency).category(template.getCategory()).expenseDate(runDate)
                .paidFromWallet(false).build());
            if (template.isFromWallet()) {
                if (debitIfFunded(user, currency, template.getAmount(), expense)) {
                    // Keep the expense's payment state aligned with the ledger. This also lets
                    // later edits/deletes refund the wallet only when a debit really happened.
                    expense.setPaidFromWallet(true);
                    expenseRepository.save(expense);
                }
            }
        }
        log.info("Recurring {} materialised: template={} user={} amount={} {} on {}",
            template.getType(), template.getId(), user.getId(), currency, template.getAmount(), runDate);
    }

    /**
     * A wallet-funded recurring expense debits the wallet only when the funds are there. Unlike a
     * manual expense (which rejects outright on a low balance), automation must not fail the whole
     * batch, so the expense is still tracked and the debit is skipped with a warning. Checking the
     * balance up front avoids throwing inside the batch transaction and marking it rollback-only.
     */
    private boolean debitIfFunded(User user, Currency currency, BigDecimal amount, Expense expense) {
        DemoWallet wallet = demoWalletService.getOrCreateWalletEntity(user);
        BigDecimal balance = currency == Currency.KHR ? wallet.getBalanceKhr() : wallet.getBalanceUsd();
        if (balance != null && balance.compareTo(amount) >= 0) {
            ledgerService.debit(wallet, TransactionType.EXPENSE, currency, amount,
                "Recurring expense: " + expense.getTitle(), "EXPENSE", expense.getId());
            return true;
        } else {
            log.warn("Recurring expense {} tracked but wallet debit skipped (insufficient {} funds): user={}",
                expense.getId(), currency, user.getId());
            return false;
        }
    }

    private RecurringTransactionResponse toResponse(RecurringTransaction t) {
        return new RecurringTransactionResponse(t.getId(), t.getType(), t.getTitle(), t.getAmount(),
            t.getCurrency(), t.getCategory(), t.getFrequency(), t.isFromWallet(), t.getNextRunDate(),
            t.getStartDate(), t.getEndDate(), t.getLastRunDate(), t.isActive(), t.getCreatedAt());
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }
}

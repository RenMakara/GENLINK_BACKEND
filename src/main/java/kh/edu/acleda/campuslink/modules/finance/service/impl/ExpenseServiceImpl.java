package kh.edu.acleda.campuslink.modules.finance.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.ExpenseCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.ExpenseResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.MonthlySummaryResponse;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.entity.Expense;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.finance.service.DemoWalletService;
import kh.edu.acleda.campuslink.modules.finance.service.ExpenseService;
import kh.edu.acleda.campuslink.modules.finance.service.LedgerService;
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
public class ExpenseServiceImpl implements ExpenseService {

    private static final String UNCATEGORIZED = "UNCATEGORIZED";

    private final ExpenseRepository expenseRepository;
    private final DemoWalletService demoWalletService;
    private final LedgerService ledgerService;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public ExpenseServiceImpl(ExpenseRepository expenseRepository,
                              DemoWalletService demoWalletService,
                              LedgerService ledgerService,
                              UserRepository userRepository,
                              CurrentUserProvider currentUserProvider) {
        this.expenseRepository = expenseRepository;
        this.demoWalletService = demoWalletService;
        this.ledgerService = ledgerService;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public ExpenseResponse create(ExpenseCreateRequest request) {
        User user = loadCurrentUser();
        Currency currency = request.currency() == null ? Currency.USD : request.currency();
        Expense expense = expenseRepository.save(Expense.builder()
            .user(user)
            .title(request.title().strip())
            .amount(request.amount())
            .currency(currency)
            .category(request.category())
            .description(request.description())
            .expenseDate(request.expenseDate())
            .paymentMethod(request.paymentMethod())
            .build());

        // Opt-in: only "paid from wallet" expenses move the balance. A too-low balance rejects
        // the whole thing (this @Transactional rolls the expense row back too), so the wallet
        // and the recorded spend never disagree.
        if (Boolean.TRUE.equals(request.paidFromWallet())) {
            DemoWallet wallet = demoWalletService.getOrCreateWalletEntity();
            ledgerService.debit(wallet, TransactionType.EXPENSE, currency, expense.getAmount(),
                "Expense: " + expense.getTitle(), "EXPENSE", expense.getId());
        }
        return toResponse(expense);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> getMine(String category, Integer month, Integer year, Pageable pageable) {
        UUID userId = currentUserProvider.getCurrentUserId();

        // A month filter needs both month and year, otherwise "March" spans every year.
        if (month != null && year != null) {
            validateMonth(month);
            LocalDate start = LocalDate.of(year, month, 1);
            LocalDate end = start.plusMonths(1);
            return PageResponse.from(
                expenseRepository.findByUserAndDateRange(userId, category, start, end, pageable), this::toResponse);
        }
        return PageResponse.from(expenseRepository.findByUser(userId, category, pageable), this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseResponse getById(UUID id) {
        return toResponse(loadOwned(id));
    }

    @Override
    @Transactional
    public ExpenseResponse update(UUID id, ExpenseCreateRequest request) {
        Expense expense = loadOwned(id);
        expense.setTitle(request.title().strip());
        expense.setAmount(request.amount());
        expense.setCategory(request.category());
        expense.setDescription(request.description());
        expense.setExpenseDate(request.expenseDate());
        expense.setPaymentMethod(request.paymentMethod());
        return toResponse(expenseRepository.save(expense));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Expense expense = loadOwned(id);
        expense.setDeletedAt(Instant.now());
        expenseRepository.save(expense);
    }

    @Override
    @Transactional(readOnly = true)
    public MonthlySummaryResponse monthlySummary(int month, int year, Currency currency) {
        validateMonth(month);
        UUID userId = currentUserProvider.getCurrentUserId();
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1);

        List<MonthlySummaryResponse.CategoryTotal> byCategory = expenseRepository
            .sumByCategoryAndCurrency(userId, currency, start, end)
            .stream()
            .map(row -> new MonthlySummaryResponse.CategoryTotal(
                row[0] == null ? UNCATEGORIZED : (String) row[0],
                (BigDecimal) row[1]))
            .toList();

        BigDecimal total = expenseRepository.sumForMonthByCurrency(userId, currency, start, end);
        return new MonthlySummaryResponse(month, year, currency,
            total == null ? BigDecimal.ZERO : total, byCategory);
    }

    /** Also guards LocalDate.of, which would otherwise throw its own DateTimeException. */
    private void validateMonth(int month) {
        if (month < 1 || month > 12) {
            throw new ApiException("Month must be between 1 and 12", "VALIDATION_ERROR");
        }
    }

    /** Owner-scoped: another user's id reads as not found, never forbidden. */
    private Expense loadOwned(UUID id) {
        return expenseRepository.findByIdAndUserIdAndDeletedAtIsNull(id, currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Expense not found"));
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private ExpenseResponse toResponse(Expense expense) {
        return new ExpenseResponse(
            expense.getId(),
            expense.getTitle(),
            expense.getAmount(),
            expense.getCurrency(),
            expense.getCategory(),
            expense.getDescription(),
            expense.getExpenseDate(),
            expense.getPaymentMethod(),
            expense.getCreatedAt()
        );
    }
}

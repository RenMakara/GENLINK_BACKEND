package kh.edu.acleda.campuslink.modules.finance.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.IncomeCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.CashFlowResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.IncomeResponse;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.entity.Income;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.IncomeRepository;
import kh.edu.acleda.campuslink.modules.finance.service.DemoWalletService;
import kh.edu.acleda.campuslink.modules.finance.service.IncomeService;
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
public class IncomeServiceImpl implements IncomeService {

    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final DemoWalletService demoWalletService;
    private final LedgerService ledgerService;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public IncomeServiceImpl(IncomeRepository incomeRepository,
                             ExpenseRepository expenseRepository,
                             DemoWalletService demoWalletService,
                             LedgerService ledgerService,
                             UserRepository userRepository,
                             CurrentUserProvider currentUserProvider) {
        this.incomeRepository = incomeRepository;
        this.expenseRepository = expenseRepository;
        this.demoWalletService = demoWalletService;
        this.ledgerService = ledgerService;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public IncomeResponse create(IncomeCreateRequest request) {
        User user = loadCurrentUser();
        Currency currency = request.currency() == null ? Currency.USD : request.currency();
        Income income = incomeRepository.save(Income.builder()
            .user(user)
            .title(request.title().strip())
            .amount(request.amount())
            .currency(currency)
            .source(request.source())
            .description(request.description())
            .incomeDate(request.incomeDate())
            .build());

        DemoWallet wallet = demoWalletService.getOrCreateWalletEntity();
        ledgerService.credit(wallet, TransactionType.INCOME, currency, income.getAmount(),
            "Income: " + income.getTitle(), "INCOME", income.getId());
        return toResponse(income);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IncomeResponse> getMine(String source, Integer month, Integer year,
                                                Currency currency, Pageable pageable) {
        UUID userId = currentUserProvider.getCurrentUserId();

        // A month filter needs both month and year, otherwise "March" spans every year.
        if (month != null && year != null) {
            validateMonth(month);
            LocalDate start = LocalDate.of(year, month, 1);
            LocalDate end = start.plusMonths(1);
            return PageResponse.from(
                incomeRepository.findByUserAndDateRange(userId, source, currency, start, end, pageable),
                this::toResponse);
        }
        return PageResponse.from(incomeRepository.findByUser(userId, source, currency, pageable), this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public IncomeResponse getById(UUID id) {
        return toResponse(loadOwned(id));
    }

    @Override
    @Transactional
    public IncomeResponse update(UUID id, IncomeCreateRequest request) {
        Income income = loadOwnedForUpdate(id);
        Currency oldCurrency = income.getCurrency();
        Currency newCurrency = request.currency() == null ? oldCurrency : request.currency();
        adjustWalletForIncomeChange(income, oldCurrency, income.getAmount(), newCurrency, request.amount());

        income.setTitle(request.title().strip());
        income.setAmount(request.amount());
        income.setCurrency(newCurrency);
        income.setSource(request.source());
        income.setDescription(request.description());
        income.setIncomeDate(request.incomeDate());
        return toResponse(incomeRepository.save(income));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Income income = loadOwnedForUpdate(id);
        DemoWallet wallet = demoWalletService.getOrCreateWalletEntity();
        ledgerService.debit(wallet, TransactionType.INCOME, income.getCurrency(), income.getAmount(),
            "Income deleted: " + income.getTitle(), "INCOME", income.getId());
        income.setDeletedAt(Instant.now());
        incomeRepository.save(income);
    }

    /** Income always belongs to the wallet. */
    private void adjustWalletForIncomeChange(Income income, Currency oldCurrency, BigDecimal oldAmount,
                                             Currency newCurrency, BigDecimal newAmount) {
        DemoWallet wallet = demoWalletService.getOrCreateWalletEntity();
        if (oldCurrency == newCurrency) {
            BigDecimal delta = newAmount.subtract(oldAmount);
            if (delta.signum() > 0) {
                ledgerService.credit(wallet, TransactionType.INCOME, newCurrency, delta,
                    "Income increased: " + income.getTitle(), "INCOME", income.getId());
            } else if (delta.signum() < 0) {
                ledgerService.debit(wallet, TransactionType.INCOME, oldCurrency, delta.abs(),
                    "Income reduced: " + income.getTitle(), "INCOME", income.getId());
            }
            return;
        }

        ledgerService.debit(wallet, TransactionType.INCOME, oldCurrency, oldAmount,
            "Income currency changed (reverse): " + income.getTitle(), "INCOME", income.getId());
        ledgerService.credit(wallet, TransactionType.INCOME, newCurrency, newAmount,
            "Income currency changed: " + income.getTitle(), "INCOME", income.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public CashFlowResponse cashFlow(Currency currency, int month, int year) {
        validateMonth(month);
        UUID userId = currentUserProvider.getCurrentUserId();
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1);

        // Per currency: mixing KHR and USD in one figure would be meaningless.
        BigDecimal income = orZero(incomeRepository.sumForMonthByCurrency(userId, currency, start, end));
        BigDecimal expense = orZero(expenseRepository.sumForMonthByCurrency(userId, currency, start, end));
        BigDecimal net = income.subtract(expense);

        // Savings rate = net / income, as a percent.
        BigDecimal savingsRate = income.signum() == 0
            ? BigDecimal.ZERO
            : net.multiply(BigDecimal.valueOf(100)).divide(income, 1, RoundingMode.HALF_UP);

        return new CashFlowResponse(currency, month, year, income, expense, net, savingsRate);
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Also guards LocalDate.of, which would otherwise throw its own DateTimeException. */
    private void validateMonth(int month) {
        if (month < 1 || month > 12) {
            throw new ApiException("Month must be between 1 and 12", "VALIDATION_ERROR");
        }
    }

    /** Owner-scoped: another user's id reads as not found, never forbidden. */
    private Income loadOwned(UUID id) {
        return incomeRepository.findByIdAndUserIdAndDeletedAtIsNull(id, currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Income not found"));
    }

    private Income loadOwnedForUpdate(UUID id) {
        return incomeRepository.findByIdAndUserIdForUpdate(id, currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Income not found"));
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private IncomeResponse toResponse(Income income) {
        return new IncomeResponse(
            income.getId(),
            income.getTitle(),
            income.getAmount(),
            income.getCurrency(),
            income.getSource(),
            income.getDescription(),
            income.getIncomeDate(),
            income.getCreatedAt()
        );
    }
}

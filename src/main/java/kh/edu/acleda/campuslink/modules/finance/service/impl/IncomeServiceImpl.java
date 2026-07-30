package kh.edu.acleda.campuslink.modules.finance.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.IncomeCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.CashFlowResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.IncomeResponse;
import kh.edu.acleda.campuslink.modules.finance.entity.Income;
import kh.edu.acleda.campuslink.modules.finance.repository.ExpenseRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.IncomeRepository;
import kh.edu.acleda.campuslink.modules.finance.service.IncomeService;
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
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public IncomeServiceImpl(IncomeRepository incomeRepository,
                             ExpenseRepository expenseRepository,
                             UserRepository userRepository,
                             CurrentUserProvider currentUserProvider) {
        this.incomeRepository = incomeRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public IncomeResponse create(IncomeCreateRequest request) {
        User user = loadCurrentUser();
        Income income = incomeRepository.save(Income.builder()
            .user(user)
            .title(request.title().strip())
            .amount(request.amount())
            .source(request.source())
            .description(request.description())
            .incomeDate(request.incomeDate())
            .build());
        return toResponse(income);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IncomeResponse> getMine(String source, Integer month, Integer year, Pageable pageable) {
        UUID userId = currentUserProvider.getCurrentUserId();

        // A month filter needs both month and year, otherwise "March" spans every year.
        if (month != null && year != null) {
            validateMonth(month);
            LocalDate start = LocalDate.of(year, month, 1);
            LocalDate end = start.plusMonths(1);
            return PageResponse.from(
                incomeRepository.findByUserAndDateRange(userId, source, start, end, pageable), this::toResponse);
        }
        return PageResponse.from(incomeRepository.findByUser(userId, source, pageable), this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public IncomeResponse getById(UUID id) {
        return toResponse(loadOwned(id));
    }

    @Override
    @Transactional
    public IncomeResponse update(UUID id, IncomeCreateRequest request) {
        Income income = loadOwned(id);
        income.setTitle(request.title().strip());
        income.setAmount(request.amount());
        income.setSource(request.source());
        income.setDescription(request.description());
        income.setIncomeDate(request.incomeDate());
        return toResponse(incomeRepository.save(income));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Income income = loadOwned(id);
        income.setDeletedAt(Instant.now());
        incomeRepository.save(income);
    }

    @Override
    @Transactional(readOnly = true)
    public CashFlowResponse cashFlow(int month, int year) {
        validateMonth(month);
        UUID userId = currentUserProvider.getCurrentUserId();
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1);

        BigDecimal income = orZero(incomeRepository.sumForMonth(userId, start, end));
        BigDecimal expense = orZero(expenseRepository.sumForMonth(userId, start, end));
        BigDecimal net = income.subtract(expense);

        // Savings rate = net / income, as a percent. Undefined with no income, so 0. It can go
        // negative when the month overspent - that is a true and useful signal, not an error.
        BigDecimal savingsRate = income.signum() == 0
            ? BigDecimal.ZERO
            : net.multiply(BigDecimal.valueOf(100)).divide(income, 1, RoundingMode.HALF_UP);

        return new CashFlowResponse(month, year, income, expense, net, savingsRate);
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

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private IncomeResponse toResponse(Income income) {
        return new IncomeResponse(
            income.getId(),
            income.getTitle(),
            income.getAmount(),
            income.getSource(),
            income.getDescription(),
            income.getIncomeDate(),
            income.getCreatedAt()
        );
    }
}

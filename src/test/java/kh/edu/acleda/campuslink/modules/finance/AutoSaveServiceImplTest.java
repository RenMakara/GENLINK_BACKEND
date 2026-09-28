package kh.edu.acleda.campuslink.modules.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.AutoSaveRuleCreateRequest;
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
import kh.edu.acleda.campuslink.modules.finance.service.DemoWalletService;
import kh.edu.acleda.campuslink.modules.finance.service.LedgerService;
import kh.edu.acleda.campuslink.modules.finance.service.SavingGoalCompletionNotifier;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.finance.service.impl.AutoSaveServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutoSaveServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID GOAL_ID = UUID.randomUUID();
    private static final LocalDate WINDOW_START = LocalDate.of(2026, 7, 1);
    private static final LocalDate WINDOW_END = LocalDate.of(2026, 8, 1);

    @Mock private AutoSaveRuleRepository autoSaveRuleRepository;
    @Mock private SavingGoalRepository savingGoalRepository;
    @Mock private SavingDepositRepository savingDepositRepository;
    @Mock private IncomeRepository incomeRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private DemoWalletService demoWalletService;
    @Mock private LedgerService ledgerService;
    @Mock private SavingGoalCompletionNotifier savingGoalCompletionNotifier;
    @Mock private NotificationEmitter notificationEmitter;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private AutoSaveServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AutoSaveServiceImpl(autoSaveRuleRepository, savingGoalRepository, savingDepositRepository,
            incomeRepository, expenseRepository, demoWalletService, ledgerService, savingGoalCompletionNotifier,
            notificationEmitter,
            userRepository,
            currentUserProvider);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(autoSaveRuleRepository.save(any(AutoSaveRule.class))).thenAnswer(i -> {
            AutoSaveRule r = i.getArgument(0);
            if (r.getId() == null) {
                ReflectionTestUtils.setField(r, "id", UUID.randomUUID());
            }
            return r;
        });
    }

    private SavingGoal goal(String target, String current, SavingGoalStatus status) {
        SavingGoal g = SavingGoal.builder().user(user).title("Laptop").currency(Currency.USD)
            .targetAmount(new BigDecimal(target)).currentAmount(new BigDecimal(current)).status(status).build();
        ReflectionTestUtils.setField(g, "id", GOAL_ID);
        return g;
    }

    private void stubGoalForUpdate(SavingGoal goal) {
        when(savingGoalRepository.findByIdAndUserIdAndDeletedAtIsNullForUpdate(GOAL_ID, USER_ID))
            .thenReturn(Optional.of(goal));
    }

    private DemoWallet wallet(String usd) {
        DemoWallet w = DemoWallet.builder().user(user)
            .balanceUsd(new BigDecimal(usd)).balanceKhr(BigDecimal.ZERO).build();
        when(demoWalletService.getOrCreateWalletEntity(user)).thenReturn(w);
        return w;
    }

    private AutoSaveRule rule(AutoSaveType type, String percent, SavingGoal goal) {
        AutoSaveRule r = AutoSaveRule.builder().user(user).savingGoal(goal).type(type)
            .percent(percent == null ? null : new BigDecimal(percent)).currency(Currency.USD)
            .nextRunDate(LocalDate.of(2026, 8, 1)).active(true).build();
        ReflectionTestUtils.setField(r, "id", UUID.randomUUID());
        return r;
    }

    // ---- create ----

    @Test
    void createPercentRulePersists() {
        when(savingGoalRepository.findByIdAndUserIdAndDeletedAtIsNull(GOAL_ID, USER_ID))
            .thenReturn(Optional.of(goal("1000.00", "0.00", SavingGoalStatus.IN_PROGRESS)));

        var resp = service.create(new AutoSaveRuleCreateRequest(
            GOAL_ID, AutoSaveType.PERCENT_OF_INCOME, new BigDecimal("10"), null));

        assertThat(resp.type()).isEqualTo(AutoSaveType.PERCENT_OF_INCOME);
        assertThat(resp.percent()).isEqualByComparingTo("10");
        assertThat(resp.active()).isTrue();
        verify(autoSaveRuleRepository).save(any(AutoSaveRule.class));
    }

    @Test
    void createPercentRuleRequiresAPercent() {
        when(savingGoalRepository.findByIdAndUserIdAndDeletedAtIsNull(GOAL_ID, USER_ID))
            .thenReturn(Optional.of(goal("1000.00", "0.00", SavingGoalStatus.IN_PROGRESS)));

        assertThatThrownBy(() -> service.create(new AutoSaveRuleCreateRequest(
            GOAL_ID, AutoSaveType.PERCENT_OF_INCOME, null, null))).isInstanceOf(ApiException.class);
    }

    @Test
    void createRejectsACurrencyThatDoesNotMatchTheGoal() {
        when(savingGoalRepository.findByIdAndUserIdAndDeletedAtIsNull(GOAL_ID, USER_ID))
            .thenReturn(Optional.of(goal("1000.00", "0.00", SavingGoalStatus.IN_PROGRESS)));

        assertThatThrownBy(() -> service.create(new AutoSaveRuleCreateRequest(
            GOAL_ID, AutoSaveType.ROUND_UP, null, Currency.KHR))).isInstanceOf(ApiException.class);
    }

    @Test
    void createFailsWhenGoalNotFound() {
        when(savingGoalRepository.findByIdAndUserIdAndDeletedAtIsNull(GOAL_ID, USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new AutoSaveRuleCreateRequest(
            GOAL_ID, AutoSaveType.ROUND_UP, null, Currency.USD))).isInstanceOf(NotFoundException.class);
    }

    // ---- processDue ----

    @Test
    void percentOfIncomeDebitsWalletAndGrowsGoal() {
        SavingGoal g = goal("1000.00", "0.00", SavingGoalStatus.IN_PROGRESS);
        stubGoalForUpdate(g);
        DemoWallet w = wallet("500.00");
        AutoSaveRule r = rule(AutoSaveType.PERCENT_OF_INCOME, "10", g);
        when(autoSaveRuleRepository.findDue(any())).thenReturn(List.of(r));
        when(incomeRepository.sumForMonthByCurrency(USER_ID, Currency.USD, WINDOW_START, WINDOW_END))
            .thenReturn(new BigDecimal("100.00")); // 10% -> 10.00

        int count = service.processDue(LocalDate.of(2026, 8, 5));

        assertThat(count).isEqualTo(1);
        verify(ledgerService).debit(eq(w), eq(TransactionType.SAVING), eq(Currency.USD),
            eq(new BigDecimal("10.00")), any(), eq("AUTO_SAVE_RULE"), any());
        verify(savingDepositRepository).save(any(SavingDeposit.class));
        verify(savingGoalRepository).findByIdAndUserIdAndDeletedAtIsNullForUpdate(GOAL_ID, USER_ID);
        assertThat(g.getCurrentAmount()).isEqualByComparingTo("10.00");
        assertThat(r.getNextRunDate()).isEqualTo(LocalDate.of(2026, 9, 1));

        ArgumentCaptor<NotificationRequest> notificationCaptor =
            ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(notificationCaptor.capture());
        NotificationRequest notification = notificationCaptor.getValue();
        assertThat(notification.templateKey()).isEqualTo("FINANCE_AUTO_SAVE_APPLIED");
        assertThat(notification.type()).isEqualTo("FINANCE_AUTO_SAVE");
        assertThat(notification.parameters()).containsEntry("amount", "10")
            .containsEntry("currency", "USD")
            .containsEntry("goalName", "Laptop");
        assertThat(notification.referenceType()).isEqualTo("AUTO_SAVE_RULE");
        assertThat(notification.referenceId()).isEqualTo(r.getId());
        assertThat(notification.dedupeKey())
            .isEqualTo("auto-save-applied:" + r.getId() + ":2026-08-01");
    }

    @Test
    void roundUpSavesTheSpareChangeOfLastMonthsExpenses() {
        SavingGoal g = goal("1000.00", "0.00", SavingGoalStatus.IN_PROGRESS);
        stubGoalForUpdate(g);
        DemoWallet w = wallet("100.00");
        AutoSaveRule r = rule(AutoSaveType.ROUND_UP, null, g);
        when(autoSaveRuleRepository.findDue(any())).thenReturn(List.of(r));
        // 2.30 -> +0.70, 5.75 -> +0.25, 1.00 -> +0.00 = 0.95
        when(expenseRepository.findAmountsForMonthByCurrency(USER_ID, Currency.USD, WINDOW_START, WINDOW_END))
            .thenReturn(List.of(new BigDecimal("2.30"), new BigDecimal("5.75"), new BigDecimal("1.00")));

        service.processDue(LocalDate.of(2026, 8, 5));

        verify(ledgerService).debit(eq(w), eq(TransactionType.SAVING), eq(Currency.USD),
            eq(new BigDecimal("0.95")), any(), eq("AUTO_SAVE_RULE"), any());
        assertThat(g.getCurrentAmount()).isEqualByComparingTo("0.95");
    }

    @Test
    void skipsTheRunButStillAdvancesWhenWalletIsShort() {
        SavingGoal g = goal("1000.00", "0.00", SavingGoalStatus.IN_PROGRESS);
        stubGoalForUpdate(g);
        wallet("5.00"); // less than the 10.00 the rule wants to save
        AutoSaveRule r = rule(AutoSaveType.PERCENT_OF_INCOME, "10", g);
        when(autoSaveRuleRepository.findDue(any())).thenReturn(List.of(r));
        when(incomeRepository.sumForMonthByCurrency(USER_ID, Currency.USD, WINDOW_START, WINDOW_END))
            .thenReturn(new BigDecimal("100.00"));

        int count = service.processDue(LocalDate.of(2026, 8, 5));

        assertThat(count).isZero();
        verify(ledgerService, never()).debit(any(), any(), any(), any(), any(), any(), any());
        assertThat(g.getCurrentAmount()).isEqualByComparingTo("0.00");
        assertThat(r.getNextRunDate()).isEqualTo(LocalDate.of(2026, 9, 1)); // still advances
        assertThat(r.isActive()).isTrue();

        ArgumentCaptor<NotificationRequest> notificationCaptor =
            ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(notificationCaptor.capture());
        NotificationRequest notification = notificationCaptor.getValue();
        assertThat(notification.templateKey()).isEqualTo("FINANCE_AUTO_SAVE_SKIPPED");
        assertThat(notification.parameters()).containsEntry("amount", "10")
            .containsEntry("currency", "USD")
            .containsEntry("goalName", "Laptop");
        assertThat(notification.referenceType()).isEqualTo("AUTO_SAVE_RULE");
        assertThat(notification.referenceId()).isEqualTo(r.getId());
        assertThat(notification.dedupeKey())
            .isEqualTo("auto-save-skipped:" + r.getId() + ":2026-08-01");
    }

    @Test
    void completesTheGoalAndDeactivatesTheRule() {
        SavingGoal g = goal("8.00", "0.00", SavingGoalStatus.IN_PROGRESS);
        stubGoalForUpdate(g);
        wallet("100.00");
        AutoSaveRule r = rule(AutoSaveType.PERCENT_OF_INCOME, "10", g);
        when(autoSaveRuleRepository.findDue(any())).thenReturn(List.of(r));
        when(incomeRepository.sumForMonthByCurrency(USER_ID, Currency.USD, WINDOW_START, WINDOW_END))
            .thenReturn(new BigDecimal("100.00")); // saves 10.00 >= target 8.00

        service.processDue(LocalDate.of(2026, 8, 5));

        assertThat(g.getStatus()).isEqualTo(SavingGoalStatus.COMPLETED);
        assertThat(r.isActive()).isFalse();
        verify(savingGoalCompletionNotifier).notifyIfCompleted(g, SavingGoalStatus.IN_PROGRESS);
    }

    @Test
    void aZeroAmountRunMovesNoMoney() {
        SavingGoal g = goal("1000.00", "0.00", SavingGoalStatus.IN_PROGRESS);
        stubGoalForUpdate(g);
        AutoSaveRule r = rule(AutoSaveType.PERCENT_OF_INCOME, "10", g);
        when(autoSaveRuleRepository.findDue(any())).thenReturn(List.of(r));
        when(incomeRepository.sumForMonthByCurrency(USER_ID, Currency.USD, WINDOW_START, WINDOW_END))
            .thenReturn(null); // no income -> 0 to save

        int count = service.processDue(LocalDate.of(2026, 8, 5));

        assertThat(count).isZero();
        verify(ledgerService, never()).debit(any(), any(), any(), any(), any(), any(), any());
        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
        assertThat(r.getNextRunDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }
}

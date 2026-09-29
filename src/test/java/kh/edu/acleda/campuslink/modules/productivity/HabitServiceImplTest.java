package kh.edu.acleda.campuslink.modules.productivity;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.HabitCheckInRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.HabitCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.HabitResponse;
import kh.edu.acleda.campuslink.modules.productivity.entity.Habit;
import kh.edu.acleda.campuslink.modules.productivity.entity.HabitCheckIn;
import kh.edu.acleda.campuslink.modules.productivity.repository.HabitCheckInRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.HabitRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.XpHistoryRepository;
import kh.edu.acleda.campuslink.modules.productivity.service.impl.HabitServiceImpl;
import kh.edu.acleda.campuslink.modules.productivity.service.HabitStreakNotifier;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HabitServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID HABIT_ID = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Phnom_Penh"));

    @Mock private HabitRepository habitRepository;
    @Mock private HabitCheckInRepository checkInRepository;
    @Mock private kh.edu.acleda.campuslink.modules.entertainment.service.RewardService rewardService;
    @Mock private HabitStreakNotifier habitStreakNotifier;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private XpHistoryRepository xpHistoryRepository;

    private HabitServiceImpl service;
    private User user;
    private Habit habit;

    @BeforeEach
    void setUp() {
        service = new HabitServiceImpl(habitRepository, checkInRepository, rewardService, habitStreakNotifier,
            userRepository, currentUserProvider, xpHistoryRepository);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        habit = Habit.builder().user(user).title("Read").category("STUDY").frequency("DAILY").build();
        ReflectionTestUtils.setField(habit, "id", HABIT_ID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(habitRepository.save(any(Habit.class))).thenAnswer(i -> i.getArgument(0));
        when(habitRepository.findByIdAndUserIdAndDeletedAtIsNull(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));
        when(habitRepository.findByIdAndUserIdAndDeletedAtIsNullForUpdate(HABIT_ID, USER_ID))
            .thenReturn(Optional.of(habit));
        when(checkInRepository.saveAndFlush(any(HabitCheckIn.class))).thenAnswer(i -> {
            HabitCheckIn checkIn = i.getArgument(0);
            if (checkIn.getId() == null) {
                ReflectionTestUtils.setField(checkIn, "id", UUID.randomUUID());
            }
            return checkIn;
        });
        when(checkInRepository.findByHabitIdAndUserIdAndCheckInDate(any(), any(), any())).thenReturn(Optional.empty());
        when(checkInRepository.findByHabitIdAndUserIdOrderByCheckInDateDesc(any(), any())).thenReturn(List.of());
    }

    private HabitCheckIn checkInOn(LocalDate date, boolean completed) {
        return HabitCheckIn.builder().habit(habit).user(user).checkInDate(date).completed(completed).build();
    }

    private void completedOn(LocalDate... dates) {
        List<HabitCheckIn> list = java.util.Arrays.stream(dates).map(d -> checkInOn(d, true)).toList();
        when(checkInRepository.findByHabitIdAndUserIdOrderByCheckInDateDesc(HABIT_ID, USER_ID)).thenReturn(list);
    }

    @Test
    void createStartsWithNoStreak() {
        HabitResponse r = service.create(new HabitCreateRequest("Read", "STUDY", "DAILY", null));

        assertThat(r.streakCount()).isZero();
        assertThat(r.completionRate()).isEqualByComparingTo("0.00");
        assertThat(r.checkedInToday()).isFalse();
    }

    @Test
    void checkInCreatesARowWhenNoneExists() {
        service.checkIn(HABIT_ID, new HabitCheckInRequest(null, null));

        ArgumentCaptor<HabitCheckIn> captor = ArgumentCaptor.forClass(HabitCheckIn.class);
        verify(checkInRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCheckInDate()).isEqualTo(TODAY);
        assertThat(captor.getValue().getCompleted()).isTrue();
    }

    @Test
    void checkInReusesTheExistingRowForThatDay() {
        HabitCheckIn existing = checkInOn(TODAY, false);
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        when(checkInRepository.findByHabitIdAndUserIdAndCheckInDate(HABIT_ID, USER_ID, TODAY))
            .thenReturn(Optional.of(existing));

        service.checkIn(HABIT_ID, new HabitCheckInRequest(null, true));

        ArgumentCaptor<HabitCheckIn> captor = ArgumentCaptor.forClass(HabitCheckIn.class);
        verify(checkInRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().getCompleted()).isTrue();
        verify(rewardService).award(user, "HABIT", existing.getId(), 5);
        verify(habitRepository).findByIdAndUserIdAndDeletedAtIsNullForUpdate(HABIT_ID, USER_ID);
    }

    @Test
    void completedCheckInEmitsTheCurrentStreakUpdate() {
        LocalDate today = TODAY;
        HabitCheckIn persisted = checkInOn(today, true);
        ReflectionTestUtils.setField(persisted, "id", UUID.randomUUID());
        when(checkInRepository.findByHabitIdAndUserIdAndCheckInDate(HABIT_ID, USER_ID, today))
            .thenReturn(Optional.empty());
        when(checkInRepository.findByHabitIdAndUserIdOrderByCheckInDateDesc(HABIT_ID, USER_ID))
            .thenReturn(List.of(persisted));

        service.checkIn(HABIT_ID, new HabitCheckInRequest(today, true));

        verify(habitStreakNotifier).notifyForCompletedCheckIn(habit, today, 1);
    }

    @Test
    void alreadyCompletedCheckInDoesNotEmitAnotherStreakUpdate() {
        LocalDate today = TODAY;
        HabitCheckIn existing = checkInOn(today, true);
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        when(checkInRepository.findByHabitIdAndUserIdAndCheckInDate(HABIT_ID, USER_ID, today))
            .thenReturn(Optional.of(existing));
        when(checkInRepository.findByHabitIdAndUserIdOrderByCheckInDateDesc(HABIT_ID, USER_ID))
            .thenReturn(List.of(existing));

        service.checkIn(HABIT_ID, new HabitCheckInRequest(today, true));

        verify(habitStreakNotifier, org.mockito.Mockito.never())
            .notifyForCompletedCheckIn(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void undoingACheckInMarksItNotCompleted() {
        service.checkIn(HABIT_ID, new HabitCheckInRequest(null, false));

        ArgumentCaptor<HabitCheckIn> captor = ArgumentCaptor.forClass(HabitCheckIn.class);
        verify(checkInRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCompleted()).isFalse();
    }

    @Test
    void streakCountsConsecutiveCompletedDaysEndingToday() {
        LocalDate today = TODAY;
        completedOn(today, today.minusDays(1), today.minusDays(2));

        HabitResponse r = service.getById(HABIT_ID);

        assertThat(r.streakCount()).isEqualTo(3);
        assertThat(r.checkedInToday()).isTrue();
    }

    @Test
    void streakStillCountsThroughYesterdayWhenTodayIsNotDoneYet() {
        LocalDate today = TODAY;
        completedOn(today.minusDays(1), today.minusDays(2));

        HabitResponse r = service.getById(HABIT_ID);

        assertThat(r.streakCount()).isEqualTo(2);
        assertThat(r.checkedInToday()).isFalse();
    }

    @Test
    void streakBreaksOnAMissedDay() {
        LocalDate today = TODAY;
        completedOn(today, today.minusDays(2)); // gap at yesterday

        assertThat(service.getById(HABIT_ID).streakCount()).isEqualTo(1);
    }

    @Test
    void completionRateIsCompletedDaysOverThirty() {
        LocalDate today = TODAY;
        completedOn(today, today.minusDays(1), today.minusDays(2)); // 3 of 30

        assertThat(service.getById(HABIT_ID).completionRate()).isEqualByComparingTo("10.00");
    }

    @Test
    void oldCheckInsOutsideTheWindowDoNotCountTowardRate() {
        LocalDate today = TODAY;
        completedOn(today, today.minusDays(40)); // one in window, one outside

        assertThat(service.getById(HABIT_ID).completionRate())
            .isEqualByComparingTo(new java.math.BigDecimal("100").divide(new java.math.BigDecimal("30"), 2, java.math.RoundingMode.DOWN));
    }

    @Test
    void unknownHabitReadsAsNotFound() {
        when(habitRepository.findByIdAndUserIdAndDeletedAtIsNull(HABIT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(HABIT_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void weeklyHabitCanBeCompletedAgainAfterIsoCalendarWeekBoundary() {
        habit.setFrequency("WEEKLY");
        LocalDate sunday = LocalDate.of(2026, 9, 27);
        LocalDate monday = sunday.plusDays(1);

        service.checkIn(HABIT_ID, new HabitCheckInRequest(sunday, true));
        service.checkIn(HABIT_ID, new HabitCheckInRequest(monday, true));

        verify(checkInRepository).existsByHabitIdAndUserIdAndCompletedTrueAndCheckInDateBetween(
            HABIT_ID, USER_ID, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 27));
        verify(checkInRepository).existsByHabitIdAndUserIdAndCompletedTrueAndCheckInDateBetween(
            HABIT_ID, USER_ID, monday, monday.plusDays(6));
    }

    @Test
    void weeklyHabitRejectsASecondCompletionWithinTheSameMondayToSundayWeek() {
        habit.setFrequency("WEEKLY");
        LocalDate monday = LocalDate.of(2026, 9, 21);
        when(checkInRepository.existsByHabitIdAndUserIdAndCompletedTrueAndCheckInDateBetween(
            HABIT_ID, USER_ID, monday, monday.plusDays(6))).thenReturn(true);

        assertThatThrownBy(() -> service.checkIn(HABIT_ID,
            new HabitCheckInRequest(monday.plusDays(4), true)))
            .isInstanceOf(kh.edu.acleda.campuslink.shared.exception.ApiException.class)
            .hasMessageContaining("already complete for this calendar week");
        verify(checkInRepository, never()).saveAndFlush(any(HabitCheckIn.class));
    }

    @Test
    void weeklyStreakAndCompletionRateUseCalendarWeeks() {
        habit.setFrequency("WEEKLY");
        LocalDate currentWeek = TODAY.with(java.time.temporal.TemporalAdjusters
            .previousOrSame(java.time.DayOfWeek.MONDAY));
        completedOn(currentWeek, currentWeek.minusWeeks(1), currentWeek.minusWeeks(3));

        HabitResponse response = service.getById(HABIT_ID);

        assertThat(response.streakCount()).isEqualTo(2);
        assertThat(response.completionRate()).isEqualByComparingTo("60.00");
    }

    @Test
    void weeklyCheckInUsesTheCorrectWeekAcrossYearBoundary() {
        habit.setFrequency("WEEKLY");
        LocalDate sunday = LocalDate.of(2027, 1, 3);

        service.checkIn(HABIT_ID, new HabitCheckInRequest(sunday, true));

        verify(checkInRepository).existsByHabitIdAndUserIdAndCompletedTrueAndCheckInDateBetween(
            HABIT_ID, USER_ID, LocalDate.of(2026, 12, 28), sunday);
    }

    @Test
    void unsupportedAndCustomFrequenciesAreRejected() {
        assertThatThrownBy(() -> service.create(new HabitCreateRequest("Read", "STUDY", "CUSTOM", null)))
            .isInstanceOf(kh.edu.acleda.campuslink.shared.exception.ApiException.class)
            .hasMessageContaining("not configured");
        assertThatThrownBy(() -> service.create(new HabitCreateRequest("Read", "STUDY", "MONTHLY", null)))
            .isInstanceOf(kh.edu.acleda.campuslink.shared.exception.ApiException.class)
            .hasMessageContaining("DAILY or WEEKLY");
        verify(habitRepository, never()).save(any(Habit.class));
    }

    @Test
    void omittedCheckInDateUsesTheCambodiaCalendarDate() {
        service.checkIn(HABIT_ID, new HabitCheckInRequest(null, true));

        ArgumentCaptor<HabitCheckIn> captor = ArgumentCaptor.forClass(HabitCheckIn.class);
        verify(checkInRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCheckInDate()).isEqualTo(TODAY);
    }
}

package kh.edu.acleda.campuslink.modules.productivity;

import java.time.LocalDate;
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
import kh.edu.acleda.campuslink.modules.productivity.service.impl.HabitServiceImpl;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HabitServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID HABIT_ID = UUID.randomUUID();

    @Mock private HabitRepository habitRepository;
    @Mock private HabitCheckInRepository checkInRepository;
    @Mock private kh.edu.acleda.campuslink.modules.entertainment.service.RewardService rewardService;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private HabitServiceImpl service;
    private User user;
    private Habit habit;

    @BeforeEach
    void setUp() {
        service = new HabitServiceImpl(habitRepository, checkInRepository, rewardService, userRepository,
            currentUserProvider);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        habit = Habit.builder().user(user).title("Read").category("STUDY").frequency("DAILY").build();
        ReflectionTestUtils.setField(habit, "id", HABIT_ID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(habitRepository.save(any(Habit.class))).thenAnswer(i -> i.getArgument(0));
        when(habitRepository.findByIdAndUserIdAndDeletedAtIsNull(HABIT_ID, USER_ID)).thenReturn(Optional.of(habit));
        when(checkInRepository.save(any(HabitCheckIn.class))).thenAnswer(i -> i.getArgument(0));
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
        verify(checkInRepository).save(captor.capture());
        assertThat(captor.getValue().getCheckInDate()).isEqualTo(LocalDate.now());
        assertThat(captor.getValue().getCompleted()).isTrue();
    }

    @Test
    void checkInReusesTheExistingRowForThatDay() {
        HabitCheckIn existing = checkInOn(LocalDate.now(), false);
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        when(checkInRepository.findByHabitIdAndUserIdAndCheckInDate(HABIT_ID, USER_ID, LocalDate.now()))
            .thenReturn(Optional.of(existing));

        service.checkIn(HABIT_ID, new HabitCheckInRequest(null, true));

        ArgumentCaptor<HabitCheckIn> captor = ArgumentCaptor.forClass(HabitCheckIn.class);
        verify(checkInRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().getCompleted()).isTrue();
    }

    @Test
    void undoingACheckInMarksItNotCompleted() {
        service.checkIn(HABIT_ID, new HabitCheckInRequest(null, false));

        ArgumentCaptor<HabitCheckIn> captor = ArgumentCaptor.forClass(HabitCheckIn.class);
        verify(checkInRepository).save(captor.capture());
        assertThat(captor.getValue().getCompleted()).isFalse();
    }

    @Test
    void streakCountsConsecutiveCompletedDaysEndingToday() {
        LocalDate today = LocalDate.now();
        completedOn(today, today.minusDays(1), today.minusDays(2));

        HabitResponse r = service.getById(HABIT_ID);

        assertThat(r.streakCount()).isEqualTo(3);
        assertThat(r.checkedInToday()).isTrue();
    }

    @Test
    void streakStillCountsThroughYesterdayWhenTodayIsNotDoneYet() {
        LocalDate today = LocalDate.now();
        completedOn(today.minusDays(1), today.minusDays(2));

        HabitResponse r = service.getById(HABIT_ID);

        assertThat(r.streakCount()).isEqualTo(2);
        assertThat(r.checkedInToday()).isFalse();
    }

    @Test
    void streakBreaksOnAMissedDay() {
        LocalDate today = LocalDate.now();
        completedOn(today, today.minusDays(2)); // gap at yesterday

        assertThat(service.getById(HABIT_ID).streakCount()).isEqualTo(1);
    }

    @Test
    void completionRateIsCompletedDaysOverThirty() {
        LocalDate today = LocalDate.now();
        completedOn(today, today.minusDays(1), today.minusDays(2)); // 3 of 30

        assertThat(service.getById(HABIT_ID).completionRate()).isEqualByComparingTo("10.00");
    }

    @Test
    void oldCheckInsOutsideTheWindowDoNotCountTowardRate() {
        LocalDate today = LocalDate.now();
        completedOn(today, today.minusDays(40)); // one in window, one outside

        assertThat(service.getById(HABIT_ID).completionRate())
            .isEqualByComparingTo(new java.math.BigDecimal("100").divide(new java.math.BigDecimal("30"), 2, java.math.RoundingMode.DOWN));
    }

    @Test
    void unknownHabitReadsAsNotFound() {
        when(habitRepository.findByIdAndUserIdAndDeletedAtIsNull(HABIT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(HABIT_ID)).isInstanceOf(NotFoundException.class);
    }
}

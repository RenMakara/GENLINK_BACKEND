package kh.edu.acleda.campuslink.modules.bff;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.bff.service.MobileProductivityBffServiceImpl;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileProductivityDashboardResponse;
import kh.edu.acleda.campuslink.modules.productivity.entity.Goal;
import kh.edu.acleda.campuslink.modules.productivity.entity.Habit;
import kh.edu.acleda.campuslink.modules.productivity.entity.HabitCheckIn;
import kh.edu.acleda.campuslink.modules.productivity.entity.MotivationContent;
import kh.edu.acleda.campuslink.modules.productivity.repository.FocusDashboardSummary;
import kh.edu.acleda.campuslink.modules.productivity.repository.FocusSessionRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.GoalRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.HabitCheckInRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.HabitRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.MotivationContentRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.TaskDashboardCounts;
import kh.edu.acleda.campuslink.modules.productivity.repository.TaskRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MobileProductivityBffServiceImplTest {

    private static final Language EN = Language.ENGLISH;
    private static final String EMAIL = "dara@example.com";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    private static final Clock TEST_CLOCK = Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneOffset.UTC);

    @Mock private UserRepository userRepository;
    @Mock private HabitRepository habitRepository;
    @Mock private HabitCheckInRepository habitCheckInRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private FocusSessionRepository focusSessionRepository;
    @Mock private MotivationContentRepository motivationRepository;

    private MobileProductivityBffServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new MobileProductivityBffServiceImpl(userRepository, habitRepository, habitCheckInRepository,
            taskRepository, goalRepository, focusSessionRepository, motivationRepository, TEST_CLOCK);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(habitRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());
        when(habitCheckInRepository.findByUserIdAndCheckInDateAndCompletedTrue(eq(USER_ID), any()))
            .thenReturn(List.of());
        when(taskRepository.summarizeForDashboard(eq(USER_ID), any(), any(), any()))
            .thenReturn(new TaskDashboardCounts(0L, 0L, 0L, 0L));
        when(goalRepository.findTop5ByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(USER_ID))
            .thenReturn(List.of());
        when(focusSessionRepository.summarizeForDashboard(eq(USER_ID), any(), any()))
            .thenReturn(new FocusDashboardSummary(0L, 0L, 0L));
        when(motivationRepository.findByDeletedAtIsNull()).thenReturn(List.of());
    }

    private Habit habit(String title) {
        Habit h = Habit.builder().user(user).title(title).build();
        ReflectionTestUtils.setField(h, "id", UUID.randomUUID());
        return h;
    }

    @Test
    void aBrandNewUserGetsAWellFormedBody() {
        MobileProductivityDashboardResponse r = service.getDashboard(EMAIL, EN);

        assertThat(r.habits().total()).isZero();
        assertThat(r.habits().items()).isEmpty();
        assertThat(r.tasks().total()).isZero();
        assertThat(r.goals()).isEmpty();
        assertThat(r.focus().todaySessions()).isZero();
        assertThat(r.motivation()).isNull();
    }

    @Test
    void habitsReflectTodaysCheckIns() {
        Habit h1 = habit("Read");
        Habit h2 = habit("Run");
        when(habitRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(USER_ID))
            .thenReturn(List.of(h1, h2));
        HabitCheckIn done = HabitCheckIn.builder().habit(h1).user(user)
            .checkInDate(TODAY).completed(true).build();
        when(habitCheckInRepository.findByUserIdAndCheckInDateAndCompletedTrue(eq(USER_ID), any()))
            .thenReturn(List.of(done));

        MobileProductivityDashboardResponse.HabitsToday habits = service.getDashboard(EMAIL, EN).habits();

        assertThat(habits.total()).isEqualTo(2);
        assertThat(habits.checkedInToday()).isEqualTo(1);
        assertThat(habits.items()).filteredOn(MobileProductivityDashboardResponse.HabitItem::checkedInToday)
            .hasSize(1);
    }

    @Test
    void tasksSummaryCountsCompletedDueTodayAndOverdue() {
        when(taskRepository.summarizeForDashboard(eq(USER_ID), any(), any(), any()))
            .thenReturn(new TaskDashboardCounts(3L, 1L, 1L, 1L));

        MobileProductivityDashboardResponse.TasksSummary tasks = service.getDashboard(EMAIL, EN).tasks();

        assertThat(tasks.total()).isEqualTo(3);
        assertThat(tasks.completed()).isEqualTo(1);
        assertThat(tasks.dueToday()).isEqualTo(1);
        assertThat(tasks.overdue()).isEqualTo(1);
    }

    @Test
    void goalsUseTheStoredSyncedProgress() {
        Goal g = Goal.builder().user(user).title("Ship app")
            .progressPercentage(new BigDecimal("50.00")).status("IN_PROGRESS").build();
        ReflectionTestUtils.setField(g, "id", UUID.randomUUID());
        when(goalRepository.findTop5ByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(g));

        MobileProductivityDashboardResponse.GoalProgress gp = service.getDashboard(EMAIL, EN).goals().get(0);

        assertThat(gp.progressPercentage()).isEqualByComparingTo("50.00");
        assertThat(gp.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void focusAggregatesTodayMinutesAndTotalXp() {
        when(focusSessionRepository.summarizeForDashboard(eq(USER_ID), any(), any()))
            .thenReturn(new FocusDashboardSummary(1L, 25L * 60L, 15L));

        MobileProductivityDashboardResponse.FocusSummary focus = service.getDashboard(EMAIL, EN).focus();

        assertThat(focus.todaySessions()).isEqualTo(1);
        assertThat(focus.todayMinutes()).isEqualTo(25);
        assertThat(focus.totalXp()).isEqualTo(15);
    }

    @Test
    void motivationCardIsPresentWhenSeeded() {
        MotivationContent m = MotivationContent.builder().titleEn("Start small").contentEn("Begin now").build();
        ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
        when(motivationRepository.findByDeletedAtIsNull()).thenReturn(List.of(m));

        assertThat(service.getDashboard(EMAIL, EN).motivation()).isNotNull();
    }

    @Test
    void unknownUserThrowsNotFound() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDashboard(EMAIL, EN)).isInstanceOf(NotFoundException.class);
    }
}

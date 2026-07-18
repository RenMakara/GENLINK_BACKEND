package kh.edu.acleda.campuslink.modules.bff.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileProductivityDashboardResponse;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileProductivityDashboardResponse.FocusSummary;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileProductivityDashboardResponse.GoalProgress;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileProductivityDashboardResponse.HabitItem;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileProductivityDashboardResponse.HabitsToday;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileProductivityDashboardResponse.MotivationCard;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileProductivityDashboardResponse.TasksSummary;
import kh.edu.acleda.campuslink.modules.productivity.entity.FocusSession;
import kh.edu.acleda.campuslink.modules.productivity.entity.Goal;
import kh.edu.acleda.campuslink.modules.productivity.entity.Habit;
import kh.edu.acleda.campuslink.modules.productivity.entity.HabitCheckIn;
import kh.edu.acleda.campuslink.modules.productivity.entity.MotivationContent;
import kh.edu.acleda.campuslink.modules.productivity.entity.Task;
import kh.edu.acleda.campuslink.modules.productivity.repository.FocusSessionRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.GoalRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.HabitCheckInRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.HabitRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.MotivationContentRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.TaskRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.localization.LocalizedTextSelector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates the Productivity screen. Reads the productivity repositories directly (the
 * BFF layer is where cross-entity coupling belongs) and resolves the user by email, like
 * the home BFF.
 *
 * <p>Goal progress is read from the stored goal columns, which Phase 3 keeps in sync on
 * every milestone change - so the dashboard avoids a per-goal milestone query.
 */
@Service
public class MobileProductivityBffServiceImpl implements MobileProductivityBffService {

    private static final int TOP_GOALS = 5;
    private static final int HABIT_ITEMS = 20;

    private final UserRepository userRepository;
    private final HabitRepository habitRepository;
    private final HabitCheckInRepository habitCheckInRepository;
    private final TaskRepository taskRepository;
    private final GoalRepository goalRepository;
    private final FocusSessionRepository focusSessionRepository;
    private final MotivationContentRepository motivationRepository;

    public MobileProductivityBffServiceImpl(UserRepository userRepository,
                                            HabitRepository habitRepository,
                                            HabitCheckInRepository habitCheckInRepository,
                                            TaskRepository taskRepository,
                                            GoalRepository goalRepository,
                                            FocusSessionRepository focusSessionRepository,
                                            MotivationContentRepository motivationRepository) {
        this.userRepository = userRepository;
        this.habitRepository = habitRepository;
        this.habitCheckInRepository = habitCheckInRepository;
        this.taskRepository = taskRepository;
        this.goalRepository = goalRepository;
        this.focusSessionRepository = focusSessionRepository;
        this.motivationRepository = motivationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public MobileProductivityDashboardResponse getDashboard(String email, Language language) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new NotFoundException("User not found"));
        UUID userId = user.getId();
        LocalDate today = LocalDate.now();

        return new MobileProductivityDashboardResponse(
            habitsToday(userId, today),
            tasksSummary(userId, today),
            goals(userId),
            focus(userId, today),
            motivation(language));
    }

    private HabitsToday habitsToday(UUID userId, LocalDate today) {
        Set<UUID> doneToday = new HashSet<>();
        for (HabitCheckIn c : habitCheckInRepository.findByUserIdAndCheckInDateAndCompletedTrue(userId, today)) {
            if (c.getHabit() != null) {
                doneToday.add(c.getHabit().getId());
            }
        }
        List<Habit> habits = habitRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
        List<HabitItem> items = habits.stream()
            .limit(HABIT_ITEMS)
            .map(h -> new HabitItem(h.getId(), h.getTitle(), doneToday.contains(h.getId())))
            .toList();
        int checkedIn = (int) habits.stream().filter(h -> doneToday.contains(h.getId())).count();
        return new HabitsToday(habits.size(), checkedIn, items);
    }

    private TasksSummary tasksSummary(UUID userId, LocalDate today) {
        LocalDateTime now = LocalDateTime.now();
        List<Task> tasks = taskRepository.findByUserIdAndDeletedAtIsNullOrderByCompletedAscDueDateAsc(userId);
        long completed = 0;
        int dueToday = 0;
        int overdue = 0;
        for (Task t : tasks) {
            boolean done = Boolean.TRUE.equals(t.getCompleted());
            if (done) {
                completed++;
            }
            LocalDateTime due = t.getDueDate();
            if (due != null && due.toLocalDate().equals(today)) {
                dueToday++;
            }
            if (due != null && due.isBefore(now) && !done) {
                overdue++;
            }
        }
        return new TasksSummary(tasks.size(), completed, dueToday, overdue);
    }

    private List<GoalProgress> goals(UUID userId) {
        return goalRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId).stream()
            .limit(TOP_GOALS)
            .map(this::toGoalProgress)
            .toList();
    }

    private GoalProgress toGoalProgress(Goal g) {
        BigDecimal progress = g.getProgressPercentage() == null ? BigDecimal.ZERO : g.getProgressPercentage();
        String status = g.getStatus() == null ? "NOT_STARTED" : g.getStatus();
        return new GoalProgress(g.getId(), g.getTitle(), progress, status);
    }

    private FocusSummary focus(UUID userId, LocalDate today) {
        List<FocusSession> completed = focusSessionRepository.findByUserIdAndCompletedTrue(userId);
        long totalXp = completed.stream().mapToLong(s -> orZero(s.getXpEarned())).sum();
        List<FocusSession> todays = completed.stream()
            .filter(s -> s.getStartedAt() != null && s.getStartedAt().toLocalDate().equals(today))
            .toList();
        int todayMinutes = todays.stream().mapToInt(s -> orZero(s.getDurationMinutes())).sum();
        return new FocusSummary(todays.size(), todayMinutes, totalXp);
    }

    /**
     * One motivation card, rotated by day of year (stable on refresh) over a stable order,
     * mirroring the home dashboard. Null when nothing is seeded.
     */
    private MotivationCard motivation(Language language) {
        List<MotivationContent> contents = motivationRepository.findByDeletedAtIsNull();
        if (contents.isEmpty()) {
            return null;
        }
        List<MotivationContent> ordered = new ArrayList<>(contents);
        ordered.sort((a, b) -> a.getId().compareTo(b.getId()));
        MotivationContent pick = ordered.get(LocalDate.now().getDayOfYear() % ordered.size());
        return new MotivationCard(pick.getId(),
            LocalizedTextSelector.select(pick.getTitleEn(), pick.getTitleKm(), language),
            LocalizedTextSelector.select(pick.getContentEn(), pick.getContentKm(), language));
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}

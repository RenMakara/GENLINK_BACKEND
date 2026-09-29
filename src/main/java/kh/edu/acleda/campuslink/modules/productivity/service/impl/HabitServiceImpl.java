package kh.edu.acleda.campuslink.modules.productivity.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.HabitCheckInRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.HabitCreateRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.HabitResponse;
import kh.edu.acleda.campuslink.modules.productivity.entity.Habit;
import kh.edu.acleda.campuslink.modules.productivity.entity.HabitCheckIn;
import kh.edu.acleda.campuslink.modules.productivity.repository.HabitCheckInRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.HabitRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.XpHistoryRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.RewardService;
import kh.edu.acleda.campuslink.modules.productivity.service.HabitService;
import kh.edu.acleda.campuslink.modules.productivity.service.HabitStreakNotifier;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HabitServiceImpl implements HabitService {

    /** completionRate is measured over this trailing window of days. */
    private static final int RATE_WINDOW_DAYS = 30;
    private static final String XP_SOURCE = "HABIT";
    private static final int CHECK_IN_XP = 5;
    private static final ZoneId PRODUCTIVITY_ZONE = ZoneId.of("Asia/Phnom_Penh");

    private final HabitRepository habitRepository;
    private final HabitCheckInRepository checkInRepository;
    private final RewardService rewardService;
    private final HabitStreakNotifier habitStreakNotifier;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final XpHistoryRepository xpHistoryRepository;

    public HabitServiceImpl(HabitRepository habitRepository,
                            HabitCheckInRepository checkInRepository,
                            RewardService rewardService,
                            HabitStreakNotifier habitStreakNotifier,
                            UserRepository userRepository,
                            CurrentUserProvider currentUserProvider,
                            XpHistoryRepository xpHistoryRepository) {
        this.habitRepository = habitRepository;
        this.checkInRepository = checkInRepository;
        this.rewardService = rewardService;
        this.habitStreakNotifier = habitStreakNotifier;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.xpHistoryRepository = xpHistoryRepository;
    }

    @Override
    @Transactional
    public HabitResponse create(HabitCreateRequest request) {
        User user = loadCurrentUser();
        String frequency = normalizeFrequency(request.frequency());
        Habit habit = habitRepository.save(Habit.builder()
            .user(user)
            .title(request.title())
            .category(request.category())
            .frequency(frequency)
            .reminderTime(request.reminderTime())
            // Stored counters satisfy the NOT NULL column but are recomputed on read.
            .streakCount(0)
            .completionRate(BigDecimal.ZERO)
            .build());
        return toResponse(habit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HabitResponse> getMine() {
        UUID userId = currentUserProvider.getCurrentUserId();
        return habitRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HabitResponse getById(UUID id) {
        return toResponse(loadOwned(id));
    }

    @Override
    @Transactional
    public HabitResponse update(UUID id, HabitCreateRequest request) {
        Habit habit = loadOwned(id);
        String frequency = normalizeFrequency(request.frequency());
        habit.setTitle(request.title());
        habit.setCategory(request.category());
        habit.setFrequency(frequency);
        habit.setReminderTime(request.reminderTime());
        habitRepository.save(habit);
        return toResponse(habit);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Habit habit = loadOwned(id);
        habit.setDeletedAt(Instant.now());
        habitRepository.save(habit);
    }

    @Override
    @Transactional
    public HabitResponse checkIn(UUID id, HabitCheckInRequest request) {
        UUID userId = currentUserProvider.getCurrentUserId();
        Habit habit = habitRepository.findByIdAndUserIdAndDeletedAtIsNullForUpdate(id, userId)
            .orElseThrow(() -> new NotFoundException("Habit not found"));
        String frequency = normalizeFrequency(habit.getFrequency());
        LocalDate date = request.date() == null ? LocalDate.now(PRODUCTIVITY_ZONE) : request.date();
        boolean completed = request.completed() == null || request.completed();

        // Idempotent per (habit, user, date): update the existing row instead of a second
        // insert, which the unique constraint would reject anyway.
        HabitCheckIn checkIn = checkInRepository.findByHabitIdAndUserIdAndCheckInDate(id, userId, date)
            .orElseGet(() -> HabitCheckIn.builder().habit(habit).user(habit.getUser()).checkInDate(date).build());
        boolean wasCompleted = Boolean.TRUE.equals(checkIn.getCompleted());
        if (completed && "WEEKLY".equals(frequency) && !wasCompleted) {
            LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate weekEnd = weekStart.plusDays(6);
            if (checkInRepository.existsByHabitIdAndUserIdAndCompletedTrueAndCheckInDateBetween(
                    id, userId, weekStart, weekEnd)) {
                throw new ApiException("This weekly habit is already complete for this calendar week",
                    "HABIT_WEEK_ALREADY_COMPLETED");
            }
        }
        checkIn.setCompleted(completed);
        checkIn = checkInRepository.saveAndFlush(checkIn);

        // XP only on the transition to completed, so re-checking the same day pays once.
        if (completed && !wasCompleted) {
            // The check-in row is the stable per-day idempotency key. Undoing and
            // re-completing the same date therefore cannot award XP twice.
            rewardService.award(loadCurrentUser(), XP_SOURCE, checkIn.getId(), CHECK_IN_XP);
        }
        HabitResponse response = toResponse(habit);
        if (completed && !wasCompleted) {
            habitStreakNotifier.notifyForCompletedCheckIn(habit, date, response.streakCount());
        }
        return response;
    }

    /** Builds the response with streak and rate derived live from check-ins. */
    private HabitResponse toResponse(Habit habit) {
        Set<LocalDate> completedDays = completedDays(habit.getId());
        LocalDate today = LocalDate.now(PRODUCTIVITY_ZONE);
        String frequency = habit.getFrequency() == null ? "DAILY" : habit.getFrequency().trim().toUpperCase();
        boolean configured = "DAILY".equals(frequency) || "WEEKLY".equals(frequency);
        UUID userId = currentUserProvider.getCurrentUserId();
        LocalDate weekStart = weekStart(today);
        List<HabitCheckIn> xpCheckIns = "WEEKLY".equals(frequency)
            ? checkInRepository.findByHabitIdAndUserIdAndCheckInDateBetweenOrderByCheckInDateDesc(
                habit.getId(), userId, weekStart, weekStart.plusDays(6))
            : checkInRepository.findByHabitIdAndUserIdAndCheckInDate(habit.getId(), userId, today)
                .map(List::of).orElseGet(List::of);
        int xpEarned = 0;
        for (HabitCheckIn checkIn : xpCheckIns) {
            xpEarned += xpHistoryRepository.findByUserIdAndSourceTypeAndSourceId(
                userId, XP_SOURCE, checkIn.getId()).map(xp -> xp.getXpAmount()).orElse(0);
        }
        return new HabitResponse(habit.getId(), habit.getTitle(), habit.getCategory(),
            frequency, habit.getReminderTime(),
            configured ? currentStreak(completedDays, today, frequency) : 0,
            configured ? completionRate(completedDays, today, frequency) : BigDecimal.ZERO,
            completedDays.contains(today), containsWeek(completedDays, weekStart(today)), xpEarned);
    }

    private Set<LocalDate> completedDays(UUID habitId) {
        Set<LocalDate> days = new HashSet<>();
        for (HabitCheckIn c : checkInRepository.findByHabitIdAndUserIdOrderByCheckInDateDesc(
                habitId, currentUserProvider.getCurrentUserId())) {
            if (Boolean.TRUE.equals(c.getCompleted())) {
                days.add(c.getCheckInDate());
            }
        }
        return days;
    }

    /**
     * Consecutive completed days ending today, or yesterday when today is not yet done -
     * so an unbroken streak is not "lost" just because the user has not checked in yet
     * today.
     */
    private int currentStreak(Set<LocalDate> completedDays, LocalDate today, String frequency) {
        if ("WEEKLY".equals(frequency)) {
            LocalDate cursor = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (!containsWeek(completedDays, cursor)) {
                cursor = cursor.minusWeeks(1);
            }
            int streak = 0;
            while (containsWeek(completedDays, cursor)) {
                streak++;
                cursor = cursor.minusWeeks(1);
            }
            return streak;
        }
        LocalDate cursor = completedDays.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (completedDays.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private BigDecimal completionRate(Set<LocalDate> completedDays, LocalDate today, String frequency) {
        if ("WEEKLY".equals(frequency)) {
            LocalDate currentWeek = weekStart(today);
            Set<LocalDate> completedWeeks = new HashSet<>();
            for (LocalDate day : completedDays) {
                LocalDate week = weekStart(day);
                if (!week.isAfter(currentWeek) && !week.isBefore(currentWeek.minusWeeks(4))) {
                    completedWeeks.add(week);
                }
            }
            return BigDecimal.valueOf(completedWeeks.size() * 100L)
                .divide(BigDecimal.valueOf(5), 2, RoundingMode.DOWN);
        }
        LocalDate windowStart = today.minusDays(RATE_WINDOW_DAYS - 1L);
        long done = completedDays.stream()
            .filter(d -> !d.isBefore(windowStart) && !d.isAfter(today))
            .count();
        return BigDecimal.valueOf(done * 100L)
            .divide(BigDecimal.valueOf(RATE_WINDOW_DAYS), 2, RoundingMode.DOWN);
    }

    private LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private boolean containsWeek(Set<LocalDate> completedDays, LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        return completedDays.stream().anyMatch(day -> !day.isBefore(weekStart) && !day.isAfter(weekEnd));
    }

    private String normalizeFrequency(String value) {
        String frequency = value == null || value.isBlank() ? "DAILY" : value.trim().toUpperCase();
        if ("CUSTOM".equals(frequency)) {
            throw new ApiException("Custom habit schedules are not configured yet", "HABIT_CUSTOM_FREQUENCY_UNAVAILABLE");
        }
        if (!"DAILY".equals(frequency) && !"WEEKLY".equals(frequency)) {
            throw new ApiException("Frequency must be DAILY or WEEKLY", "HABIT_INVALID_FREQUENCY");
        }
        return frequency;
    }

    private Habit loadOwned(UUID id) {
        return habitRepository.findByIdAndUserIdAndDeletedAtIsNull(id, currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Habit not found"));
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }
}

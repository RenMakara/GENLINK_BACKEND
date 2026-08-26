package kh.edu.acleda.campuslink.modules.productivity.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
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
import kh.edu.acleda.campuslink.modules.entertainment.service.RewardService;
import kh.edu.acleda.campuslink.modules.productivity.service.HabitService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HabitServiceImpl implements HabitService {

    /** completionRate is measured over this trailing window of days. */
    private static final int RATE_WINDOW_DAYS = 30;
    private static final String XP_SOURCE = "HABIT";
    private static final int CHECK_IN_XP = 5;

    private final HabitRepository habitRepository;
    private final HabitCheckInRepository checkInRepository;
    private final RewardService rewardService;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public HabitServiceImpl(HabitRepository habitRepository,
                            HabitCheckInRepository checkInRepository,
                            RewardService rewardService,
                            UserRepository userRepository,
                            CurrentUserProvider currentUserProvider) {
        this.habitRepository = habitRepository;
        this.checkInRepository = checkInRepository;
        this.rewardService = rewardService;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public HabitResponse create(HabitCreateRequest request) {
        User user = loadCurrentUser();
        Habit habit = habitRepository.save(Habit.builder()
            .user(user)
            .title(request.title())
            .category(request.category())
            .frequency(request.frequency())
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
        habit.setTitle(request.title());
        habit.setCategory(request.category());
        habit.setFrequency(request.frequency());
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
        Habit habit = loadOwned(id);
        UUID userId = currentUserProvider.getCurrentUserId();
        LocalDate date = request.date() == null ? LocalDate.now() : request.date();
        boolean completed = request.completed() == null || request.completed();

        // Idempotent per (habit, user, date): update the existing row instead of a second
        // insert, which the unique constraint would reject anyway.
        HabitCheckIn checkIn = checkInRepository.findByHabitIdAndUserIdAndCheckInDate(id, userId, date)
            .orElseGet(() -> HabitCheckIn.builder().habit(habit).user(habit.getUser()).checkInDate(date).build());
        boolean wasCompleted = Boolean.TRUE.equals(checkIn.getCompleted());
        checkIn.setCompleted(completed);
        checkIn = checkInRepository.saveAndFlush(checkIn);

        // XP only on the transition to completed, so re-checking the same day pays once.
        if (completed && !wasCompleted) {
            // The check-in row is the stable per-day idempotency key. Undoing and
            // re-completing the same date therefore cannot award XP twice.
            rewardService.award(loadCurrentUser(), XP_SOURCE, checkIn.getId(), CHECK_IN_XP);
        }
        return toResponse(habit);
    }

    /** Builds the response with streak and rate derived live from check-ins. */
    private HabitResponse toResponse(Habit habit) {
        Set<LocalDate> completedDays = completedDays(habit.getId());
        LocalDate today = LocalDate.now();
        return new HabitResponse(habit.getId(), habit.getTitle(), habit.getCategory(),
            habit.getFrequency(), habit.getReminderTime(),
            currentStreak(completedDays, today), completionRate(completedDays, today),
            completedDays.contains(today));
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
    private int currentStreak(Set<LocalDate> completedDays, LocalDate today) {
        LocalDate cursor = completedDays.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (completedDays.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private BigDecimal completionRate(Set<LocalDate> completedDays, LocalDate today) {
        LocalDate windowStart = today.minusDays(RATE_WINDOW_DAYS - 1L);
        long done = completedDays.stream()
            .filter(d -> !d.isBefore(windowStart) && !d.isAfter(today))
            .count();
        return BigDecimal.valueOf(done * 100L)
            .divide(BigDecimal.valueOf(RATE_WINDOW_DAYS), 2, RoundingMode.DOWN);
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

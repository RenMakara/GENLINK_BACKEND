package kh.edu.acleda.campuslink.modules.productivity;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kh.edu.acleda.campuslink.modules.entertainment.repository.XpHistoryRepository;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.FocusSessionStartRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.request.HabitCheckInRequest;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.FocusSessionResponse;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.HabitResponse;
import kh.edu.acleda.campuslink.modules.productivity.entity.FocusSessionStatus;
import kh.edu.acleda.campuslink.modules.productivity.entity.Habit;
import kh.edu.acleda.campuslink.modules.productivity.repository.FocusSessionRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.HabitCheckInRepository;
import kh.edu.acleda.campuslink.modules.productivity.repository.HabitRepository;
import kh.edu.acleda.campuslink.modules.productivity.service.FocusSessionService;
import kh.edu.acleda.campuslink.modules.productivity.service.HabitService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProductivityConcurrencyIntegrationTest {

    @Autowired private HabitService habitService;
    @Autowired private HabitRepository habitRepository;
    @Autowired private HabitCheckInRepository habitCheckInRepository;
    @Autowired private FocusSessionService focusSessionService;
    @Autowired private FocusSessionRepository focusSessionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private XpHistoryRepository xpHistoryRepository;

    @Test
    void concurrentHabitCheckInsReturnOnePersistedCompletionAndOneReward() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User user = userRepository.saveAndFlush(user("habit-" + suffix));
        Habit habit = habitRepository.saveAndFlush(Habit.builder()
            .user(user)
            .title("Concurrent habit " + suffix)
            .frequency("DAILY")
            .streakCount(0)
            .build());
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Phnom_Penh"));

        List<HabitResponse> responses = runTogether(user.getEmail(),
            () -> habitService.checkIn(habit.getId(), new HabitCheckInRequest(date, true)),
            () -> habitService.checkIn(habit.getId(), new HabitCheckInRequest(date, true)));

        assertThat(responses).hasSize(2).allSatisfy(response -> {
            assertThat(response.habitId()).isEqualTo(habit.getId());
            assertThat(response.checkedInToday()).isTrue();
        });
        assertThat(habitCheckInRepository.findByHabitIdAndUserIdOrderByCheckInDateDesc(
            habit.getId(), user.getId()))
            .filteredOn(checkIn -> date.equals(checkIn.getCheckInDate())
                && Boolean.TRUE.equals(checkIn.getCompleted()))
            .hasSize(1);
        assertThat(xpHistoryRepository.countByUserIdAndSourceType(user.getId(), "HABIT"))
            .isEqualTo(1);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getTotalXp()).isEqualTo(5);
    }

    @Test
    void concurrentFocusStartsReturnTheSameActiveSession() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User user = userRepository.saveAndFlush(user("focus-" + suffix));

        List<FocusSessionResponse> responses = runTogether(user.getEmail(),
            () -> focusSessionService.start(new FocusSessionStartRequest("POMODORO", 25, null)),
            () -> focusSessionService.start(new FocusSessionStartRequest("DEEP_FOCUS", 50, null)));

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).sessionId()).isEqualTo(responses.get(1).sessionId());
        assertThat(focusSessionRepository.countByUserIdAndStatusIn(user.getId(),
            List.of(FocusSessionStatus.ACTIVE, FocusSessionStatus.PAUSED))).isEqualTo(1);
    }

    private <T> List<T> runTogether(String email, CheckedSupplier<T> firstWork,
                                    CheckedSupplier<T> secondWork) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<T> first = executor.submit(() -> executeAsUser(email, ready, start, firstWork));
            Future<T> second = executor.submit(() -> executeAsUser(email, ready, start, secondWork));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private <T> T executeAsUser(String email, CountDownLatch ready, CountDownLatch start,
                                CheckedSupplier<T> work) throws Exception {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(email, "", List.of()));
        SecurityContextHolder.setContext(context);
        try {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test did not start");
            }
            return work.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private User user(String username) {
        User user = new User();
        user.setEmail(username + "@example.com");
        user.setUsername(username);
        user.setFullName(username);
        return user;
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}

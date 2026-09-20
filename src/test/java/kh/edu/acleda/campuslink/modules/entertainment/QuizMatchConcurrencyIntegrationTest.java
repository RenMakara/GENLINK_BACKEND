package kh.edu.acleda.campuslink.modules.entertainment;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kh.edu.acleda.campuslink.modules.entertainment.entity.Quiz;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizMatch;
import kh.edu.acleda.campuslink.modules.entertainment.enums.QuizDifficulty;
import kh.edu.acleda.campuslink.modules.entertainment.enums.QuizMatchStatus;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizMatchRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.XpHistoryRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.QuizMatchService;
import kh.edu.acleda.campuslink.modules.entertainment.service.RewardService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class QuizMatchConcurrencyIntegrationTest {

    @Autowired private QuizMatchService matchService;
    @Autowired private QuizMatchRepository matchRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private XpHistoryRepository xpHistoryRepository;
    @Autowired private RewardService rewardService;

    @Test
    void concurrentFinishAwardsWinnerOnlyOnce() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString();
        User p1 = userRepository.save(user("p1-" + suffix, "P1 " + suffix));
        User p2 = userRepository.save(user("p2-" + suffix, "P2 " + suffix));
        Quiz quiz = quizRepository.save(Quiz.builder()
            .titleEn("Concurrent quiz " + suffix)
            .difficulty(QuizDifficulty.EASY)
            .xpReward(10)
            .build());
        QuizMatch match = new QuizMatch();
        match.setQuiz(quiz);
        match.setPlayer1(p1);
        match.setPlayer2(p2);
        match.setPlayer1Score(20);
        match.setPlayer2Score(10);
        match.setStatus(QuizMatchStatus.IN_PROGRESS);
        match = matchRepository.saveAndFlush(match);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final QuizMatch persisted = match;
            Future<?> first = executor.submit(() -> finishTogether(persisted, p1, ready, start));
            Future<?> second = executor.submit(() -> finishTogether(persisted, p2, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(matchRepository.findById(match.getId()).orElseThrow().getStatus())
            .isEqualTo(QuizMatchStatus.COMPLETED);
        assertThat(xpHistoryRepository.countByUserIdAndSourceType(p1.getId(), "QUIZ_MATCH")).isEqualTo(1);
        assertThat(userRepository.findById(p1.getId()).orElseThrow().getTotalXp()).isEqualTo(50);
        assertThat(userRepository.findById(p2.getId()).orElseThrow().getTotalXp()).isZero();
    }

    @Test
    void taskAndHabitSourceRetriesCannotFarmXp() {
        String suffix = java.util.UUID.randomUUID().toString();
        User user = userRepository.save(user("xp-" + suffix, "XP " + suffix));
        java.util.UUID taskId = java.util.UUID.randomUUID();
        java.util.UUID dailyCheckInId = java.util.UUID.randomUUID();

        rewardService.award(user, "TASK", taskId, 10);
        rewardService.award(user, "TASK", taskId, 10);
        rewardService.award(user, "HABIT", dailyCheckInId, 5);
        rewardService.award(user, "HABIT", dailyCheckInId, 5);

        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(refreshed.getTotalXp()).isEqualTo(15);
        assertThat(xpHistoryRepository.countByUserIdAndSourceType(user.getId(), "TASK")).isEqualTo(1);
        assertThat(xpHistoryRepository.countByUserIdAndSourceType(user.getId(), "HABIT")).isEqualTo(1);
    }

    @Test
    void concurrentQuizRewardsForTheSameSourceAwardXpOnlyOnce() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString();
        User user = userRepository.save(user("quiz-xp-" + suffix, "Quiz XP " + suffix));
        java.util.UUID quizId = java.util.UUID.randomUUID();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> awardTogether(user, quizId, ready, start));
            Future<Integer> second = executor.submit(() -> awardTogether(user, quizId, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(java.util.List.of(
                first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(0, 30);
        } finally {
            executor.shutdownNow();
        }

        assertThat(userRepository.findById(user.getId()).orElseThrow().getTotalXp()).isEqualTo(30);
        assertThat(xpHistoryRepository.countByUserIdAndSourceType(user.getId(), "QUIZ")).isEqualTo(1);
    }

    private void finishTogether(QuizMatch match, User player, CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test did not start");
            }
            matchService.finishMatch(match.getId(), player.getId());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private int awardTogether(User user, java.util.UUID quizId,
                              CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent XP test did not start");
            }
            return rewardService.award(user, "QUIZ", quizId, 30);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private User user(String username, String fullName) {
        User user = new User();
        user.setEmail(username + "@example.com");
        user.setUsername(username);
        user.setFullName(fullName);
        return user;
    }
}

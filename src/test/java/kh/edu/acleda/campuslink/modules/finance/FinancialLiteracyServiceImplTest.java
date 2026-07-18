package kh.edu.acleda.campuslink.modules.finance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.repository.XpHistoryRepository;
import kh.edu.acleda.campuslink.modules.finance.dto.request.QuizSubmitRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.request.QuizSubmitRequest.Answer;
import kh.edu.acleda.campuslink.modules.finance.dto.response.QuizResultResponse;
import kh.edu.acleda.campuslink.modules.finance.entity.FinanceQuizAttempt;
import kh.edu.acleda.campuslink.modules.finance.entity.FinancialLesson;
import kh.edu.acleda.campuslink.modules.finance.entity.FinancialQuizQuestion;
import kh.edu.acleda.campuslink.modules.finance.entity.LessonProgress;
import kh.edu.acleda.campuslink.modules.finance.enums.LiteracyTopic;
import kh.edu.acleda.campuslink.modules.finance.repository.FinanceQuizAttemptRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.FinancialLessonRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.FinancialQuizQuestionRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.LessonProgressRepository;
import kh.edu.acleda.campuslink.modules.finance.service.DemoWalletService;
import kh.edu.acleda.campuslink.modules.finance.service.impl.FinancialLiteracyServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinancialLiteracyServiceImplTest {

    private static final Language EN = Language.ENGLISH;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID LESSON_ID = UUID.randomUUID();
    private static final UUID Q1 = UUID.randomUUID();
    private static final UUID Q2 = UUID.randomUUID();

    @Mock private FinancialLessonRepository lessonRepository;
    @Mock private FinancialQuizQuestionRepository questionRepository;
    @Mock private LessonProgressRepository progressRepository;
    @Mock private FinanceQuizAttemptRepository attemptRepository;
    @Mock private XpHistoryRepository xpHistoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private DemoWalletService demoWalletService;
    @Mock private CurrentUserProvider currentUserProvider;

    private FinancialLiteracyServiceImpl service;
    private User user;
    private FinancialLesson lesson;

    @BeforeEach
    void setUp() {
        service = new FinancialLiteracyServiceImpl(lessonRepository, questionRepository, progressRepository,
            attemptRepository, xpHistoryRepository, userRepository, demoWalletService, currentUserProvider);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "totalXp", 0);

        lesson = FinancialLesson.builder().titleEn("Saving").contentEn("body")
            .topic("SAVING_MONEY").contentType("ARTICLE").xpReward(20).build();
        ReflectionTestUtils.setField(lesson, "id", LESSON_ID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(lessonRepository.findByIdAndDeletedAtIsNull(LESSON_ID)).thenReturn(Optional.of(lesson));
        when(progressRepository.findByUserIdAndLessonId(USER_ID, LESSON_ID)).thenReturn(Optional.empty());
        when(progressRepository.save(any(LessonProgress.class))).thenAnswer(i -> i.getArgument(0));
        when(attemptRepository.save(any(FinanceQuizAttempt.class))).thenAnswer(i -> i.getArgument(0));
        when(questionRepository.findByLessonIdOrderByCreatedAtAsc(LESSON_ID))
            .thenReturn(List.of(question(Q1, "B"), question(Q2, "A")));
    }

    private FinancialQuizQuestion question(UUID id, String correct) {
        FinancialQuizQuestion q = FinancialQuizQuestion.builder().lesson(lesson)
            .questionTextEn("Q?").optionAEn("a").optionBEn("b").optionCEn("c").optionDEn("d")
            .correctAnswer(correct).explanationEn("because").build();
        ReflectionTestUtils.setField(q, "id", id);
        return q;
    }

    private QuizSubmitRequest answers(String a1, String a2) {
        return new QuizSubmitRequest(List.of(new Answer(Q1, a1), new Answer(Q2, a2)));
    }

    @Test
    void dashboardListsAllSixTopicsEvenWithNoLessons() {
        when(lessonRepository.findByDeletedAtIsNullOrderByTopicAscCreatedAtAsc()).thenReturn(List.of());
        when(progressRepository.findByUserId(USER_ID)).thenReturn(List.of());

        assertThat(service.getDashboard().topics()).hasSize(LiteracyTopic.values().length);
    }

    @Test
    void getLessonsRejectsAnUnknownTopic() {
        assertThatThrownBy(() -> service.getLessons("NOT_A_TOPIC", EN))
            .isInstanceOf(ApiException.class).hasMessageContaining("Unknown topic");
    }

    @Test
    void getQuizExposesOptionsButNotTheAnswer() {
        List<kh.edu.acleda.campuslink.modules.finance.dto.response.QuizQuestionResponse> quiz =
            service.getQuiz(LESSON_ID, EN);

        assertThat(quiz).hasSize(2);
        assertThat(quiz.get(0).options()).hasSize(4);
        // The response record simply has no field for the correct answer - structurally safe.
    }

    @Test
    void passingTheQuizGrantsXpAndCreditsTheWalletOnce() {
        QuizResultResponse r = service.submitQuiz(LESSON_ID, answers("B", "A"), EN);

        assertThat(r.score()).isEqualTo(100);
        assertThat(r.passed()).isTrue();
        assertThat(r.xpEarned()).isEqualTo(20);
        assertThat(r.learningBudgetCredited()).isEqualByComparingTo("1.00");
        assertThat(user.getTotalXp()).isEqualTo(20);
        verify(demoWalletService).creditReward(eq(new BigDecimal("1.00")), any());
        verify(attemptRepository).save(any(FinanceQuizAttempt.class));
    }

    @Test
    void failingBelowSixtyPercentGrantsNothing() {
        QuizResultResponse r = service.submitQuiz(LESSON_ID, answers("B", "B"), EN); // 1 of 2 correct

        assertThat(r.score()).isEqualTo(50);
        assertThat(r.passed()).isFalse();
        assertThat(r.xpEarned()).isZero();
        assertThat(r.learningBudgetCredited()).isEqualByComparingTo("0");
        assertThat(user.getTotalXp()).isZero();
        verify(demoWalletService, never()).creditReward(any(), any());
    }

    @Test
    void alreadyRewardedLessonDoesNotPayTwice() {
        LessonProgress rewarded = LessonProgress.builder().user(user).lesson(lesson).build();
        rewarded.setRewarded(true);
        rewarded.setQuizPassed(true);
        when(progressRepository.findByUserIdAndLessonId(USER_ID, LESSON_ID)).thenReturn(Optional.of(rewarded));

        QuizResultResponse r = service.submitQuiz(LESSON_ID, answers("B", "A"), EN);

        assertThat(r.passed()).isTrue();
        assertThat(r.xpEarned()).isZero();
        assertThat(r.learningBudgetCredited()).isEqualByComparingTo("0");
        assertThat(user.getTotalXp()).isZero();
        verify(demoWalletService, never()).creditReward(any(), any());
    }

    @Test
    void bestScoreIsKeptAcrossRetakes() {
        LessonProgress prior = LessonProgress.builder().user(user).lesson(lesson).build();
        prior.setBestScore(100);
        prior.setRewarded(true);
        prior.setQuizPassed(true);
        when(progressRepository.findByUserIdAndLessonId(USER_ID, LESSON_ID)).thenReturn(Optional.of(prior));

        service.submitQuiz(LESSON_ID, answers("B", "B"), EN); // scores 50, worse than 100

        assertThat(prior.getBestScore()).isEqualTo(100);
    }

    @Test
    void submittingWithNoQuizIsRejected() {
        when(questionRepository.findByLessonIdOrderByCreatedAtAsc(LESSON_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.submitQuiz(LESSON_ID, answers("B", "A"), EN))
            .isInstanceOf(ApiException.class).hasMessageContaining("no quiz");
    }

    @Test
    void completeLessonMarksItFinished() {
        org.mockito.ArgumentCaptor<LessonProgress> captor =
            org.mockito.ArgumentCaptor.forClass(LessonProgress.class);

        service.completeLesson(LESSON_ID, EN);

        verify(progressRepository).save(captor.capture());
        assertThat(captor.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    void unknownLessonReadsAsNotFound() {
        when(lessonRepository.findByIdAndDeletedAtIsNull(LESSON_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLesson(LESSON_ID, EN)).isInstanceOf(NotFoundException.class);
    }
}

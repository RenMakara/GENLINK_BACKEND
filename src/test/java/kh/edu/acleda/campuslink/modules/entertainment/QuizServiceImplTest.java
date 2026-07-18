package kh.edu.acleda.campuslink.modules.entertainment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.dto.request.QuizSubmitRequest;
import kh.edu.acleda.campuslink.modules.entertainment.dto.request.QuizSubmitRequest.Answer;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.QuizAttemptResponse;
import kh.edu.acleda.campuslink.modules.entertainment.entity.Quiz;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizAttempt;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizAttemptAnswer;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizOption;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizQuestion;
import kh.edu.acleda.campuslink.modules.entertainment.enums.QuizDifficulty;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizAttemptAnswerRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizAttemptRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizOptionRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizQuestionRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.RewardService;
import kh.edu.acleda.campuslink.modules.entertainment.service.impl.QuizServiceImpl;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuizServiceImplTest {

    private static final Language EN = Language.ENGLISH;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID QUIZ_ID = UUID.randomUUID();
    private static final UUID Q1 = UUID.randomUUID();
    private static final UUID Q2 = UUID.randomUUID();
    private static final UUID Q1_RIGHT = UUID.randomUUID();
    private static final UUID Q1_WRONG = UUID.randomUUID();
    private static final UUID Q2_RIGHT = UUID.randomUUID();
    private static final UUID Q2_WRONG = UUID.randomUUID();

    @Mock private QuizRepository quizRepository;
    @Mock private QuizQuestionRepository questionRepository;
    @Mock private QuizOptionRepository optionRepository;
    @Mock private QuizAttemptRepository attemptRepository;
    @Mock private QuizAttemptAnswerRepository answerRepository;
    @Mock private RewardService rewardService;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private QuizServiceImpl service;
    private User user;
    private Quiz quiz;

    @BeforeEach
    void setUp() {
        service = new QuizServiceImpl(quizRepository, questionRepository, optionRepository, attemptRepository,
            answerRepository, rewardService, userRepository, currentUserProvider);
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "totalXp", 0);
        quiz = Quiz.builder().titleEn("Money Basics").category("FINANCE")
            .difficulty(QuizDifficulty.EASY).xpReward(30).build();
        ReflectionTestUtils.setField(quiz, "id", QUIZ_ID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizRepository.findByIdAndDeletedAtIsNull(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(questionRepository.findByQuizIdOrderByCreatedAtAsc(QUIZ_ID))
            .thenReturn(List.of(question(Q1), question(Q2)));
        when(optionRepository.findByQuestionIdOrderByCreatedAtAsc(Q1))
            .thenReturn(List.of(option(Q1_RIGHT, true), option(Q1_WRONG, false)));
        when(optionRepository.findByQuestionIdOrderByCreatedAtAsc(Q2))
            .thenReturn(List.of(option(Q2_WRONG, false), option(Q2_RIGHT, true)));
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(i -> {
            QuizAttempt a = i.getArgument(0);
            ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
            return a;
        });
        when(answerRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(attemptRepository.existsByQuizIdAndUserIdAndXpEarnedGreaterThan(eq(QUIZ_ID), eq(USER_ID), eq(0)))
            .thenReturn(false);
    }

    private QuizQuestion question(UUID id) {
        QuizQuestion q = QuizQuestion.builder().quiz(quiz).questionTextEn("q").build();
        ReflectionTestUtils.setField(q, "id", id);
        return q;
    }

    private QuizOption option(UUID id, boolean correct) {
        QuizOption o = QuizOption.builder().optionTextEn("opt").correct(correct).build();
        ReflectionTestUtils.setField(o, "id", id);
        return o;
    }

    private QuizSubmitRequest answers(UUID a1, UUID a2) {
        return new QuizSubmitRequest(List.of(new Answer(Q1, a1), new Answer(Q2, a2)));
    }

    @Test
    void listMapsLocalizedTitleAndQuestionCount() {
        when(quizRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(quiz));
        when(questionRepository.countByQuizId(QUIZ_ID)).thenReturn(3L);

        assertThat(service.getAll(null, null, EN)).singleElement()
            .satisfies(r -> {
                assertThat(r.title()).isEqualTo("Money Basics");
                assertThat(r.questionCount()).isEqualTo(3);
            });
    }

    @Test
    void detailExposesOptionsWithoutTheCorrectFlag() {
        when(questionRepository.findByQuizIdOrderByCreatedAtAsc(QUIZ_ID)).thenReturn(List.of(question(Q1)));

        var detail = service.getQuiz(QUIZ_ID, EN);

        assertThat(detail.questions()).singleElement()
            .satisfies(q -> assertThat(q.options()).hasSize(2));
        // QuizDetailResponse.Option has no `correct` field - structurally safe.
    }

    @Test
    void allCorrectScores100AndAwardsFullScaledXp() {
        QuizAttemptResponse r = service.submit(QUIZ_ID, answers(Q1_RIGHT, Q2_RIGHT));

        assertThat(r.score()).isEqualTo(100);
        assertThat(r.correctCount()).isEqualTo(2);
        assertThat(r.xpEarned()).isEqualTo(30);
        verify(rewardService).award(any(), eq("QUIZ"), eq(QUIZ_ID), eq(30));
    }

    @Test
    void halfCorrectScalesXpByScore() {
        QuizAttemptResponse r = service.submit(QUIZ_ID, answers(Q1_RIGHT, Q2_WRONG));

        assertThat(r.score()).isEqualTo(50);
        assertThat(r.xpEarned()).isEqualTo(15); // round(30 * 1/2)
    }

    @Test
    void replayAfterEarningPaysNoMoreXp() {
        when(attemptRepository.existsByQuizIdAndUserIdAndXpEarnedGreaterThan(QUIZ_ID, USER_ID, 0)).thenReturn(true);

        QuizAttemptResponse r = service.submit(QUIZ_ID, answers(Q1_RIGHT, Q2_RIGHT));

        assertThat(r.score()).isEqualTo(100);
        assertThat(r.xpEarned()).isZero();
        verify(rewardService, never()).award(any(), any(), any(), anyInt());
    }

    @Test
    void answerRowsAreRecordedForAnsweredQuestions() {
        service.submit(QUIZ_ID, answers(Q1_RIGHT, Q2_RIGHT));

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(answerRepository).saveAll(captor.capture());
        assertThat((List<QuizAttemptAnswer>) captor.getValue()).hasSize(2);
    }

    @Test
    void submittingAQuizWithNoQuestionsIsRejected() {
        when(questionRepository.findByQuizIdOrderByCreatedAtAsc(QUIZ_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.submit(QUIZ_ID, answers(Q1_RIGHT, Q2_RIGHT)))
            .isInstanceOf(ApiException.class).hasMessageContaining("no questions");
    }

    @Test
    void unknownQuizReadsAsNotFound() {
        when(quizRepository.findByIdAndDeletedAtIsNull(QUIZ_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getQuiz(QUIZ_ID, EN)).isInstanceOf(NotFoundException.class);
    }
}

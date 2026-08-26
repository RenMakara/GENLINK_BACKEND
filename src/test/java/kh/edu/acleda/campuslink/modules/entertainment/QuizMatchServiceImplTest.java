package kh.edu.acleda.campuslink.modules.entertainment;

import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.entity.Quiz;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizMatch;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizMatchAnswer;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizOption;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizQuestion;
import kh.edu.acleda.campuslink.modules.entertainment.enums.QuizMatchStatus;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizMatchAnswerRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizMatchRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizOptionRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizQuestionRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.RewardService;
import kh.edu.acleda.campuslink.modules.entertainment.service.impl.QuizMatchServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuizMatchServiceImplTest {

    private static final UUID QUIZ_ID = UUID.randomUUID();
    private static final UUID MATCH_ID = UUID.randomUUID();
    private static final UUID P1_ID = UUID.randomUUID();
    private static final UUID P2_ID = UUID.randomUUID();
    private static final UUID OUTSIDER_ID = UUID.randomUUID();
    private static final UUID QUESTION_ID = UUID.randomUUID();
    private static final UUID OPTION_ID = UUID.randomUUID();

    @Mock private QuizMatchRepository matchRepository;
    @Mock private QuizMatchAnswerRepository answerRepository;
    @Mock private QuizRepository quizRepository;
    @Mock private QuizOptionRepository optionRepository;
    @Mock private QuizQuestionRepository questionRepository;
    @Mock private UserRepository userRepository;
    @Mock private RewardService rewardService;

    private QuizMatchServiceImpl service;
    private Quiz quiz;
    private User p1;
    private User p2;
    private QuizMatch match;

    @BeforeEach
    void setUp() {
        service = new QuizMatchServiceImpl(matchRepository, answerRepository, quizRepository,
            optionRepository, questionRepository, userRepository, rewardService);
        quiz = Quiz.builder().titleEn("Quiz").xpReward(10).build();
        ReflectionTestUtils.setField(quiz, "id", QUIZ_ID);
        p1 = user(P1_ID, "Player One");
        p2 = user(P2_ID, "Player Two");
        match = match(QuizMatchStatus.IN_PROGRESS, p1, p2);

        when(matchRepository.findByIdForUpdate(MATCH_ID)).thenReturn(Optional.of(match));
        when(matchRepository.save(any(QuizMatch.class))).thenAnswer(i -> {
            QuizMatch saved = i.getArgument(0);
            if (saved.getId() == null) ReflectionTestUtils.setField(saved, "id", MATCH_ID);
            return saved;
        });
        when(quizRepository.findById(QUIZ_ID)).thenReturn(Optional.of(quiz));
        when(userRepository.findById(P1_ID)).thenReturn(Optional.of(p1));
        when(userRepository.findById(P2_ID)).thenReturn(Optional.of(p2));
    }

    @Test
    void outsiderCannotSubmitOrFinish() {
        assertThatThrownBy(() -> service.submitAnswer(MATCH_ID, OUTSIDER_ID, QUESTION_ID, OPTION_ID))
            .isInstanceOf(ApiException.class).hasMessageContaining("not a participant");
        assertThatThrownBy(() -> service.finishMatch(MATCH_ID, OUTSIDER_ID))
            .isInstanceOf(ApiException.class).hasMessageContaining("not a participant");
        verify(answerRepository, never()).save(any());
        verify(rewardService, never()).award(any(), any(), any(), any(Integer.class));
    }

    @Test
    void questionMustBelongToMatchQuiz() {
        when(questionRepository.findByIdAndQuizId(QUESTION_ID, QUIZ_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitAnswer(MATCH_ID, P1_ID, QUESTION_ID, OPTION_ID))
            .isInstanceOf(ApiException.class).hasMessageContaining("Question does not belong");
    }

    @Test
    void optionMustBelongToSubmittedQuestion() {
        QuizQuestion question = question();
        when(questionRepository.findByIdAndQuizId(QUESTION_ID, QUIZ_ID)).thenReturn(Optional.of(question));
        when(optionRepository.findByIdAndQuestionId(OPTION_ID, QUESTION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitAnswer(MATCH_ID, P1_ID, QUESTION_ID, OPTION_ID))
            .isInstanceOf(ApiException.class).hasMessageContaining("Option does not belong");
    }

    @Test
    void validCorrectAnswerIsRecordedAndScoredOnce() {
        QuizQuestion question = question();
        QuizOption option = QuizOption.builder().question(question).correct(true).optionTextEn("A").build();
        ReflectionTestUtils.setField(option, "id", OPTION_ID);
        when(questionRepository.findByIdAndQuizId(QUESTION_ID, QUIZ_ID)).thenReturn(Optional.of(question));
        when(optionRepository.findByIdAndQuestionId(OPTION_ID, QUESTION_ID)).thenReturn(Optional.of(option));

        assertThat(service.submitAnswer(MATCH_ID, P1_ID, QUESTION_ID, OPTION_ID).player1Score()).isEqualTo(10);
        ArgumentCaptor<QuizMatchAnswer> answer = ArgumentCaptor.forClass(QuizMatchAnswer.class);
        verify(answerRepository).save(answer.capture());
        assertThat(answer.getValue().getQuestion()).isSameAs(question);
        assertThat(answer.getValue().getPlayer()).isSameAs(p1);
    }

    @Test
    void waitingMatchCannotBeFinished() {
        match.setStatus(QuizMatchStatus.WAITING);
        match.setPlayer2(null);

        assertThatThrownBy(() -> service.finishMatch(MATCH_ID, P1_ID))
            .isInstanceOf(ApiException.class).hasMessageContaining("in-progress");
        verify(rewardService, never()).award(any(), any(), any(), any(Integer.class));
    }

    @Test
    void completedMatchIsIdempotentAndUsesLockedLookup() {
        match.setPlayer1Score(20);
        service.finishMatch(MATCH_ID, P1_ID);
        assertThat(match.getStatus()).isEqualTo(QuizMatchStatus.COMPLETED);
        service.finishMatch(MATCH_ID, P2_ID);

        verify(matchRepository, times(2)).findByIdForUpdate(MATCH_ID);
        verify(rewardService, times(1)).award(p1, "QUIZ_MATCH", MATCH_ID, 50);
    }

    @Test
    void secondPlayerUpdatesThePersistedWaitingMatchInsteadOfCreatingAnother() {
        QuizMatch waiting = match(QuizMatchStatus.WAITING, p1, null);
        when(matchRepository.save(any(QuizMatch.class))).thenAnswer(i -> {
            QuizMatch saved = i.getArgument(0);
            if (saved.getId() == null) ReflectionTestUtils.setField(saved, "id", MATCH_ID);
            return saved;
        });

        assertThat(service.matchmake(QUIZ_ID, P1_ID).status()).isEqualTo(QuizMatchStatus.WAITING);
        when(matchRepository.findByIdForUpdate(MATCH_ID)).thenReturn(Optional.of(waiting));
        assertThat(service.matchmake(QUIZ_ID, P2_ID).status()).isEqualTo(QuizMatchStatus.IN_PROGRESS);

        assertThat(waiting.getPlayer2()).isSameAs(p2);
        assertThat(waiting.getStatus()).isEqualTo(QuizMatchStatus.IN_PROGRESS);
    }

    private QuizQuestion question() {
        QuizQuestion question = QuizQuestion.builder().quiz(quiz).questionTextEn("Q").build();
        ReflectionTestUtils.setField(question, "id", QUESTION_ID);
        return question;
    }

    private QuizMatch match(QuizMatchStatus status, User player1, User player2) {
        QuizMatch value = new QuizMatch();
        ReflectionTestUtils.setField(value, "id", MATCH_ID);
        value.setQuiz(quiz);
        value.setPlayer1(player1);
        value.setPlayer2(player2);
        value.setStatus(status);
        return value;
    }

    private User user(UUID id, String name) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setFullName(name);
        return user;
    }
}

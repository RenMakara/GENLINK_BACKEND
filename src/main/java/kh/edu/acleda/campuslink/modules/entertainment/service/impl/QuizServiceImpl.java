package kh.edu.acleda.campuslink.modules.entertainment.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.dto.request.QuizSubmitRequest;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.QuizAttemptResponse;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.QuizAttemptResponse.AnswerResult;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.QuizAttemptSummaryResponse;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.QuizDetailResponse;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.QuizResponse;
import kh.edu.acleda.campuslink.modules.entertainment.entity.Quiz;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizAttempt;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizAttemptAnswer;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizOption;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizQuestion;
import kh.edu.acleda.campuslink.modules.entertainment.entity.XpHistory;
import kh.edu.acleda.campuslink.modules.entertainment.enums.QuizDifficulty;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizAttemptAnswerRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizAttemptRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizOptionRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizQuestionRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.XpHistoryRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.QuizService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.localization.LocalizedTextSelector;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizServiceImpl implements QuizService {

    private static final Logger log = LoggerFactory.getLogger(QuizServiceImpl.class);
    private static final String XP_SOURCE = "QUIZ";

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizOptionRepository optionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizAttemptAnswerRepository answerRepository;
    private final XpHistoryRepository xpHistoryRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public QuizServiceImpl(QuizRepository quizRepository,
                           QuizQuestionRepository questionRepository,
                           QuizOptionRepository optionRepository,
                           QuizAttemptRepository attemptRepository,
                           QuizAttemptAnswerRepository answerRepository,
                           XpHistoryRepository xpHistoryRepository,
                           UserRepository userRepository,
                           CurrentUserProvider currentUserProvider) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.xpHistoryRepository = xpHistoryRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuizResponse> getAll(String category, QuizDifficulty difficulty, Language language) {
        return quizRepository.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
            .filter(q -> category == null || category.equalsIgnoreCase(q.getCategory()))
            .filter(q -> difficulty == null || difficulty == q.getDifficulty())
            .map(q -> new QuizResponse(q.getId(),
                LocalizedTextSelector.select(q.getTitleEn(), q.getTitleKm(), language),
                q.getCategory(), q.getDifficulty(), q.getXpReward(),
                questionRepository.countByQuizId(q.getId())))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public QuizDetailResponse getQuiz(UUID id, Language language) {
        Quiz quiz = loadQuiz(id);
        List<QuizDetailResponse.Question> questions = questionRepository.findByQuizIdOrderByCreatedAtAsc(id).stream()
            .map(q -> new QuizDetailResponse.Question(q.getId(),
                LocalizedTextSelector.select(q.getQuestionTextEn(), q.getQuestionTextKm(), language),
                optionRepository.findByQuestionIdOrderByCreatedAtAsc(q.getId()).stream()
                    .map(o -> new QuizDetailResponse.Option(o.getId(),
                        LocalizedTextSelector.select(o.getOptionTextEn(), o.getOptionTextKm(), language)))
                    .toList()))
            .toList();
        return new QuizDetailResponse(quiz.getId(),
            LocalizedTextSelector.select(quiz.getTitleEn(), quiz.getTitleKm(), language),
            quiz.getCategory(), quiz.getDifficulty(), quiz.getXpReward(), questions);
    }

    @Override
    @Transactional
    public QuizAttemptResponse submit(UUID quizId, QuizSubmitRequest request) {
        Quiz quiz = loadQuiz(quizId);
        List<QuizQuestion> questions = questionRepository.findByQuizIdOrderByCreatedAtAsc(quizId);
        if (questions.isEmpty()) {
            throw new ApiException("This quiz has no questions", "NO_QUESTIONS");
        }
        User user = loadCurrentUser();

        Map<UUID, UUID> chosen = new HashMap<>();
        for (QuizSubmitRequest.Answer a : request.answers()) {
            chosen.put(a.questionId(), a.selectedOptionId());
        }

        int correctCount = 0;
        List<AnswerResult> results = new ArrayList<>();
        List<QuizAttemptAnswer> answerRows = new ArrayList<>();
        for (QuizQuestion q : questions) {
            List<QuizOption> options = optionRepository.findByQuestionIdOrderByCreatedAtAsc(q.getId());
            UUID correctOptionId = options.stream()
                .filter(o -> Boolean.TRUE.equals(o.getCorrect())).map(QuizOption::getId).findFirst().orElse(null);
            UUID selectedId = chosen.get(q.getId());
            QuizOption selected = options.stream()
                .filter(o -> o.getId().equals(selectedId)).findFirst().orElse(null);
            boolean isCorrect = selected != null && selected.getId().equals(correctOptionId);
            if (isCorrect) {
                correctCount++;
            }
            results.add(new AnswerResult(q.getId(), selected == null ? null : selected.getId(),
                correctOptionId, isCorrect));
            if (selected != null) {
                answerRows.add(QuizAttemptAnswer.builder()
                    .question(q).selectedOption(selected).correct(isCorrect)
                    .answeredAt(LocalDateTime.now()).build());
            }
        }

        int total = questions.size();
        int score = correctCount * 100 / total;
        boolean alreadyEarned =
            attemptRepository.existsByQuizIdAndUserIdAndXpEarnedGreaterThan(quizId, user.getId(), 0);
        int xpEarned = alreadyEarned ? 0 : (int) Math.round((double) quiz.getXpReward() * correctCount / total);

        QuizAttempt attempt = attemptRepository.save(QuizAttempt.builder()
            .quiz(quiz).user(user).score(score).correctCount(correctCount).totalQuestions(total)
            .xpEarned(xpEarned).completedAt(LocalDateTime.now()).build());
        answerRows.forEach(row -> row.setAttempt(attempt));
        answerRepository.saveAll(answerRows);

        if (xpEarned > 0) {
            awardXp(user, quizId, xpEarned);
        }
        return new QuizAttemptResponse(attempt.getId(), score, correctCount, total, xpEarned, results);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuizAttemptSummaryResponse> history(Language language) {
        return attemptRepository.findByUserIdOrderByCompletedAtDesc(currentUserProvider.getCurrentUserId()).stream()
            .map(a -> new QuizAttemptSummaryResponse(a.getId(),
                a.getQuiz() == null ? null : a.getQuiz().getId(),
                a.getQuiz() == null ? null
                    : LocalizedTextSelector.select(a.getQuiz().getTitleEn(), a.getQuiz().getTitleKm(), language),
                a.getScore(), a.getCorrectCount(), a.getTotalQuestions(), a.getXpEarned(), a.getCompletedAt()))
            .toList();
    }

    private void awardXp(User user, UUID quizId, int xp) {
        user.setTotalXp(user.getTotalXp() + xp);
        userRepository.save(user);
        xpHistoryRepository.save(XpHistory.builder()
            .user(user).sourceType(XP_SOURCE).sourceId(quizId).xpAmount(xp).build());
        log.info("Quiz XP: user={} quiz={} xp={}", user.getId(), quizId, xp);
    }

    private Quiz loadQuiz(UUID id) {
        return quizRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("Quiz not found"));
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }
}

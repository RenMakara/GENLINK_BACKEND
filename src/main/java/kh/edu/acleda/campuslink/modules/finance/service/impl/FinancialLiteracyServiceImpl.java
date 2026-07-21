package kh.edu.acleda.campuslink.modules.finance.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.service.RewardService;
import kh.edu.acleda.campuslink.modules.finance.dto.request.QuizSubmitRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.LessonDetailResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.LessonSummaryResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.LiteracyDashboardResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.LiteracyDashboardResponse.TopicProgress;
import kh.edu.acleda.campuslink.modules.finance.dto.response.QuizQuestionResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.QuizResultResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.QuizResultResponse.QuestionResult;
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
import kh.edu.acleda.campuslink.modules.finance.service.FinancialLiteracyService;
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
public class FinancialLiteracyServiceImpl implements FinancialLiteracyService {

    private static final Logger log = LoggerFactory.getLogger(FinancialLiteracyServiceImpl.class);

    private static final int PASS_THRESHOLD = 60;
    private static final String XP_SOURCE = "FINANCE_LESSON";
    /** Simulated learning-budget bonus credited to the demo wallet on first pass. */
    private static final BigDecimal LEARNING_REWARD = new BigDecimal("1.00");

    private final FinancialLessonRepository lessonRepository;
    private final FinancialQuizQuestionRepository questionRepository;
    private final LessonProgressRepository progressRepository;
    private final FinanceQuizAttemptRepository attemptRepository;
    private final RewardService rewardService;
    private final UserRepository userRepository;
    private final DemoWalletService demoWalletService;
    private final CurrentUserProvider currentUserProvider;

    public FinancialLiteracyServiceImpl(FinancialLessonRepository lessonRepository,
                                        FinancialQuizQuestionRepository questionRepository,
                                        LessonProgressRepository progressRepository,
                                        FinanceQuizAttemptRepository attemptRepository,
                                        RewardService rewardService,
                                        UserRepository userRepository,
                                        DemoWalletService demoWalletService,
                                        CurrentUserProvider currentUserProvider) {
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.progressRepository = progressRepository;
        this.attemptRepository = attemptRepository;
        this.rewardService = rewardService;
        this.userRepository = userRepository;
        this.demoWalletService = demoWalletService;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public LiteracyDashboardResponse getDashboard() {
        UUID userId = currentUserProvider.getCurrentUserId();
        List<FinancialLesson> lessons = lessonRepository.findByDeletedAtIsNullOrderByTopicAscCreatedAtAsc();
        Map<UUID, LessonProgress> progress = progressByLesson(userId);

        int completed = 0;
        int literacyXp = 0;
        // Every topic appears, in the canonical order, even with zero lessons.
        Map<String, int[]> perTopic = new LinkedHashMap<>();
        for (LiteracyTopic topic : LiteracyTopic.values()) {
            perTopic.put(topic.name(), new int[]{0, 0});
        }
        for (FinancialLesson lesson : lessons) {
            LessonProgress p = progress.get(lesson.getId());
            boolean isDone = p != null && p.getCompletedAt() != null;
            if (isDone) {
                completed++;
            }
            if (p != null && p.isRewarded()) {
                literacyXp += lesson.getXpReward();
            }
            int[] counts = perTopic.get(lesson.getTopic());
            if (counts != null) { // ignore any lesson tagged with an unknown topic
                counts[0]++;
                if (isDone) {
                    counts[1]++;
                }
            }
        }

        List<TopicProgress> topics = new ArrayList<>();
        perTopic.forEach((topic, counts) -> topics.add(new TopicProgress(topic, counts[0], counts[1])));
        return new LiteracyDashboardResponse(lessons.size(), completed, literacyXp, topics);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LessonSummaryResponse> getLessons(String topic, Language language) {
        UUID userId = currentUserProvider.getCurrentUserId();
        List<FinancialLesson> lessons = topic == null
            ? lessonRepository.findByDeletedAtIsNullOrderByTopicAscCreatedAtAsc()
            : lessonRepository.findByTopicAndDeletedAtIsNullOrderByCreatedAtAsc(validTopic(topic));
        Map<UUID, LessonProgress> progress = progressByLesson(userId);
        return lessons.stream().map(l -> toSummary(l, progress.get(l.getId()), language)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LessonDetailResponse getLesson(UUID lessonId, Language language) {
        FinancialLesson lesson = loadLesson(lessonId);
        LessonProgress p = progressRepository
            .findByUserIdAndLessonId(currentUserProvider.getCurrentUserId(), lessonId).orElse(null);
        return toDetail(lesson, p, language);
    }

    @Override
    @Transactional
    public LessonDetailResponse completeLesson(UUID lessonId, Language language) {
        FinancialLesson lesson = loadLesson(lessonId);
        LessonProgress p = loadOrNewProgress(lessonId);
        if (p.getCompletedAt() == null) {
            p.setCompletedAt(Instant.now());
        }
        progressRepository.save(p);
        return toDetail(lesson, p, language);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuizQuestionResponse> getQuiz(UUID lessonId, Language language) {
        loadLesson(lessonId); // 404 if the lesson does not exist
        return questionRepository.findByLessonIdOrderByCreatedAtAsc(lessonId).stream()
            .map(q -> toQuestion(q, language))
            .toList();
    }

    @Override
    @Transactional
    public QuizResultResponse submitQuiz(UUID lessonId, QuizSubmitRequest request, Language language) {
        FinancialLesson lesson = loadLesson(lessonId);
        List<FinancialQuizQuestion> questions = questionRepository.findByLessonIdOrderByCreatedAtAsc(lessonId);
        if (questions.isEmpty()) {
            throw new ApiException("This lesson has no quiz", "NO_QUIZ");
        }

        Map<UUID, String> answers = new java.util.HashMap<>();
        for (QuizSubmitRequest.Answer a : request.answers()) {
            answers.put(a.questionId(), a.selectedOption());
        }

        int correct = 0;
        List<QuestionResult> results = new ArrayList<>();
        for (FinancialQuizQuestion q : questions) {
            String chosen = answers.get(q.getId());
            boolean isCorrect = q.getCorrectAnswer() != null && q.getCorrectAnswer().equalsIgnoreCase(chosen);
            if (isCorrect) {
                correct++;
            }
            results.add(new QuestionResult(q.getId(), chosen, q.getCorrectAnswer(), isCorrect,
                LocalizedTextSelector.select(q.getExplanationEn(), q.getExplanationKm(), language)));
        }

        int score = correct * 100 / questions.size();
        boolean passed = score >= PASS_THRESHOLD;

        attemptRepository.save(FinanceQuizAttempt.builder()
            .user(loadCurrentUser()).lesson(lesson).score(score).passed(passed).build());

        LessonProgress p = loadOrNewProgress(lessonId);
        if (p.getCompletedAt() == null) {
            p.setCompletedAt(Instant.now());
        }
        if (p.getBestScore() == null || score > p.getBestScore()) {
            p.setBestScore(score);
        }
        p.setQuizPassed(p.isQuizPassed() || passed);

        int xpEarned = 0;
        BigDecimal credited = BigDecimal.ZERO;
        if (passed && !p.isRewarded()) {
            xpEarned = awardXp(lesson);
            credited = LEARNING_REWARD;
            demoWalletService.creditReward(LEARNING_REWARD, "Learning reward: " + lesson.getTitleEn());
            p.setRewarded(true);
        }
        progressRepository.save(p);

        return new QuizResultResponse(score, correct, questions.size(), passed, xpEarned, credited, results);
    }

    private int awardXp(FinancialLesson lesson) {
        User user = loadCurrentUser();
        int xp = lesson.getXpReward();
        rewardService.award(user, XP_SOURCE, lesson.getId(), xp);
        return xp;
    }

    private LessonSummaryResponse toSummary(FinancialLesson lesson, LessonProgress p, Language language) {
        return new LessonSummaryResponse(lesson.getId(),
            LocalizedTextSelector.select(lesson.getTitleEn(), lesson.getTitleKm(), language),
            lesson.getTopic(), lesson.getContentType(), lesson.getEstimatedTime(), lesson.getXpReward(),
            p != null && p.getCompletedAt() != null, p != null && p.isQuizPassed());
    }

    private LessonDetailResponse toDetail(FinancialLesson lesson, LessonProgress p, Language language) {
        int questionCount = questionRepository.findByLessonIdOrderByCreatedAtAsc(lesson.getId()).size();
        return new LessonDetailResponse(lesson.getId(),
            LocalizedTextSelector.select(lesson.getTitleEn(), lesson.getTitleKm(), language),
            LocalizedTextSelector.select(lesson.getContentEn(), lesson.getContentKm(), language),
            lesson.getTopic(), lesson.getContentType(), lesson.getMediaUrl(), lesson.getEstimatedTime(),
            lesson.getXpReward(), p != null && p.getCompletedAt() != null, p != null && p.isQuizPassed(),
            questionCount);
    }

    private QuizQuestionResponse toQuestion(FinancialQuizQuestion q, Language language) {
        List<QuizQuestionResponse.Option> options = new ArrayList<>();
        addOption(options, "A", q.getOptionAEn(), q.getOptionAKm(), language);
        addOption(options, "B", q.getOptionBEn(), q.getOptionBKm(), language);
        addOption(options, "C", q.getOptionCEn(), q.getOptionCKm(), language);
        addOption(options, "D", q.getOptionDEn(), q.getOptionDKm(), language);
        return new QuizQuestionResponse(q.getId(),
            LocalizedTextSelector.select(q.getQuestionTextEn(), q.getQuestionTextKm(), language), options);
    }

    private void addOption(List<QuizQuestionResponse.Option> options, String key,
                           String en, String km, Language language) {
        if (en != null) {
            options.add(new QuizQuestionResponse.Option(key, LocalizedTextSelector.select(en, km, language)));
        }
    }

    private Map<UUID, LessonProgress> progressByLesson(UUID userId) {
        Map<UUID, LessonProgress> map = new java.util.HashMap<>();
        for (LessonProgress p : progressRepository.findByUserId(userId)) {
            if (p.getLesson() != null) {
                map.put(p.getLesson().getId(), p);
            }
        }
        return map;
    }

    private LessonProgress loadOrNewProgress(UUID lessonId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        return progressRepository.findByUserIdAndLessonId(userId, lessonId)
            .orElseGet(() -> LessonProgress.builder()
                .user(loadCurrentUser())
                .lesson(loadLesson(lessonId))
                .build());
    }

    private String validTopic(String topic) {
        try {
            return LiteracyTopic.valueOf(topic).name();
        } catch (IllegalArgumentException e) {
            throw new ApiException("Unknown topic: " + topic, "VALIDATION_ERROR");
        }
    }

    private FinancialLesson loadLesson(UUID lessonId) {
        return lessonRepository.findByIdAndDeletedAtIsNull(lessonId)
            .orElseThrow(() -> new NotFoundException("Lesson not found"));
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }
}

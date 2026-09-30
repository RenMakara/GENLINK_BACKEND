package kh.edu.acleda.campuslink.modules.entertainment.service.impl;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kh.edu.acleda.campuslink.modules.entertainment.dto.websocket.MatchEvent;
import kh.edu.acleda.campuslink.modules.entertainment.entity.Quiz;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizMatch;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizMatchAnswer;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizOption;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizQuestion;
import kh.edu.acleda.campuslink.modules.entertainment.enums.QuizMatchStatus;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizMatchRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizMatchAnswerRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizOptionRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizQuestionRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizRepository;
import kh.edu.acleda.campuslink.modules.entertainment.service.QuizMatchService;
import kh.edu.acleda.campuslink.modules.entertainment.service.RewardService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuizMatchServiceImpl implements QuizMatchService {

    private final QuizMatchRepository quizMatchRepository;
    private final QuizMatchAnswerRepository quizMatchAnswerRepository;
    private final QuizRepository quizRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final UserRepository userRepository;
    private final RewardService rewardService;

    // Single-instance matchmaking queue: quizId -> the persisted WAITING match.
    private final ConcurrentHashMap<UUID, UUID> waitingMatchIds = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public MatchEvent matchmake(UUID quizId, UUID userId) {
        Quiz quiz = quizRepository.findById(quizId)
            .orElseThrow(() -> new ApiException("Quiz not found", "QUIZ_NOT_FOUND"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiException("User not found", "USER_NOT_FOUND"));

        synchronized (waitingMatchIds) {
            UUID waitingMatchId = waitingMatchIds.get(quizId);
            if (waitingMatchId != null) {
                QuizMatch waiting = quizMatchRepository.findByIdForUpdate(waitingMatchId).orElse(null);
                if (waiting != null && waiting.getStatus() == QuizMatchStatus.WAITING) {
                    if (waiting.getPlayer1().getId().equals(userId)) {
                        return buildEvent(waiting);
                    }
                    waiting.setPlayer2(user);
                    waiting.setStatus(QuizMatchStatus.IN_PROGRESS);
                    waitingMatchIds.remove(quizId, waitingMatchId);
                    return buildEvent(quizMatchRepository.save(waiting));
                }
                waitingMatchIds.remove(quizId, waitingMatchId);
            }

            QuizMatch waiting = new QuizMatch();
            waiting.setQuiz(quiz);
            waiting.setPlayer1(user);
            waiting.setStatus(QuizMatchStatus.WAITING);
            waiting = quizMatchRepository.save(waiting);
            waitingMatchIds.put(quizId, waiting.getId());
            return buildEvent(waiting);
        }
    }

    @Override
    @Transactional
    public MatchEvent submitAnswer(UUID matchId, UUID userId, UUID questionId, UUID optionId) {
        QuizMatch match = loadForUpdate(matchId);
        requireParticipant(match, userId);

        if (match.getStatus() != QuizMatchStatus.IN_PROGRESS) {
            throw new ApiException("Match is not in progress", "MATCH_NOT_IN_PROGRESS");
        }

        // Prevent duplicate answers
        if (quizMatchAnswerRepository.existsByMatchIdAndPlayerIdAndQuestionId(matchId, userId, questionId)) {
            // Already answered, just ignore
            return buildEvent(match);
        }

        QuizQuestion question = quizQuestionRepository.findByIdAndQuizId(questionId, match.getQuiz().getId())
            .orElseThrow(() -> new ApiException("Question does not belong to this quiz", "INVALID_MATCH_QUESTION"));
        QuizOption option = optionId == null ? null : quizOptionRepository.findByIdAndQuestionId(optionId, questionId)
            .orElseThrow(() -> new ApiException("Option does not belong to this question", "INVALID_MATCH_OPTION"));
        User player = match.getPlayer1().getId().equals(userId) ? match.getPlayer1() : match.getPlayer2();
        boolean isCorrect = option != null && Boolean.TRUE.equals(option.getCorrect());

        // Record the answer
        QuizMatchAnswer answer = new QuizMatchAnswer();
        answer.setMatch(match);
        answer.setPlayer(player);
        answer.setQuestion(question);
        answer.setOption(option);
        answer.setCorrect(isCorrect);
        quizMatchAnswerRepository.save(answer);
        
        if (isCorrect) {
            if (userId.equals(match.getPlayer1().getId())) {
                match.setPlayer1Score(match.getPlayer1Score() + 10);
            } else if (match.getPlayer2() != null && userId.equals(match.getPlayer2().getId())) {
                match.setPlayer2Score(match.getPlayer2Score() + 10);
            }
            match = quizMatchRepository.save(match);
        }

        // Logic for concluding the match can be invoked separately via finishMatch()
        
        return buildEvent(match);
    }

    @Override
    @Transactional
    public MatchEvent finishMatch(UUID matchId, UUID userId) {
        QuizMatch match = loadForUpdate(matchId);
        requireParticipant(match, userId);

        if (match.getStatus() == QuizMatchStatus.COMPLETED) {
            return buildEvent(match);
        }
        if (match.getStatus() != QuizMatchStatus.IN_PROGRESS) {
            throw new ApiException("Only an in-progress match can be finished", "MATCH_NOT_IN_PROGRESS");
        }

        long questions = quizQuestionRepository.countByQuizId(match.getQuiz().getId());
        long player1Answers = quizMatchAnswerRepository.countByMatchIdAndPlayerId(matchId, match.getPlayer1().getId());
        long player2Answers = match.getPlayer2() == null ? 0
            : quizMatchAnswerRepository.countByMatchIdAndPlayerId(matchId, match.getPlayer2().getId());
        if (questions == 0 || player1Answers < questions || player2Answers < questions) {
            throw new ApiException("Both players must answer or skip every question before the match can finish",
                "MATCH_QUESTIONS_INCOMPLETE");
        }

        match.setStatus(QuizMatchStatus.COMPLETED);

        if (match.getPlayer1Score() > match.getPlayer2Score()) {
            match.setWinner(match.getPlayer1());
            rewardService.award(match.getPlayer1(), "QUIZ_MATCH", matchId, 50);
        } else if (match.getPlayer2Score() > match.getPlayer1Score()) {
            match.setWinner(match.getPlayer2());
            rewardService.award(match.getPlayer2(), "QUIZ_MATCH", matchId, 50);
        } else {
            // Tie - give both players a smaller tie reward
            rewardService.award(match.getPlayer1(), "QUIZ_MATCH", matchId, 20);
            if (match.getPlayer2() != null) {
                rewardService.award(match.getPlayer2(), "QUIZ_MATCH", matchId, 20);
            }
        }

        return buildEvent(quizMatchRepository.save(match));
    }

    private QuizMatch loadForUpdate(UUID matchId) {
        return quizMatchRepository.findByIdForUpdate(matchId)
            .orElseThrow(() -> new ApiException("Match not found", "MATCH_NOT_FOUND"));
    }

    private void requireParticipant(QuizMatch match, UUID userId) {
        boolean player1 = match.getPlayer1().getId().equals(userId);
        boolean player2 = match.getPlayer2() != null && match.getPlayer2().getId().equals(userId);
        if (!player1 && !player2) {
            throw new ApiException("User is not a participant in this match", "MATCH_ACCESS_DENIED");
        }
    }

    private MatchEvent buildEvent(QuizMatch match) {
        return new MatchEvent(
            match.getId(),
            match.getQuiz().getId(),
            match.getStatus(),
            match.getPlayer1().getId(),
            match.getPlayer1().getFullName(),
            match.getPlayer1Score(),
            match.getPlayer2() != null ? match.getPlayer2().getId() : null,
            match.getPlayer2() != null ? match.getPlayer2().getFullName() : null,
            match.getPlayer2Score(),
            match.getWinner() != null ? match.getWinner().getId() : null
        );
    }
}

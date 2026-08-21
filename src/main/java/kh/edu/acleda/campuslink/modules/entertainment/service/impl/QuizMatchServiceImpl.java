package kh.edu.acleda.campuslink.modules.entertainment.service.impl;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kh.edu.acleda.campuslink.modules.entertainment.dto.websocket.MatchEvent;
import kh.edu.acleda.campuslink.modules.entertainment.entity.Quiz;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizMatch;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizMatchAnswer;
import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizOption;
import kh.edu.acleda.campuslink.modules.entertainment.enums.QuizMatchStatus;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizMatchRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizMatchAnswerRepository;
import kh.edu.acleda.campuslink.modules.entertainment.repository.QuizOptionRepository;
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
    private final UserRepository userRepository;
    private final RewardService rewardService;

    // In-memory matchmaking queue: quizId -> waitingUserId
    private final ConcurrentHashMap<UUID, UUID> waitingQueue = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public MatchEvent matchmake(UUID quizId, UUID userId) {
        // Check if someone is already waiting for this quiz (and not the same user)
        UUID waitingUserId = waitingQueue.remove(quizId);
        
        if (waitingUserId != null && !waitingUserId.equals(userId)) {
            // Found a match
            Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ApiException("Quiz not found"));
            User p1 = userRepository.findById(waitingUserId).orElseThrow();
            User p2 = userRepository.findById(userId).orElseThrow();

            QuizMatch match = new QuizMatch();
            match.setQuiz(quiz);
            match.setPlayer1(p1);
            match.setPlayer2(p2);
            match.setStatus(QuizMatchStatus.IN_PROGRESS);
            match = quizMatchRepository.save(match);

            return buildEvent(match);
        } else {
            // Nobody waiting, put self in queue
            waitingQueue.put(quizId, userId);
            
            Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ApiException("Quiz not found"));
            User p1 = userRepository.findById(userId).orElseThrow();
            
            QuizMatch match = new QuizMatch();
            match.setQuiz(quiz);
            match.setPlayer1(p1);
            match.setStatus(QuizMatchStatus.WAITING);
            match = quizMatchRepository.save(match);

            return buildEvent(match);
        }
    }

    @Override
    @Transactional
    public MatchEvent submitAnswer(UUID matchId, UUID userId, UUID questionId, UUID optionId) {
        QuizMatch match = quizMatchRepository.findById(matchId)
            .orElseThrow(() -> new ApiException("Match not found"));

        if (match.getStatus() != QuizMatchStatus.IN_PROGRESS) {
            return buildEvent(match);
        }

        // Prevent duplicate answers
        if (quizMatchAnswerRepository.existsByMatchIdAndPlayerIdAndQuestionId(matchId, userId, questionId)) {
            // Already answered, just ignore
            return buildEvent(match);
        }

        QuizOption option = quizOptionRepository.findById(optionId).orElseThrow();
        User player = userRepository.findById(userId).orElseThrow();
        boolean isCorrect = option.getCorrect() != null && option.getCorrect();

        // Record the answer
        QuizMatchAnswer answer = new QuizMatchAnswer();
        answer.setMatch(match);
        answer.setPlayer(player);
        answer.setQuestion(option.getQuestion());
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
        QuizMatch match = quizMatchRepository.findById(matchId)
            .orElseThrow(() -> new ApiException("Match not found"));

        if (match.getStatus() == QuizMatchStatus.COMPLETED) {
            return buildEvent(match);
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

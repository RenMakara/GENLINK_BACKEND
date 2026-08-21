package kh.edu.acleda.campuslink.modules.entertainment.service;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.dto.websocket.MatchEvent;

public interface QuizMatchService {
    /** Joins the matchmaking queue or creates a match if someone is waiting. */
    MatchEvent matchmake(UUID quizId, UUID userId);
    
    /** Submits an answer for a question in a match. */
    MatchEvent submitAnswer(UUID matchId, UUID userId, UUID questionId, UUID optionId);
    
    /** Concludes the match, determines the winner, and awards XP. */
    MatchEvent finishMatch(UUID matchId, UUID userId);
}

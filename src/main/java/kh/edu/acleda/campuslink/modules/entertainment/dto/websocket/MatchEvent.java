package kh.edu.acleda.campuslink.modules.entertainment.dto.websocket;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.enums.QuizMatchStatus;

public record MatchEvent(
    UUID matchId,
    UUID quizId,
    QuizMatchStatus status,
    UUID player1Id,
    String player1Name,
    int player1Score,
    UUID player2Id,
    String player2Name,
    int player2Score,
    UUID winnerId
) {}

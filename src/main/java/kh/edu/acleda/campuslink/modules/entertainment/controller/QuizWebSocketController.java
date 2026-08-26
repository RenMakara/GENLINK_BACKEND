package kh.edu.acleda.campuslink.modules.entertainment.controller;

import java.security.Principal;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.dto.websocket.MatchEvent;
import kh.edu.acleda.campuslink.modules.entertainment.dto.websocket.SubmitAnswerEvent;
import kh.edu.acleda.campuslink.modules.entertainment.service.QuizMatchService;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import lombok.RequiredArgsConstructor;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class QuizWebSocketController {

    private final QuizMatchService quizMatchService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    @MessageMapping("/quiz/matchmake/{quizId}")
    public void matchmake(@DestinationVariable UUID quizId, Principal principal) {
        if (principal == null) throw new ApiException("Unauthorized");
        User user = userRepository.findByEmail(principal.getName())
                .orElseGet(() -> userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ApiException("User not found")));
        UUID userId = user.getId();
        
        MatchEvent event = quizMatchService.matchmake(quizId, userId);
        
        // Find player 1's username/email for STOMP routing
        User p1 = userRepository.findById(event.player1Id()).orElseThrow();
        messagingTemplate.convertAndSendToUser(p1.getEmail(), "/queue/match", event);
        
        // If an opponent was found, notify them too
        if (event.player2Id() != null) {
            User p2 = userRepository.findById(event.player2Id()).orElseThrow();
            messagingTemplate.convertAndSendToUser(p2.getEmail(), "/queue/match", event);
        }
    }

    @MessageMapping("/quiz/match/{matchId}/submit")
    public void submitAnswer(@DestinationVariable UUID matchId, 
                             SubmitAnswerEvent request,
                             Principal principal) {
        if (principal == null) throw new ApiException("Unauthorized");
        User user = userRepository.findByEmail(principal.getName())
                .orElseGet(() -> userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ApiException("User not found")));
        UUID userId = user.getId();
        
        MatchEvent event = quizMatchService.submitAnswer(matchId, userId, request.questionId(), request.optionId());
        
        // Broadcast the updated score and status to both players
        messagingTemplate.convertAndSend("/topic/quiz/match/" + matchId, event);
    }

    @MessageMapping("/quiz/match/{matchId}/finish")
    public void finishMatch(@DestinationVariable UUID matchId, Principal principal) {
        if (principal == null) throw new ApiException("Unauthorized");
        User user = userRepository.findByEmail(principal.getName())
                .orElseGet(() -> userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ApiException("User not found")));
        
        MatchEvent event = quizMatchService.finishMatch(matchId, user.getId());
        messagingTemplate.convertAndSend("/topic/quiz/match/" + matchId, event);
    }
}

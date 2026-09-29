package kh.edu.acleda.campuslink.modules.entertainment;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.controller.QuizWebSocketController;
import kh.edu.acleda.campuslink.modules.entertainment.dto.websocket.MatchEvent;
import kh.edu.acleda.campuslink.modules.entertainment.enums.QuizMatchStatus;
import kh.edu.acleda.campuslink.modules.entertainment.service.QuizMatchService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizWebSocketControllerTest {

    private static final UUID MATCH_ID = UUID.randomUUID();
    private static final UUID QUIZ_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private QuizMatchService matchService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private UserRepository userRepository;

    private QuizWebSocketController controller;
    private Principal principal;

    @BeforeEach
    void setUp() {
        controller = new QuizWebSocketController(matchService, messagingTemplate, userRepository);
        User user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(userRepository.findByEmail("player@example.com")).thenReturn(Optional.of(user));
        principal = () -> "player@example.com";
    }

    @Test
    void finishRouteDoesNotBroadcastWhenServerRejectsAnEarlyFinish() {
        when(matchService.finishMatch(MATCH_ID, USER_ID))
            .thenThrow(new ApiException("Both players must answer or skip every question", "MATCH_QUESTIONS_INCOMPLETE"));

        assertThatThrownBy(() -> controller.finishMatch(MATCH_ID, principal))
            .isInstanceOf(ApiException.class).hasMessageContaining("every question");
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/quiz/match/" + MATCH_ID),
            any(MatchEvent.class));
    }

    @Test
    void finishRouteBroadcastsTheServerResultAfterValidation() {
        MatchEvent event = new MatchEvent(MATCH_ID, QUIZ_ID, QuizMatchStatus.COMPLETED,
            USER_ID, "Player", 10, null, null, 0, USER_ID);
        when(matchService.finishMatch(MATCH_ID, USER_ID)).thenReturn(event);

        controller.finishMatch(MATCH_ID, principal);

        verify(messagingTemplate).convertAndSend("/topic/quiz/match/" + MATCH_ID, event);
    }
}

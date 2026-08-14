package kh.edu.acleda.campuslink.shared.websocket;

import kh.edu.acleda.campuslink.shared.security.JwtService;
import kh.edu.acleda.campuslink.shared.security.TokenType;
import kh.edu.acleda.campuslink.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock private JwtService jwtService;
    @Mock private UserDetailsService userDetailsService;
    @Mock private MessageChannel channel;

    @InjectMocks
    private StompAuthChannelInterceptor interceptor;

    private AuthenticatedUser userDetails;
    private static final String VALID_TOKEN = "valid.token.here";

    @BeforeEach
    void setUp() {
        userDetails = mock(AuthenticatedUser.class);
    }

    @Test
    void missingJwtOnConnectIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Missing or invalid JWT");
    }

    @Test
    void invalidJwtOnConnectIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer invalid-token");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtService.validateToken("invalid-token")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Missing or invalid JWT");
    }

    @Test
    void adminTokenIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + VALID_TOKEN);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtService.getTokenType(VALID_TOKEN)).thenReturn(TokenType.ADMIN);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only MOBILE tokens are allowed");
    }

    @Test
    void adminMfaChallengeTokenIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + VALID_TOKEN);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtService.getTokenType(VALID_TOKEN)).thenReturn(TokenType.ADMIN_MFA_CHALLENGE);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only MOBILE tokens are allowed");
    }

    @Test
    void mobileTokenIsAccepted() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + VALID_TOKEN);
        accessor.setLeaveMutable(true);
        
        @SuppressWarnings("unchecked")
        Message<byte[]> message = mock(Message.class);
        when(message.getHeaders()).thenReturn(accessor.getMessageHeaders());

        when(jwtService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtService.getTokenType(VALID_TOKEN)).thenReturn(TokenType.MOBILE);
        when(jwtService.getUsername(VALID_TOKEN)).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);
        when(userDetails.isEnabled()).thenReturn(true);
        when(jwtService.getTokenVersion(VALID_TOKEN)).thenReturn(1);
        when(userDetails.getTokenVersion()).thenReturn(1);

        Message<?> result = interceptor.preSend(message, channel);
        
        assertThat(result).isNotNull();
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo(userDetails.getUsername());
    }

    @Test
    void unauthenticatedSubscribeIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/queue/chat");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Unauthenticated subscription attempt");
    }

    @Test
    void authenticatedSubscribeToUserQueueChatIsAccepted() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/queue/chat");
        accessor.setUser(mock(Principal.class));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);
        assertThat(result).isSameAs(message);
    }

    @Test
    void directSubscribeToPhysicalQueueIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/queue/chat-user123");
        accessor.setUser(mock(Principal.class));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Subscription to destination not allowed");
    }

    @Test
    void directSubscribeToAnotherUsersPrivateQueueIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/otheruser/queue/chat");
        accessor.setUser(mock(Principal.class));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Subscription to destination not allowed");
    }
}

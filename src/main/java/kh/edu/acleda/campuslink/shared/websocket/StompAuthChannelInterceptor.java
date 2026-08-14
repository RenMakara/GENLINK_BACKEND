package kh.edu.acleda.campuslink.shared.websocket;

import kh.edu.acleda.campuslink.shared.security.AuthenticatedUser;
import kh.edu.acleda.campuslink.shared.security.JwtService;
import kh.edu.acleda.campuslink.shared.security.TokenType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * Intercepts STOMP messages to authenticate the CONNECT frame using a JWT.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public StompAuthChannelInterceptor(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            if (token == null || !jwtService.validateToken(token)) {
                throw new AccessDeniedException("Missing or invalid JWT");
            }
            
            TokenType tokenType = jwtService.getTokenType(token);
            if (tokenType != TokenType.MOBILE) {
                log.warn("Rejected STOMP connection attempt with token type {}", tokenType);
                throw new AccessDeniedException("Only MOBILE tokens are allowed");
            }

            String username = jwtService.getUsername(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (!isUsable(token, userDetails)) {
                throw new AccessDeniedException("User account disabled or token superseded");
            }
            
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            accessor.setUser(authentication);
        } else if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            if (accessor.getUser() == null) {
                throw new AccessDeniedException("Unauthenticated subscription attempt");
            }
            String destination = accessor.getDestination();
            if (destination == null || !destination.equals("/user/queue/chat")) {
                throw new AccessDeniedException("Subscription to destination not allowed");
            }
        }
        
        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        
        String passcode = accessor.getPasscode();
        if (passcode != null && !passcode.isBlank()) {
            return passcode;
        }

        return null;
    }

    private boolean isUsable(String token, UserDetails userDetails) {
        if (!userDetails.isEnabled()) {
            log.warn("Rejected STOMP request for disabled account: {}", userDetails.getUsername());
            return false;
        }
        if (!isTokenVersionCurrent(token, userDetails)) {
            log.debug("Rejected superseded token in STOMP for {}", userDetails.getUsername());
            return false;
        }
        return true;
    }

    private boolean isTokenVersionCurrent(String token, UserDetails userDetails) {
        Integer tokenVersion = jwtService.getTokenVersion(token);
        if (tokenVersion == null || !(userDetails instanceof AuthenticatedUser authenticatedUser)) {
            return false;
        }
        return tokenVersion == authenticatedUser.getTokenVersion();
    }
}

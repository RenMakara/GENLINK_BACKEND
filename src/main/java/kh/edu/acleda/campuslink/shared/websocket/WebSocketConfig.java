package kh.edu.acleda.campuslink.shared.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket for real-time chat (Phase 3).
 *
 * <p>Delivery is push-only through per-user queues: the server sends each member their own copy
 * (see {@code ChatRealtimeListener}), so no client subscribes to a shared room topic and there is
 * nothing to authorize per subscription. REST stays the source of truth; the socket only pushes
 * an already-persisted message.
 *
 * <p>Uses the in-memory simple broker — correct only for a single instance, which the
 * docker-compose interlock already enforces (same constraint as the rate limiter and MFA store).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authChannelInterceptor;

    public WebSocketConfig(StompAuthChannelInterceptor authChannelInterceptor) {
        this.authChannelInterceptor = authChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // The handshake is permitted in SecurityConfig; the trust boundary is the JWT on the
        // STOMP CONNECT frame (StompAuthChannelInterceptor), not the HTTP origin. Nginx forwards
        // the upgrade here (see deploy/nginx/genlink.conf).
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}

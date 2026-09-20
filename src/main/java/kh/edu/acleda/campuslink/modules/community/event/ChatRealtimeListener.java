package kh.edu.acleda.campuslink.modules.community.event;

import java.util.List;
import java.util.Map;
import java.util.Set;
import kh.edu.acleda.campuslink.modules.community.enums.MemberStatus;
import kh.edu.acleda.campuslink.modules.community.repository.ChatRoomMemberIdentity;
import kh.edu.acleda.campuslink.modules.community.repository.ChatRoomMemberRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes saved messages to active room members via WebSocket and emits notifications.
 */
@Slf4j
@Component
public class ChatRealtimeListener {

    static final String CHAT_TEMPLATE = "CHAT_NEW_MESSAGE";
    static final String CHAT_TYPE = "CHAT";

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomMemberRepository memberRepository;
    private final NotificationEmitter notificationEmitter;

    public ChatRealtimeListener(SimpMessagingTemplate messagingTemplate,
                                ChatRoomMemberRepository memberRepository,
                                NotificationEmitter notificationEmitter) {
        this.messagingTemplate = messagingTemplate;
        this.memberRepository = memberRepository;
        this.notificationEmitter = notificationEmitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSaved(ChatMessageSavedEvent event) {
        List<ChatRoomMemberIdentity> activeMembers = memberRepository
                .findActiveMemberIdentities(event.roomId(), MemberStatus.ACTIVE);

        String senderName = resolveSenderName(event);
        String preview = resolvePreview(event);

        for (ChatRoomMemberIdentity member : activeMembers) {
            // Do not echo the message back to the sender over WS. They receive the REST response.
            if (!member.getUserId().equals(event.senderId())) {
                String destinationEmail = member.getEmail();
                log.debug("Pushing message {} to user {}", event.message().messageId(), destinationEmail);
                
                messagingTemplate.convertAndSendToUser(
                        destinationEmail,
                        "/queue/chat",
                        event.message().withMine(false).withRoomId(event.roomId())
                );

                try {
                    notificationEmitter.emit(new NotificationRequest(
                            member.getUserId(),
                            CHAT_TEMPLATE,
                            CHAT_TYPE,
                            Map.of("senderName", senderName, "preview", preview),
                            "CHAT_ROOM",
                            event.roomId(),
                            "chat-msg:" + event.message().messageId() + ":" + member.getUserId(),
                            Set.of(NotificationChannel.IN_APP, NotificationChannel.PUSH, NotificationChannel.MOBILE_PUSH)
                    ));
                } catch (RuntimeException exception) {
                    log.warn("Could not emit chat notification recipient={} message={}",
                            member.getUserId(), event.message().messageId(), exception);
                }
            }
        }
    }

    private static String resolveSenderName(ChatMessageSavedEvent event) {
        if (event.message() != null && event.message().sender() != null) {
            String fullName = event.message().sender().fullName();
            if (fullName != null && !fullName.isBlank()) {
                return fullName.strip();
            }
            String username = event.message().sender().username();
            if (username != null && !username.isBlank()) {
                return username.strip();
            }
        }
        return "Someone";
    }

    private static String resolvePreview(ChatMessageSavedEvent event) {
        if (event.message() != null && event.message().content() != null && !event.message().content().isBlank()) {
            String trimmed = event.message().content().strip();
            return trimmed.length() > 100 ? trimmed.substring(0, 97) + "..." : trimmed;
        }
        return "[Attachment]";
    }
}


package kh.edu.acleda.campuslink.modules.community.event;

import kh.edu.acleda.campuslink.modules.community.enums.MemberStatus;
import kh.edu.acleda.campuslink.modules.community.repository.ChatRoomMemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import kh.edu.acleda.campuslink.modules.community.repository.ChatRoomMemberIdentity;
import java.util.List;

/**
 * Pushes saved messages to active room members via WebSocket.
 */
@Slf4j
@Component
public class ChatRealtimeListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomMemberRepository memberRepository;

    public ChatRealtimeListener(SimpMessagingTemplate messagingTemplate,
                                ChatRoomMemberRepository memberRepository) {
        this.messagingTemplate = messagingTemplate;
        this.memberRepository = memberRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSaved(ChatMessageSavedEvent event) {
        List<ChatRoomMemberIdentity> activeMembers = memberRepository
                .findActiveMemberIdentities(event.roomId(), MemberStatus.ACTIVE);

        for (ChatRoomMemberIdentity member : activeMembers) {
            // Do not echo the message back to the sender over WS. They receive the REST response.
            if (!member.getUserId().equals(event.senderId())) {
                String destinationEmail = member.getEmail();
                log.debug("Pushing message {} to user {}", event.message().messageId(), destinationEmail);
                
                messagingTemplate.convertAndSendToUser(
                        destinationEmail,
                        "/queue/chat",
                        event.message().withMine(false)
                );
            }
        }
    }
}

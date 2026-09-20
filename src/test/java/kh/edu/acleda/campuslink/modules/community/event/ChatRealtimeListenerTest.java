package kh.edu.acleda.campuslink.modules.community.event;

import kh.edu.acleda.campuslink.modules.community.dto.response.ChatMessageResponse;
import kh.edu.acleda.campuslink.modules.community.enums.MemberStatus;
import kh.edu.acleda.campuslink.modules.community.repository.ChatRoomMemberIdentity;
import kh.edu.acleda.campuslink.modules.community.repository.ChatRoomMemberRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ChatRealtimeListenerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private ChatRoomMemberRepository memberRepository;
    @Mock private NotificationEmitter notificationEmitter;

    @InjectMocks private ChatRealtimeListener listener;

    private ChatRoomMemberIdentity createMockIdentity(UUID id, String email) {
        return new ChatRoomMemberIdentity() {
            @Override
            public UUID getUserId() {
                return id;
            }
            @Override
            public String getEmail() {
                return email;
            }
        };
    }

    @Test
    void pushesToAllActiveMembersExceptSenderUsingEmailIdentityAndSetsMineFalse() {
        // Arrange
        UUID roomId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID activeMember1Id = UUID.randomUUID();
        UUID activeMember2Id = UUID.randomUUID();
        
        ChatRoomMemberIdentity senderIdentity = createMockIdentity(senderId, "sender@example.com");
        ChatRoomMemberIdentity activeMember1Identity = createMockIdentity(activeMember1Id, "member1@example.com");
        ChatRoomMemberIdentity activeMember2Identity = createMockIdentity(activeMember2Id, "member2@example.com");

        when(memberRepository.findActiveMemberIdentities(roomId, MemberStatus.ACTIVE))
                .thenReturn(List.of(senderIdentity, activeMember1Identity, activeMember2Identity));

        ChatMessageResponse senderPerspectiveMessage = new ChatMessageResponse(
                UUID.randomUUID(), null, null, "Hello", null, null, null, true, null
        );
        ChatMessageSavedEvent event = new ChatMessageSavedEvent(roomId, senderId, senderPerspectiveMessage);

        // Act
        listener.onMessageSaved(event);

        // Assert
        ChatMessageResponse expectedRecipientMessage = senderPerspectiveMessage.withMine(false).withRoomId(roomId);

        // Should send to member1 using EMAIL
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq("member1@example.com"),
                eq("/queue/chat"),
                eq(expectedRecipientMessage)
        );
        
        // Should send to member2 using EMAIL
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq("member2@example.com"),
                eq("/queue/chat"),
                eq(expectedRecipientMessage)
        );
        
        // Should NOT send to sender
        verify(messagingTemplate, never()).convertAndSendToUser(
                eq("sender@example.com"),
                any(),
                any()
        );

        // Verify notifications emitted to active members (excluding sender)
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter, times(2)).emit(captor.capture());
        List<NotificationRequest> emitted = captor.getAllValues();
        assertThat(emitted).extracting(NotificationRequest::recipientUserId)
                .containsExactlyInAnyOrder(activeMember1Id, activeMember2Id);
        assertThat(emitted.get(0).templateKey()).isEqualTo("CHAT_NEW_MESSAGE");
        assertThat(emitted.get(0).referenceType()).isEqualTo("CHAT_ROOM");
        assertThat(emitted.get(0).referenceId()).isEqualTo(roomId);
    }
}


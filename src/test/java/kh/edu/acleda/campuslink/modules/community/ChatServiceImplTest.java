package kh.edu.acleda.campuslink.modules.community;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.CreateGroupRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.SendMessageRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.ChatMessageResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.ChatRoomResponse;
import kh.edu.acleda.campuslink.modules.community.entity.ChatMessage;
import kh.edu.acleda.campuslink.modules.community.entity.ChatRoom;
import kh.edu.acleda.campuslink.modules.community.entity.ChatRoomMember;
import kh.edu.acleda.campuslink.modules.community.entity.MessageRead;
import kh.edu.acleda.campuslink.modules.community.enums.ChatType;
import kh.edu.acleda.campuslink.modules.community.enums.MemberStatus;
import kh.edu.acleda.campuslink.modules.community.enums.MessageType;
import kh.edu.acleda.campuslink.modules.community.repository.ChatMessageRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ChatRoomMemberRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ChatRoomRepository;
import kh.edu.acleda.campuslink.modules.community.repository.MessageReadRepository;
import kh.edu.acleda.campuslink.modules.community.service.impl.ChatServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceImplTest {

    private static final UUID ME_ID = UUID.randomUUID();
    private static final UUID OTHER_ID = UUID.randomUUID();
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID MSG_ID = UUID.randomUUID();

    @Mock private ChatRoomRepository roomRepository;
    @Mock private ChatRoomMemberRepository memberRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private MessageReadRepository messageReadRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private ChatServiceImpl service;
    private User me;
    private User other;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        service = new ChatServiceImpl(roomRepository, memberRepository, messageRepository,
            messageReadRepository, userRepository, currentUserProvider);

        me = userWithId(ME_ID, "me");
        other = userWithId(OTHER_ID, "other");
        room = ChatRoom.builder().chatType(ChatType.PRIVATE).createdByUser(me).build();
        ReflectionTestUtils.setField(room, "id", ROOM_ID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(ME_ID);
        when(userRepository.findById(ME_ID)).thenReturn(Optional.of(me));
        when(userRepository.findById(OTHER_ID)).thenReturn(Optional.of(other));
        when(roomRepository.save(any(ChatRoom.class))).thenAnswer(i -> {
            ChatRoom r = i.getArgument(0);
            if (r.getId() == null) {
                ReflectionTestUtils.setField(r, "id", ROOM_ID);
            }
            return r;
        });
        when(memberRepository.save(any(ChatRoomMember.class))).thenAnswer(i -> i.getArgument(0));
        when(memberRepository.findByChatRoomIdAndStatus(ROOM_ID, MemberStatus.ACTIVE))
            .thenReturn(List.of(activeMember(me), activeMember(other)));
    }

    private User userWithId(UUID id, String name) {
        User u = new User();
        u.setUsername(name);
        u.setFullName(name);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private ChatRoomMember activeMember(User user) {
        return ChatRoomMember.builder().chatRoom(room).user(user).status(MemberStatus.ACTIVE).build();
    }

    // ---------------------------------------------------------- private chat

    @Test
    void openPrivateChatCreatesRoomAndBothMembersWhenNoneExists() {
        when(roomRepository.findPrivateRoomBetween(ChatType.PRIVATE, ME_ID, OTHER_ID))
            .thenReturn(Optional.empty());

        ChatRoomResponse response = service.openPrivateChat(OTHER_ID);

        assertThat(response.type()).isEqualTo(ChatType.PRIVATE);
        assertThat(response.members()).hasSize(2);
        verify(roomRepository).save(any(ChatRoom.class));
        verify(memberRepository, org.mockito.Mockito.times(2)).save(any(ChatRoomMember.class));
    }

    @Test
    void openPrivateChatReusesTheExistingRoom() {
        when(roomRepository.findPrivateRoomBetween(ChatType.PRIVATE, ME_ID, OTHER_ID))
            .thenReturn(Optional.of(room));
        when(memberRepository.findByChatRoomIdAndUserId(eq(ROOM_ID), any()))
            .thenReturn(Optional.of(activeMember(me)));

        service.openPrivateChat(OTHER_ID);

        verify(roomRepository, never()).save(any(ChatRoom.class));
    }

    @Test
    void cannotOpenAPrivateChatWithYourself() {
        assertThatThrownBy(() -> service.openPrivateChat(ME_ID))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "CHAT_SELF");
    }

    // ----------------------------------------------------------------- group

    @Test
    void createGroupAddsCreatorPlusMembers() {
        when(userRepository.findAllById(any())).thenReturn(List.of(other));

        ChatRoomResponse response = service.createGroup(new CreateGroupRequest("Study group", List.of(OTHER_ID)));

        assertThat(response.type()).isEqualTo(ChatType.GROUP);
        assertThat(response.name()).isEqualTo("Study group");
        // creator + one other = two member saves
        verify(memberRepository, org.mockito.Mockito.times(2)).save(any(ChatRoomMember.class));
    }

    @Test
    void createGroupWithOnlyYourselfIsRejected() {
        when(userRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.createGroup(new CreateGroupRequest("Solo", List.of(ME_ID))))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "CHAT_GROUP_EMPTY");
    }

    // -------------------------------------------------------- send: security

    @Test
    void sendingToARoomYouAreNotInIsNotFound() {
        when(memberRepository.findByChatRoomIdAndUserId(ROOM_ID, ME_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendMessage(ROOM_ID, new SendMessageRequest(MessageType.TEXT, "hi", null, null)))
            .isInstanceOf(NotFoundException.class);
        verify(messageRepository, never()).save(any());
    }

    // ------------------------------------------------------ send: validation

    @Test
    void textMessageWithoutContentIsRejected() {
        activeMembership();
        assertThatThrownBy(() -> service.sendMessage(ROOM_ID, new SendMessageRequest(MessageType.TEXT, "  ", null, null)))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "VALIDATION_ERROR");
    }

    @Test
    void imageMessageWithoutMediaUrlIsRejected() {
        activeMembership();
        assertThatThrownBy(() -> service.sendMessage(ROOM_ID, new SendMessageRequest(MessageType.IMAGE, null, null, null)))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "VALIDATION_ERROR");
    }

    @Test
    void systemMessagesCannotBeSentByClients() {
        activeMembership();
        assertThatThrownBy(() -> service.sendMessage(ROOM_ID, new SendMessageRequest(MessageType.SYSTEM, "x", null, null)))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "CHAT_MESSAGE_TYPE_INVALID");
    }

    @Test
    void replyingToAMessageThatIsNotInTheRoomIsNotFound() {
        activeMembership();
        UUID replyId = UUID.randomUUID();
        when(messageRepository.findByIdAndChatRoomIdAndDeletedAtIsNull(replyId, ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendMessage(ROOM_ID,
            new SendMessageRequest(MessageType.TEXT, "re", null, replyId)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void sendingATextMessageStoresItAndBumpsRoomActivity() {
        activeMembership();
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(i -> {
            ChatMessage m = i.getArgument(0);
            ReflectionTestUtils.setField(m, "id", MSG_ID);
            ReflectionTestUtils.setField(m, "createdAt", Instant.now());
            return m;
        });

        ChatMessageResponse response = service.sendMessage(ROOM_ID,
            new SendMessageRequest(MessageType.TEXT, "hello #genlink", null, null));

        assertThat(response.messageId()).isEqualTo(MSG_ID);
        assertThat(response.content()).isEqualTo("hello #genlink");
        assertThat(response.mine()).isTrue();
        verify(roomRepository).touch(eq(ROOM_ID), any(Instant.class));
    }

    // ------------------------------------------------------------ delete/read

    @Test
    void youCannotDeleteSomeoneElsesMessage() {
        activeMembership();
        ChatMessage theirs = ChatMessage.builder().chatRoom(room).sender(other).messageType(MessageType.TEXT)
            .content("theirs").build();
        ReflectionTestUtils.setField(theirs, "id", MSG_ID);
        when(messageRepository.findByIdAndChatRoomIdAndDeletedAtIsNull(MSG_ID, ROOM_ID)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.deleteMessage(ROOM_ID, MSG_ID)).isInstanceOf(NotFoundException.class);
        assertThat(theirs.getDeletedAt()).isNull();
    }

    @Test
    void deletingYourOwnMessageSoftDeletesIt() {
        activeMembership();
        ChatMessage mine = ChatMessage.builder().chatRoom(room).sender(me).messageType(MessageType.TEXT)
            .content("mine").build();
        ReflectionTestUtils.setField(mine, "id", MSG_ID);
        when(messageRepository.findByIdAndChatRoomIdAndDeletedAtIsNull(MSG_ID, ROOM_ID)).thenReturn(Optional.of(mine));

        service.deleteMessage(ROOM_ID, MSG_ID);

        assertThat(mine.getDeletedAt()).isNotNull();
    }

    @Test
    void markReadRecordsTheLatestMessageWhenItIsFromSomeoneElse() {
        activeMembership();
        ChatMessage latest = ChatMessage.builder().chatRoom(room).sender(other).messageType(MessageType.TEXT)
            .content("newest").build();
        ReflectionTestUtils.setField(latest, "id", MSG_ID);
        when(messageRepository.findFirstByChatRoomIdAndDeletedAtIsNullOrderByCreatedAtDesc(ROOM_ID))
            .thenReturn(Optional.of(latest));
        when(messageReadRepository.existsByMessageIdAndUserId(MSG_ID, ME_ID)).thenReturn(false);

        service.markRead(ROOM_ID);

        verify(messageReadRepository).save(any(MessageRead.class));
    }

    @Test
    void gettingARoomYouAreNotInIsNotFound() {
        when(memberRepository.findByChatRoomIdAndUserId(ROOM_ID, ME_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRoom(ROOM_ID)).isInstanceOf(NotFoundException.class);
    }

    /** Stub the caller as an ACTIVE member of the room. */
    private void activeMembership() {
        when(memberRepository.findByChatRoomIdAndUserId(ROOM_ID, ME_ID))
            .thenReturn(Optional.of(activeMember(me)));
    }
}

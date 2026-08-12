package kh.edu.acleda.campuslink.modules.community.service.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.CreateGroupRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.SendMessageRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.ChatMessageResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.ChatParticipant;
import kh.edu.acleda.campuslink.modules.community.dto.response.ChatRoomResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.ChatRoomSummaryResponse;
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
import kh.edu.acleda.campuslink.modules.community.service.ChatService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private static final int SNIPPET_MAX = 80;

    private final ChatRoomRepository roomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final ChatMessageRepository messageRepository;
    private final MessageReadRepository messageReadRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public ChatServiceImpl(ChatRoomRepository roomRepository,
                           ChatRoomMemberRepository memberRepository,
                           ChatMessageRepository messageRepository,
                           MessageReadRepository messageReadRepository,
                           UserRepository userRepository,
                           CurrentUserProvider currentUserProvider) {
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.messageReadRepository = messageReadRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    // ----------------------------------------------------------------- rooms

    @Override
    @Transactional
    public ChatRoomResponse openPrivateChat(UUID otherUserId) {
        UUID meId = currentUserProvider.getCurrentUserId();
        if (meId.equals(otherUserId)) {
            throw new ApiException("You cannot open a chat with yourself", "CHAT_SELF");
        }
        User me = loadUser(meId);
        User other = loadUser(otherUserId);

        ChatRoom room = roomRepository.findPrivateRoomBetween(ChatType.PRIVATE, meId, otherUserId)
            .map(existing -> {
                // Reuse the thread; if either side had left, re-activate them.
                ensureActive(existing, me);
                ensureActive(existing, other);
                return existing;
            })
            .orElseGet(() -> {
                ChatRoom created = roomRepository.save(ChatRoom.builder()
                    .chatType(ChatType.PRIVATE).createdByUser(me).build());
                memberRepository.save(activeMember(created, me));
                memberRepository.save(activeMember(created, other));
                log.info("Private chat opened: room={} between {} and {}", created.getId(), meId, otherUserId);
                return created;
            });
        return toRoomResponse(room);
    }

    @Override
    @Transactional
    public ChatRoomResponse createGroup(CreateGroupRequest request) {
        UUID meId = currentUserProvider.getCurrentUserId();
        User me = loadUser(meId);

        // Distinct, minus the creator (added automatically), and only ids that resolve to a user.
        Set<UUID> otherIds = new LinkedHashSet<>(request.memberIds());
        otherIds.remove(meId);
        List<User> others = userRepository.findAllById(otherIds);
        if (others.isEmpty()) {
            throw new ApiException("A group needs at least one other member", "CHAT_GROUP_EMPTY");
        }

        ChatRoom room = roomRepository.save(ChatRoom.builder()
            .chatType(ChatType.GROUP).name(request.name().strip()).createdByUser(me).build());
        memberRepository.save(activeMember(room, me));
        for (User other : others) {
            memberRepository.save(activeMember(room, other));
        }
        log.info("Group chat created: room={} name='{}' by {} with {} members",
            room.getId(), room.getName(), meId, others.size() + 1);
        return toRoomResponse(room);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatRoomSummaryResponse> myChats(Pageable pageable) {
        UUID meId = currentUserProvider.getCurrentUserId();
        return PageResponse.from(
            memberRepository.findMyRooms(meId, MemberStatus.ACTIVE, pageable),
            membership -> toSummary(membership.getChatRoom(), meId));
    }

    @Override
    @Transactional(readOnly = true)
    public ChatRoomResponse getRoom(UUID roomId) {
        requireActiveMember(roomId, currentUserProvider.getCurrentUserId());
        return toRoomResponse(loadRoom(roomId));
    }

    @Override
    @Transactional
    public void leaveRoom(UUID roomId) {
        UUID meId = currentUserProvider.getCurrentUserId();
        ChatRoomMember membership = requireActiveMember(roomId, meId);
        membership.setStatus(MemberStatus.LEFT);
        membership.setLeftAt(LocalDateTime.now());
        memberRepository.save(membership);
        log.info("User {} left chat room {}", meId, roomId);
    }

    // -------------------------------------------------------------- messages

    @Override
    @Transactional
    public ChatMessageResponse sendMessage(UUID roomId, SendMessageRequest request) {
        UUID meId = currentUserProvider.getCurrentUserId();
        ChatRoomMember membership = requireActiveMember(roomId, meId);
        ChatRoom room = membership.getChatRoom();
        User me = loadUser(meId);

        MessageType type = validatePayload(request);
        ChatMessage replyTo = resolveReplyTarget(roomId, request.replyToMessageId());

        ChatMessage message = messageRepository.save(ChatMessage.builder()
            .chatRoom(room)
            .sender(me)
            .messageType(type)
            .content(request.content())
            .mediaUrl(request.mediaUrl())
            .replyToMessage(replyTo)
            .build());
        // Bump last-activity so this room rises in every member's chat list.
        roomRepository.touch(roomId, Instant.now());
        return toMessageResponse(message, meId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatMessageResponse> messages(UUID roomId, Pageable pageable) {
        UUID meId = currentUserProvider.getCurrentUserId();
        requireActiveMember(roomId, meId);
        return PageResponse.from(
            messageRepository.findByChatRoomIdAndDeletedAtIsNull(roomId, pageable),
            message -> toMessageResponse(message, meId));
    }

    @Override
    @Transactional
    public void deleteMessage(UUID roomId, UUID messageId) {
        UUID meId = currentUserProvider.getCurrentUserId();
        requireActiveMember(roomId, meId);
        ChatMessage message = messageRepository.findByIdAndChatRoomIdAndDeletedAtIsNull(messageId, roomId)
            .orElseThrow(() -> new NotFoundException("Message not found"));
        // Author-only, and not-found (not forbidden) for anyone else so it reveals nothing.
        if (!message.getSender().getId().equals(meId)) {
            throw new NotFoundException("Message not found");
        }
        message.setDeletedAt(Instant.now());
        messageRepository.save(message);
        log.info("Message {} soft-deleted by author {} in room {}", messageId, meId, roomId);
    }

    // ------------------------------------------------------------------ read

    @Override
    @Transactional
    public void markRead(UUID roomId) {
        UUID meId = currentUserProvider.getCurrentUserId();
        requireActiveMember(roomId, meId);
        messageRepository.findFirstByChatRoomIdAndDeletedAtIsNullOrderByCreatedAtDesc(roomId)
            .filter(latest -> !latest.getSender().getId().equals(meId))
            .filter(latest -> !messageReadRepository.existsByMessageIdAndUserId(latest.getId(), meId))
            .ifPresent(latest -> messageReadRepository.save(MessageRead.builder()
                .message(latest).user(loadUser(meId)).readAt(LocalDateTime.now()).build()));
    }

    @Override
    @Transactional(readOnly = true)
    public long totalUnread() {
        UUID meId = currentUserProvider.getCurrentUserId();
        long total = 0;
        for (ChatRoomMember membership : memberRepository.findByUserIdAndStatus(meId, MemberStatus.ACTIVE)) {
            total += unreadFor(membership.getChatRoom().getId(), meId);
        }
        return total;
    }

    // --------------------------------------------------------------- helpers

    private MessageType validatePayload(SendMessageRequest request) {
        MessageType type = request.type();
        if (type == MessageType.SYSTEM) {
            throw new ApiException("SYSTEM messages are server-generated", "CHAT_MESSAGE_TYPE_INVALID");
        }
        if (type == MessageType.TEXT && (request.content() == null || request.content().isBlank())) {
            throw new ApiException("A text message needs content", "VALIDATION_ERROR");
        }
        if ((type == MessageType.IMAGE || type == MessageType.VOICE)
            && (request.mediaUrl() == null || request.mediaUrl().isBlank())) {
            throw new ApiException("An image or voice message needs a mediaUrl", "VALIDATION_ERROR");
        }
        return type;
    }

    private ChatMessage resolveReplyTarget(UUID roomId, UUID replyToMessageId) {
        if (replyToMessageId == null) {
            return null;
        }
        return messageRepository.findByIdAndChatRoomIdAndDeletedAtIsNull(replyToMessageId, roomId)
            .orElseThrow(() -> new NotFoundException("The message you are replying to was not found"));
    }

    /** Live messages from other people newer than the viewer's read high-water. */
    private long unreadFor(UUID roomId, UUID meId) {
        Instant after = messageReadRepository
            .findFirstByUserIdAndMessage_ChatRoomIdOrderByMessage_CreatedAtDesc(meId, roomId)
            .map(read -> read.getMessage().getCreatedAt())
            .orElse(Instant.EPOCH);
        return messageRepository.countByChatRoomIdAndDeletedAtIsNullAndSenderIdNotAndCreatedAtAfter(
            roomId, meId, after);
    }

    /** Not-found rather than forbidden, so the API never confirms a room to a non-member. */
    private ChatRoomMember requireActiveMember(UUID roomId, UUID userId) {
        return memberRepository.findByChatRoomIdAndUserId(roomId, userId)
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("Chat not found"));
    }

    private void ensureActive(ChatRoom room, User user) {
        ChatRoomMember member = memberRepository.findByChatRoomIdAndUserId(room.getId(), user.getId())
            .orElseGet(() -> activeMember(room, user));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            member.setStatus(MemberStatus.ACTIVE);
            member.setJoinedAt(LocalDateTime.now());
            member.setLeftAt(null);
        }
        memberRepository.save(member);
    }

    private ChatRoomMember activeMember(ChatRoom room, User user) {
        return ChatRoomMember.builder()
            .chatRoom(room).user(user).status(MemberStatus.ACTIVE).joinedAt(LocalDateTime.now()).build();
    }

    private ChatRoom loadRoom(UUID roomId) {
        return roomRepository.findById(roomId)
            .orElseThrow(() -> new NotFoundException("Chat not found"));
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private ChatRoomResponse toRoomResponse(ChatRoom room) {
        List<ChatParticipant> members = memberRepository
            .findByChatRoomIdAndStatus(room.getId(), MemberStatus.ACTIVE).stream()
            .map(member -> toParticipant(member.getUser()))
            .toList();
        return new ChatRoomResponse(room.getId(), room.getChatType(), room.getName(), members, room.getCreatedAt());
    }

    private ChatRoomSummaryResponse toSummary(ChatRoom room, UUID meId) {
        ChatMessageResponse last = messageRepository
            .findFirstByChatRoomIdAndDeletedAtIsNullOrderByCreatedAtDesc(room.getId())
            .map(message -> toMessageResponse(message, meId))
            .orElse(null);
        ChatParticipant counterpart = room.getChatType() == ChatType.PRIVATE
            ? otherMember(room.getId(), meId) : null;
        String title = room.getChatType() == ChatType.GROUP ? room.getName() : null;
        return new ChatRoomSummaryResponse(
            room.getId(), room.getChatType(), title, counterpart, last,
            unreadFor(room.getId(), meId), room.getUpdatedAt());
    }

    private ChatParticipant otherMember(UUID roomId, UUID meId) {
        return memberRepository.findByChatRoomIdAndStatus(roomId, MemberStatus.ACTIVE).stream()
            .map(ChatRoomMember::getUser)
            .filter(user -> !user.getId().equals(meId))
            .findFirst()
            .map(this::toParticipant)
            .orElse(null);
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message, UUID meId) {
        ChatMessage replyTo = message.getReplyToMessage();
        ChatMessageResponse.ReplyPreview preview = replyTo == null ? null
            : new ChatMessageResponse.ReplyPreview(
                replyTo.getId(), displayName(replyTo.getSender()), replyTo.getMessageType(), snippet(replyTo));
        return new ChatMessageResponse(
            message.getId(),
            toParticipant(message.getSender()),
            message.getMessageType(),
            message.getContent(),
            message.getMediaUrl(),
            replyTo == null ? null : replyTo.getId(),
            preview,
            message.getSender().getId().equals(meId),
            message.getCreatedAt());
    }

    private ChatParticipant toParticipant(User user) {
        return new ChatParticipant(user.getId(), user.getFullName(), user.getUsername(), user.getAvatarUrl());
    }

    private String displayName(User user) {
        return user.getFullName() != null ? user.getFullName() : user.getUsername();
    }

    private String snippet(ChatMessage message) {
        return switch (message.getMessageType()) {
            case IMAGE -> "[image]";
            case VOICE -> "[voice message]";
            default -> {
                String text = message.getContent() == null ? "" : message.getContent();
                yield text.length() > SNIPPET_MAX ? text.substring(0, SNIPPET_MAX) + "…" : text;
            }
        };
    }
}

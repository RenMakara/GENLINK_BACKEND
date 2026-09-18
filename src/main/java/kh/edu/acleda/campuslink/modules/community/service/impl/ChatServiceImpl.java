package kh.edu.acleda.campuslink.modules.community.service.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.AddMembersRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.ChangeChatRoleRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.CreateGroupRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.RenameGroupRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.SendMessageRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.ChatMemberResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.ChatMessageResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.ChatParticipant;
import kh.edu.acleda.campuslink.modules.community.dto.response.ChatRoomResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.ChatRoomSummaryResponse;
import kh.edu.acleda.campuslink.modules.community.entity.ChatMessage;
import kh.edu.acleda.campuslink.modules.community.entity.ChatRoom;
import kh.edu.acleda.campuslink.modules.community.entity.ChatRoomMember;
import kh.edu.acleda.campuslink.modules.community.entity.MessageRead;
import kh.edu.acleda.campuslink.modules.community.enums.ChatRole;
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
import kh.edu.acleda.campuslink.shared.exception.ForbiddenException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import kh.edu.acleda.campuslink.modules.community.event.ChatMessageSavedEvent;

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
    private final ApplicationEventPublisher eventPublisher;

    public ChatServiceImpl(ChatRoomRepository roomRepository,
                           ChatRoomMemberRepository memberRepository,
                           ChatMessageRepository messageRepository,
                           MessageReadRepository messageReadRepository,
                           UserRepository userRepository,
                           CurrentUserProvider currentUserProvider,
                           ApplicationEventPublisher eventPublisher) {
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.messageReadRepository = messageReadRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.eventPublisher = eventPublisher;
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
                // Reuse the thread; if either side had left, re-activate them (private = MEMBER).
                ensureActive(existing, me, ChatRole.MEMBER);
                ensureActive(existing, other, ChatRole.MEMBER);
                return existing;
            })
            .orElseGet(() -> {
                ChatRoom created = roomRepository.save(ChatRoom.builder()
                    .chatType(ChatType.PRIVATE).createdByUser(me).build());
                // Private rooms have no hierarchy — both sides are plain members.
                memberRepository.save(activeMember(created, me, ChatRole.MEMBER));
                memberRepository.save(activeMember(created, other, ChatRole.MEMBER));
                log.info("Private chat opened: room={} between {} and {}", created.getId(), meId, otherUserId);
                return created;
            });
        return toRoomResponse(room, meId);
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
        memberRepository.save(activeMember(room, me, ChatRole.OWNER));
        for (User other : others) {
            memberRepository.save(activeMember(room, other, ChatRole.MEMBER));
        }
        log.info("Group chat created: room={} name='{}' by {} with {} members",
            room.getId(), room.getName(), meId, others.size() + 1);
        return toRoomResponse(room, meId);
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
        UUID meId = currentUserProvider.getCurrentUserId();
        requireActiveMember(roomId, meId);
        return toRoomResponse(loadRoom(roomId), meId);
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

    @Override
    @Transactional
    public void deleteConversation(UUID roomId) {
        UUID meId = currentUserProvider.getCurrentUserId();
        ChatRoomMember membership = requireActiveMember(roomId, meId);
        ChatRoom room = membership.getChatRoom();

        // 1. Soft-delete all messages in the conversation to clear history
        messageRepository.softDeleteAllByChatRoomId(roomId, Instant.now());

        // 2. Clear any pinned message on the room
        if (room.getPinnedMessage() != null) {
            room.setPinnedMessage(null);
            roomRepository.save(room);
        }

        // 3. Mark caller as LEFT so the conversation is removed from their active chat list
        membership.setStatus(MemberStatus.LEFT);
        membership.setLeftAt(LocalDateTime.now());
        memberRepository.save(membership);

        // 4. For 1:1 private chats, mark the other member as LEFT as well so the empty conversation doesn't linger
        if (room.getChatType() == ChatType.PRIVATE) {
            for (ChatRoomMember otherMember : memberRepository.findByChatRoomIdAndStatus(roomId, MemberStatus.ACTIVE)) {
                if (!otherMember.getUser().getId().equals(meId)) {
                    otherMember.setStatus(MemberStatus.LEFT);
                    otherMember.setLeftAt(LocalDateTime.now());
                    memberRepository.save(otherMember);
                }
            }
        }

        log.info("Conversation {} and history deleted by user {}", roomId, meId);
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
        // If this is a private chat and the other member had left, re-activate them so the thread reappears in their chats.
        if (room.getChatType() == ChatType.PRIVATE) {
            for (ChatRoomMember leftMember : memberRepository.findByChatRoomIdAndStatus(roomId, MemberStatus.LEFT)) {
                leftMember.setStatus(MemberStatus.ACTIVE);
                leftMember.setLeftAt(null);
                memberRepository.save(leftMember);
            }
        }

        // Bump last-activity so this room rises in every member's chat list.
        roomRepository.touch(roomId, Instant.now());
        
        ChatMessageResponse response = toMessageResponse(message, meId);
        eventPublisher.publishEvent(new ChatMessageSavedEvent(roomId, meId, response));
        
        return response;
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

    // --------------------------------------------------------- group management

    @Override
    @Transactional
    public ChatRoomResponse renameGroup(UUID roomId, RenameGroupRequest request) {
        UUID meId = currentUserProvider.getCurrentUserId();
        ChatRoom room = requireGroupManager(roomId, meId, false).getChatRoom();
        room.setName(request.name().strip());
        roomRepository.save(room);
        log.info("Group {} renamed by {}", roomId, meId);
        return toRoomResponse(room, meId);
    }

    @Override
    @Transactional
    public ChatRoomResponse addMembers(UUID roomId, AddMembersRequest request) {
        UUID meId = currentUserProvider.getCurrentUserId();
        ChatRoom room = requireGroupManager(roomId, meId, false).getChatRoom();
        for (UUID userId : new LinkedHashSet<>(request.userIds())) {
            userRepository.findById(userId).ifPresent(user -> ensureActive(room, user, ChatRole.MEMBER));
        }
        log.info("Members added to group {} by {}", roomId, meId);
        return toRoomResponse(room, meId);
    }

    @Override
    @Transactional
    public void removeMember(UUID roomId, UUID userId) {
        UUID meId = currentUserProvider.getCurrentUserId();
        ChatRoomMember manager = requireGroupManager(roomId, meId, false);
        if (userId.equals(meId)) {
            throw new ApiException("Use leave to remove yourself", "CHAT_REMOVE_SELF");
        }
        ChatRoomMember target = memberRepository.findByChatRoomIdAndUserId(roomId, userId)
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("Member not found"));
        if (target.getRole() == ChatRole.OWNER) {
            throw new ApiException("The group owner cannot be removed", "CHAT_REMOVE_OWNER");
        }
        // An admin may not remove another admin; that is reserved to the owner.
        if (target.getRole() == ChatRole.ADMIN && manager.getRole() != ChatRole.OWNER) {
            throw new ForbiddenException("Only the owner can remove an admin", "CHAT_ADMIN_ONLY");
        }
        target.setStatus(MemberStatus.REMOVED);
        target.setLeftAt(LocalDateTime.now());
        memberRepository.save(target);
        log.info("Member {} removed from group {} by {}", userId, roomId, meId);
    }

    @Override
    @Transactional
    public ChatMemberResponse changeMemberRole(UUID roomId, UUID userId, ChangeChatRoleRequest request) {
        UUID meId = currentUserProvider.getCurrentUserId();
        requireGroupManager(roomId, meId, true); // owner only
        ChatRole newRole = request.role();
        if (newRole == ChatRole.OWNER) {
            throw new ApiException("Ownership transfer is not supported", "CHAT_ROLE_INVALID");
        }
        if (userId.equals(meId)) {
            throw new ApiException("You cannot change your own role", "CHAT_ROLE_SELF");
        }
        ChatRoomMember target = memberRepository.findByChatRoomIdAndUserId(roomId, userId)
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("Member not found"));
        target.setRole(newRole);
        memberRepository.save(target);
        log.info("Member {} role changed to {} in group {} by {}", userId, newRole, roomId, meId);
        return new ChatMemberResponse(toParticipant(target.getUser()), target.getRole());
    }

    @Override
    @Transactional
    public ChatRoomResponse pinMessage(UUID roomId, UUID messageId) {
        UUID meId = currentUserProvider.getCurrentUserId();
        ChatRoom room = requireGroupManager(roomId, meId, false).getChatRoom();
        ChatMessage message = messageRepository.findByIdAndChatRoomIdAndDeletedAtIsNull(messageId, roomId)
            .orElseThrow(() -> new NotFoundException("Message not found"));
        room.setPinnedMessage(message);
        roomRepository.save(room);
        log.info("Message {} pinned in group {} by {}", messageId, roomId, meId);
        return toRoomResponse(room, meId);
    }

    @Override
    @Transactional
    public ChatRoomResponse unpinMessage(UUID roomId) {
        UUID meId = currentUserProvider.getCurrentUserId();
        ChatRoom room = requireGroupManager(roomId, meId, false).getChatRoom();
        room.setPinnedMessage(null);
        roomRepository.save(room);
        return toRoomResponse(room, meId);
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
        // TODO (chat phase 2 / hardening): mediaUrl is trusted as-is today. Validate it against the
        // media module — a real, CONFIRMED upload owned by the sender (media presign/confirm flow) —
        // so a client cannot attach an arbitrary or someone else's URL. Add VIDEO to this branch too.
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

    /**
     * Gate a group-management action. The caller must be an ACTIVE member of a GROUP room and hold
     * the required role. A member who simply lacks the role gets 403 (they already know the room
     * exists), whereas a non-member gets 404 from {@link #requireActiveMember}.
     */
    private ChatRoomMember requireGroupManager(UUID roomId, UUID userId, boolean ownerOnly) {
        ChatRoomMember member = requireActiveMember(roomId, userId);
        if (member.getChatRoom().getChatType() != ChatType.GROUP) {
            throw new ApiException("This action only applies to group chats", "CHAT_NOT_GROUP");
        }
        boolean allowed = ownerOnly
            ? member.getRole() == ChatRole.OWNER
            : member.getRole() == ChatRole.OWNER || member.getRole() == ChatRole.ADMIN;
        if (!allowed) {
            throw new ForbiddenException(
                ownerOnly ? "Only the group owner can do this" : "You must be a group admin",
                ownerOnly ? "CHAT_OWNER_ONLY" : "CHAT_ADMIN_ONLY");
        }
        return member;
    }

    /** Re-add a user to a room as an ACTIVE member with the given role, reusing their row if any. */
    private ChatRoomMember ensureActive(ChatRoom room, User user, ChatRole role) {
        ChatRoomMember member = memberRepository.findByChatRoomIdAndUserId(room.getId(), user.getId())
            .orElseGet(() -> activeMember(room, user, role));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            member.setStatus(MemberStatus.ACTIVE);
            member.setRole(role);
            member.setJoinedAt(LocalDateTime.now());
            member.setLeftAt(null);
        }
        return memberRepository.save(member);
    }

    private ChatRoomMember activeMember(ChatRoom room, User user, ChatRole role) {
        return ChatRoomMember.builder()
            .chatRoom(room).user(user).status(MemberStatus.ACTIVE).role(role)
            .joinedAt(LocalDateTime.now()).build();
    }

    private ChatRoom loadRoom(UUID roomId) {
        return roomRepository.findById(roomId)
            .orElseThrow(() -> new NotFoundException("Chat not found"));
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private ChatRoomResponse toRoomResponse(ChatRoom room, UUID meId) {
        List<ChatMemberResponse> members = memberRepository
            .findByChatRoomIdAndStatus(room.getId(), MemberStatus.ACTIVE).stream()
            .map(member -> new ChatMemberResponse(toParticipant(member.getUser()), member.getRole()))
            .toList();
        ChatMessageResponse pinned = room.getPinnedMessage() == null ? null
            : toMessageResponse(room.getPinnedMessage(), meId);
        return new ChatRoomResponse(room.getId(), room.getChatType(), room.getName(),
            members, pinned, room.getCreatedAt());
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
        UUID roomId = message.getChatRoom() != null ? message.getChatRoom().getId() : null;
        return new ChatMessageResponse(
            message.getId(),
            roomId,
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

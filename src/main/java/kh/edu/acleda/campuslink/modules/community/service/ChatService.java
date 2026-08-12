package kh.edu.acleda.campuslink.modules.community.service;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.CreateGroupRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.SendMessageRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.ChatMessageResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.ChatRoomResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.ChatRoomSummaryResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

/**
 * Direct and group messaging (Phase 1, REST). Every operation requires the caller to be an
 * ACTIVE member of the room; a non-member sees "not found", never a hint the room exists.
 */
public interface ChatService {

    /** Open (or reuse) the 1:1 room with another user. Idempotent. */
    ChatRoomResponse openPrivateChat(UUID otherUserId);

    /** Create a group room; the caller becomes creator and first member. */
    ChatRoomResponse createGroup(CreateGroupRequest request);

    /** The caller's rooms, most-recently-active first, with last message and unread count. */
    PageResponse<ChatRoomSummaryResponse> myChats(Pageable pageable);

    /** A room and its active members. */
    ChatRoomResponse getRoom(UUID roomId);

    /** Leave a room. */
    void leaveRoom(UUID roomId);

    /** Post a message (TEXT / IMAGE / VOICE, optional reply). */
    ChatMessageResponse sendMessage(UUID roomId, SendMessageRequest request);

    /** Message history, newest first. */
    PageResponse<ChatMessageResponse> messages(UUID roomId, Pageable pageable);

    /** Soft-delete one of the caller's own messages. */
    void deleteMessage(UUID roomId, UUID messageId);

    /** Mark the room read up to its latest message. */
    void markRead(UUID roomId);

    /** Total unread messages across all the caller's rooms — for a badge. */
    long totalUnread();
}

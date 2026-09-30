package kh.edu.acleda.campuslink.modules.community.service;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.AddMembersRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.ChangeChatRoleRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.CreateGroupRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.RenameGroupRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.SendMessageRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.ChatMemberResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.ChatMessageResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.ChatRoomResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.ChatRoomSummaryResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

/** REST messaging for active room members. */
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

    /** Delete a conversation and clear its message history. */
    void deleteConversation(UUID roomId);

    /** Toggle mute state for caller. Returns true if now muted, false if unmuted. */
    boolean toggleMute(UUID roomId);

    /** Explicitly unmute the room for the caller. */
    void unmuteRoom(UUID roomId);

    /** Archive the room for the caller. */
    void archiveRoom(UUID roomId);

    /** Unarchive the room for the caller. */
    void unarchiveRoom(UUID roomId);

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

    // Group messages

    /** Rename a group. Admin/owner only. */
    ChatRoomResponse renameGroup(UUID roomId, RenameGroupRequest request);

    /** Add people to a group. Admin/owner only. */
    ChatRoomResponse addMembers(UUID roomId, AddMembersRequest request);

    /** Remove someone from a group. Admin/owner only; the owner cannot be removed. */
    void removeMember(UUID roomId, UUID userId);

    /** Promote/demote a member (ADMIN or MEMBER). Owner only. */
    ChatMemberResponse changeMemberRole(UUID roomId, UUID userId, ChangeChatRoleRequest request);

    /** Pin a message in the room. Admin/owner only. */
    ChatRoomResponse pinMessage(UUID roomId, UUID messageId);

    /** Clear the room's pinned message. Admin/owner only. */
    ChatRoomResponse unpinMessage(UUID roomId);
}

package kh.edu.acleda.campuslink.modules.community.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
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
import kh.edu.acleda.campuslink.modules.community.service.ChatService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/community/chats")
@Tag(name = "Community - Chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** Open (or reuse) the 1:1 room with another user. */
    @PostMapping("/private/{userId}")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> openPrivate(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success("Chat opened", chatService.openPrivateChat(userId)));
    }

    @PostMapping("/groups")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> createGroup(
        @Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Group created", chatService.createGroup(request)));
    }

    /** The caller's chat list, most-recently-active first. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ChatRoomSummaryResponse>>> myChats(
        @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Chats retrieved", chatService.myChats(pageable)));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> getRoom(@PathVariable UUID roomId) {
        return ResponseEntity.ok(ApiResponse.success("Chat retrieved", chatService.getRoom(roomId)));
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(@PathVariable UUID roomId) {
        chatService.deleteConversation(roomId);
        return ResponseEntity.ok(ApiResponse.success("Conversation deleted", null));
    }

    @DeleteMapping("/{roomId}/leave")
    public ResponseEntity<ApiResponse<Void>> leave(@PathVariable UUID roomId) {
        chatService.leaveRoom(roomId);
        return ResponseEntity.ok(ApiResponse.success("Left chat", null));
    }

    @PostMapping("/{roomId}/mute")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> mute(@PathVariable UUID roomId) {
        boolean muted = chatService.toggleMute(roomId);
        return ResponseEntity.ok(ApiResponse.success(muted ? "Chat muted" : "Chat unmuted", Map.of("isMuted", muted)));
    }

    @PostMapping("/{roomId}/unmute")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> unmute(@PathVariable UUID roomId) {
        chatService.unmuteRoom(roomId);
        return ResponseEntity.ok(ApiResponse.success("Chat unmuted", Map.of("isMuted", false)));
    }

    @PostMapping("/{roomId}/archive")
    public ResponseEntity<ApiResponse<Void>> archive(@PathVariable UUID roomId) {
        chatService.archiveRoom(roomId);
        return ResponseEntity.ok(ApiResponse.success("Chat archived", null));
    }

    @PostMapping("/{roomId}/unarchive")
    public ResponseEntity<ApiResponse<Void>> unarchive(@PathVariable UUID roomId) {
        chatService.unarchiveRoom(roomId);
        return ResponseEntity.ok(ApiResponse.success("Chat unarchived", null));
    }

    @PostMapping("/{roomId}/messages")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> send(@PathVariable UUID roomId,
                                                                 @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Message sent", chatService.sendMessage(roomId, request)));
    }

    /** Message history, newest first. */
    @GetMapping("/{roomId}/messages")
    public ResponseEntity<ApiResponse<PageResponse<ChatMessageResponse>>> messages(
        @PathVariable UUID roomId,
        @PageableDefault(size = 30, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Messages retrieved", chatService.messages(roomId, pageable)));
    }

    @DeleteMapping("/{roomId}/messages/{messageId}")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(@PathVariable UUID roomId,
                                                           @PathVariable UUID messageId) {
        chatService.deleteMessage(roomId, messageId);
        return ResponseEntity.ok(ApiResponse.success("Message deleted", null));
    }

    @PostMapping("/{roomId}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable UUID roomId) {
        chatService.markRead(roomId);
        return ResponseEntity.ok(ApiResponse.success("Marked read", null));
    }

    /** Total unread across all the caller's rooms — for the badge. */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount() {
        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved",
            Map.of("unreadCount", chatService.totalUnread())));
    }

    // Group management

    /** Rename a group. Admin/owner only. */
    @PutMapping("/{roomId}")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> rename(@PathVariable UUID roomId,
                                                                @Valid @RequestBody RenameGroupRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Group renamed", chatService.renameGroup(roomId, request)));
    }

    /** Add members to a group. Admin/owner only. */
    @PostMapping("/{roomId}/members")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> addMembers(@PathVariable UUID roomId,
                                                                    @Valid @RequestBody AddMembersRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Members added", chatService.addMembers(roomId, request)));
    }

    /** Remove a member from a group. Admin/owner only; owner cannot be removed. */
    @DeleteMapping("/{roomId}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(@PathVariable UUID roomId,
                                                          @PathVariable UUID userId) {
        chatService.removeMember(roomId, userId);
        return ResponseEntity.ok(ApiResponse.success("Member removed", null));
    }

    /** Promote/demote a member (ADMIN or MEMBER). Owner only. */
    @PutMapping("/{roomId}/members/{userId}/role")
    public ResponseEntity<ApiResponse<ChatMemberResponse>> changeRole(@PathVariable UUID roomId,
                                                                      @PathVariable UUID userId,
                                                                      @Valid @RequestBody ChangeChatRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Role updated",
            chatService.changeMemberRole(roomId, userId, request)));
    }

    /** Pin a message. Admin/owner only. */
    @PostMapping("/{roomId}/pin/{messageId}")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> pin(@PathVariable UUID roomId,
                                                             @PathVariable UUID messageId) {
        return ResponseEntity.ok(ApiResponse.success("Message pinned", chatService.pinMessage(roomId, messageId)));
    }

    /** Clear the pinned message. Admin/owner only. */
    @DeleteMapping("/{roomId}/pin")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> unpin(@PathVariable UUID roomId) {
        return ResponseEntity.ok(ApiResponse.success("Message unpinned", chatService.unpinMessage(roomId)));
    }
}

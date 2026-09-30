package kh.edu.acleda.campuslink.modules.community.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.ChatType;

/** One row in the chat list. */
public record ChatRoomSummaryResponse(
    UUID roomId,
    ChatType type,
    String title,
    String avatarUrl,
    ChatParticipant counterpart,
    ChatMessageResponse lastMessage,
    long unreadCount,
    Instant lastActivityAt,
    @JsonProperty("isMuted") boolean isMuted,
    @JsonProperty("isArchived") boolean isArchived
) {
    public ChatRoomSummaryResponse(
        UUID roomId,
        ChatType type,
        String title,
        ChatParticipant counterpart,
        ChatMessageResponse lastMessage,
        long unreadCount,
        Instant lastActivityAt,
        boolean isMuted,
        boolean isArchived
    ) {
        this(roomId, type, title, null, counterpart, lastMessage, unreadCount, lastActivityAt, isMuted, isArchived);
    }
}

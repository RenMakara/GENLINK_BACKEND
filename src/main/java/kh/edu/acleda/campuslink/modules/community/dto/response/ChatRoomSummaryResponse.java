package kh.edu.acleda.campuslink.modules.community.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.ChatType;

/**
 * One row in the chat list.
 *
 * @param title       the group's name; null for a private chat (the client titles it from
 *                    {@code counterpart})
 * @param counterpart the other person in a private chat; null for a group
 * @param lastMessage the most recent message, for the preview line; null for an empty room
 */
public record ChatRoomSummaryResponse(
    UUID roomId,
    ChatType type,
    String title,
    ChatParticipant counterpart,
    ChatMessageResponse lastMessage,
    long unreadCount,
    Instant lastActivityAt,
    @JsonProperty("isMuted") boolean isMuted,
    @JsonProperty("isArchived") boolean isArchived
) {
}

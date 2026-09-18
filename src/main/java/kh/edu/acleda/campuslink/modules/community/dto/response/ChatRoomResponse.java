package kh.edu.acleda.campuslink.modules.community.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.ChatType;

/**
 * A room with its active members (and their roles) and the pinned message, if any — returned
 * when a room is opened, created, fetched, or its group settings change.
 */
public record ChatRoomResponse(
    UUID roomId,
    ChatType type,
    String name,
    List<ChatMemberResponse> members,
    ChatMessageResponse pinnedMessage,
    Instant createdAt,
    @JsonProperty("isMuted") boolean isMuted,
    @JsonProperty("isArchived") boolean isArchived
) {
}

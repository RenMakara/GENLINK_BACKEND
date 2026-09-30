package kh.edu.acleda.campuslink.modules.community.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.ChatType;

/** A room with its active members (and their roles). */
public record ChatRoomResponse(
    UUID roomId,
    ChatType type,
    String name,
    String avatarUrl,
    List<ChatMemberResponse> members,
    ChatMessageResponse pinnedMessage,
    Instant createdAt,
    @JsonProperty("isMuted") boolean isMuted,
    @JsonProperty("isArchived") boolean isArchived
) {
    public ChatRoomResponse(
        UUID roomId,
        ChatType type,
        String name,
        List<ChatMemberResponse> members,
        ChatMessageResponse pinnedMessage,
        Instant createdAt,
        boolean isMuted,
        boolean isArchived
    ) {
        this(roomId, type, name, null, members, pinnedMessage, createdAt, isMuted, isArchived);
    }
}

package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.ChatType;

/** A room with its active members — returned when a room is opened, created, or fetched. */
public record ChatRoomResponse(
    UUID roomId,
    ChatType type,
    String name,
    List<ChatParticipant> members,
    Instant createdAt
) {
}

package kh.edu.acleda.campuslink.modules.community.event;

import kh.edu.acleda.campuslink.modules.community.dto.response.ChatMessageResponse;
import java.util.UUID;

/** Fired when a new chat message is persisted. */
public record ChatMessageSavedEvent(UUID roomId, UUID senderId, ChatMessageResponse message) {
}

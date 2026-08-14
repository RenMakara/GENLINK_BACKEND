package kh.edu.acleda.campuslink.modules.community.event;

import kh.edu.acleda.campuslink.modules.community.dto.response.ChatMessageResponse;
import java.util.UUID;

/**
 * Fired when a new chat message is persisted.
 *
 * @param roomId   The room the message belongs to.
 * @param senderId The sender's ID, to exclude them from the push since they receive the HTTP response.
 * @param message  The message payload to broadcast.
 */
public record ChatMessageSavedEvent(UUID roomId, UUID senderId, ChatMessageResponse message) {
}

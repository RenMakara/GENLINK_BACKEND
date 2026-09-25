package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.MessageType;

/**
 * Send a message into a room.
 *
 * <p>Field-level rules that depend on {@code type} are checked in the service: TEXT requires
 * {@code content}; IMAGE/VOICE require a {@code mediaUrl} (obtained through the media
 * presign/confirm flow). SYSTEM is server-generated and rejected from clients.
 *
 * @param replyToMessageId optional — set it to reply to another message in the same room.
 */
public record SendMessageRequest(
    @NotNull MessageType type,
    @Size(max = 4000) String content,
    @Size(max = 2048) String mediaUrl,
    UUID replyToMessageId,
    String sharedContent
) {
    public SendMessageRequest(
        MessageType type,
        String content,
        String mediaUrl,
        UUID replyToMessageId
    ) {
        this(type, content, mediaUrl, replyToMessageId, null);
    }
}

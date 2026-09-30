package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.MessageType;

/** Send a message into a room. */
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

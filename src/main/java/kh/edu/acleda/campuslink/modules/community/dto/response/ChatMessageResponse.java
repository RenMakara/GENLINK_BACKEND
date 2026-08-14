package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.MessageType;

/**
 * One message, renderable without a follow-up call.
 *
 * @param mine    resolved server-side so the client can align the bubble without knowing its own id
 * @param replyTo a shallow preview of the replied-to message (null when this is not a reply)
 */
public record ChatMessageResponse(
    UUID messageId,
    ChatParticipant sender,
    MessageType type,
    String content,
    String mediaUrl,
    UUID replyToMessageId,
    ReplyPreview replyTo,
    boolean mine,
    Instant createdAt
) {
    public ChatMessageResponse withMine(boolean newMine) {
        return new ChatMessageResponse(messageId, sender, type, content, mediaUrl, replyToMessageId, replyTo, newMine, createdAt);
    }

    /** Just enough of the replied-to message to render a quote line. */
    public record ReplyPreview(
        UUID messageId,
        String senderName,
        MessageType type,
        String snippet
    ) {}
}

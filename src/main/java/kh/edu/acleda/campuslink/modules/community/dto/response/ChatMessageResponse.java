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
    UUID roomId,
    ChatParticipant sender,
    MessageType type,
    String content,
    String mediaUrl,
    UUID replyToMessageId,
    ReplyPreview replyTo,
    String sharedContent,
    boolean mine,
    Instant createdAt
) {
    public ChatMessageResponse(
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
        this(messageId, null, sender, type, content, mediaUrl, replyToMessageId, replyTo, null, mine, createdAt);
    }

    public ChatMessageResponse(
        UUID messageId,
        UUID roomId,
        ChatParticipant sender,
        MessageType type,
        String content,
        String mediaUrl,
        UUID replyToMessageId,
        ReplyPreview replyTo,
        boolean mine,
        Instant createdAt
    ) {
        this(messageId, roomId, sender, type, content, mediaUrl, replyToMessageId, replyTo, null, mine, createdAt);
    }

    public ChatMessageResponse withMine(boolean newMine) {
        return new ChatMessageResponse(messageId, roomId, sender, type, content, mediaUrl, replyToMessageId, replyTo, sharedContent, newMine, createdAt);
    }

    public ChatMessageResponse withRoomId(UUID newRoomId) {
        return new ChatMessageResponse(messageId, newRoomId, sender, type, content, mediaUrl, replyToMessageId, replyTo, sharedContent, mine, createdAt);
    }

    /** Just enough of the replied-to message to render a quote line. */
    public record ReplyPreview(
        UUID messageId,
        String senderName,
        MessageType type,
        String snippet
    ) {}
}

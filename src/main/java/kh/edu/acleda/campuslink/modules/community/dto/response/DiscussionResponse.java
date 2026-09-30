package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.shared.enums.Language;

/** @param author null when the discussion is anonymous. The author id is still */
public record DiscussionResponse(
    UUID discussionId,
    PostResponse.AuthorSummary author,
    String title,
    String content,
    String category,
    boolean anonymous,
    Language language,
    Integer reactionCount,
    Integer commentCount,
    boolean reactedByMe,
    boolean mine,
    Instant createdAt,
    boolean repostedByMe,
    String repostedBy,
    String repostCaption,
    UUID reposterId,
    UUID originalDiscussionId
) {

    /** Backward-compatible constructor for normal discussion callers. */
    public DiscussionResponse(
        UUID discussionId,
        PostResponse.AuthorSummary author,
        String title,
        String content,
        String category,
        boolean anonymous,
        Language language,
        Integer reactionCount,
        Integer commentCount,
        boolean reactedByMe,
        boolean mine,
        Instant createdAt
    ) {
        this(discussionId, author, title, content, category, anonymous, language,
            reactionCount, commentCount, reactedByMe, mine, createdAt,
            false, null, null, null, null);
    }
}

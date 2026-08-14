package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * One author's active stories, grouped for the story bar.
 *
 * @param hasUnseen whether any story in the group is still unseen, so the client can ring
 *                  the avatar. Always false for the viewer's own group.
 */
public record StoryFeedItem(
    Author author,
    boolean hasUnseen,
    List<StoryResponse> stories
) {

    /** Public-safe author fields only. */
    public record Author(
        UUID userId,
        String fullName,
        String username,
        String avatarUrl
    ) {}
}

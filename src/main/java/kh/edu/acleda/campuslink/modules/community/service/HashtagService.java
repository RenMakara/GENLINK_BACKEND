package kh.edu.acleda.campuslink.modules.community.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.Post;

public interface HashtagService {

    /** Parses #tags out of the text, links them to the post, and bumps usage counts. */
    void syncForPost(Post post, String contentText);

    /**
     * Removes the post's tag links and decrements each tag's usage count. Called when a post is
     * deleted (author or moderator), so trending stops counting tags on content nobody can see.
     */
    void clearForPost(Post post);

    /** Tags per post id, batched for a feed page. */
    Map<UUID, List<String>> tagsForPosts(Set<UUID> postIds);

    List<String> trending(int limit);
}

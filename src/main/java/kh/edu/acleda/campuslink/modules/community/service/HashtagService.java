package kh.edu.acleda.campuslink.modules.community.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.Post;

public interface HashtagService {

    /** Parses #tags out of the text, links them to the post, and bumps usage counts. */
    void syncForPost(Post post, String contentText);

    /** Tags per post id, batched for a feed page. */
    Map<UUID, List<String>> tagsForPosts(Set<UUID> postIds);

    List<String> trending(int limit);
}

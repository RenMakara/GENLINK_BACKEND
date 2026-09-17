package kh.edu.acleda.campuslink.modules.search.dto.response;

import java.util.List;
import kh.edu.acleda.campuslink.modules.community.dto.response.DiscussionResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.EventResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;

public record SearchResultResponse(
    String query,
    String category,
    List<UserSearchResponse> people,
    List<PostResponse> posts,
    List<EventResponse> events,
    List<DiscussionResponse> discussions,
    long totalPeople,
    long totalPosts,
    long totalEvents,
    long totalDiscussions
) {}

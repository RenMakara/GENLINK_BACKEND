package kh.edu.acleda.campuslink.modules.search.service;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.response.DiscussionResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.EventResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.modules.search.dto.SearchCategory;
import kh.edu.acleda.campuslink.modules.search.dto.response.SearchResultResponse;
import kh.edu.acleda.campuslink.modules.search.dto.response.UserSearchResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface SearchService {

    SearchResultResponse search(String query, SearchCategory category, Pageable pageable, UUID viewerId);

    PageResponse<UserSearchResponse> searchPeople(String query, Pageable pageable);

    PageResponse<PostResponse> searchPosts(String query, UUID viewerId, Pageable pageable);

    PageResponse<EventResponse> searchEvents(String query, UUID viewerId, Pageable pageable);

    PageResponse<DiscussionResponse> searchDiscussions(String query, UUID viewerId, Pageable pageable);
}

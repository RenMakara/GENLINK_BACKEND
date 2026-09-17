package kh.edu.acleda.campuslink.modules.search.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.response.DiscussionResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.EventResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.modules.search.dto.SearchCategory;
import kh.edu.acleda.campuslink.modules.search.dto.response.SearchResultResponse;
import kh.edu.acleda.campuslink.modules.search.dto.response.UserSearchResponse;
import kh.edu.acleda.campuslink.modules.search.service.SearchService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search")
public class SearchController {

    private final SearchService searchService;
    private final CurrentUserProvider currentUserProvider;

    public SearchController(SearchService searchService, CurrentUserProvider currentUserProvider) {
        this.searchService = searchService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SearchResultResponse>> search(
        @RequestParam(name = "q", required = false) String q,
        @RequestParam(name = "query", required = false) String query,
        @RequestParam(name = "type", required = false) String type,
        @RequestParam(name = "category", required = false) String category,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        String effectiveQuery = (q != null && !q.isBlank()) ? q : query;
        String effectiveType = (type != null && !type.isBlank()) ? type : category;
        SearchCategory searchCategory = SearchCategory.fromString(effectiveType);

        UUID viewerId = getViewerIdQuietly();
        SearchResultResponse result = searchService.search(effectiveQuery, searchCategory, pageable, viewerId);
        return ResponseEntity.ok(ApiResponse.success("Search results retrieved", result));
    }

    @GetMapping("/people")
    public ResponseEntity<ApiResponse<PageResponse<UserSearchResponse>>> searchPeople(
        @RequestParam(name = "q", required = false) String q,
        @RequestParam(name = "query", required = false) String query,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        String effectiveQuery = (q != null && !q.isBlank()) ? q : query;
        return ResponseEntity.ok(ApiResponse.success("People retrieved", searchService.searchPeople(effectiveQuery, pageable)));
    }

    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> searchPosts(
        @RequestParam(name = "q", required = false) String q,
        @RequestParam(name = "query", required = false) String query,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        String effectiveQuery = (q != null && !q.isBlank()) ? q : query;
        UUID viewerId = getViewerIdQuietly();
        return ResponseEntity.ok(ApiResponse.success("Posts retrieved", searchService.searchPosts(effectiveQuery, viewerId, pageable)));
    }

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<PageResponse<EventResponse>>> searchEvents(
        @RequestParam(name = "q", required = false) String q,
        @RequestParam(name = "query", required = false) String query,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        String effectiveQuery = (q != null && !q.isBlank()) ? q : query;
        UUID viewerId = getViewerIdQuietly();
        return ResponseEntity.ok(ApiResponse.success("Events retrieved", searchService.searchEvents(effectiveQuery, viewerId, pageable)));
    }

    @GetMapping("/discussions")
    public ResponseEntity<ApiResponse<PageResponse<DiscussionResponse>>> searchDiscussions(
        @RequestParam(name = "q", required = false) String q,
        @RequestParam(name = "query", required = false) String query,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        String effectiveQuery = (q != null && !q.isBlank()) ? q : query;
        UUID viewerId = getViewerIdQuietly();
        return ResponseEntity.ok(ApiResponse.success("Discussions retrieved", searchService.searchDiscussions(effectiveQuery, viewerId, pageable)));
    }

    private UUID getViewerIdQuietly() {
        try {
            return currentUserProvider.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }
}

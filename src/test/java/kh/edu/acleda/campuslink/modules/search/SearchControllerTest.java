package kh.edu.acleda.campuslink.modules.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.search.controller.SearchController;
import kh.edu.acleda.campuslink.modules.search.dto.SearchCategory;
import kh.edu.acleda.campuslink.modules.search.dto.response.SearchResultResponse;
import kh.edu.acleda.campuslink.modules.search.dto.response.UserSearchResponse;
import kh.edu.acleda.campuslink.modules.search.service.SearchService;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

class SearchControllerTest {

    private SearchService searchService;
    private CurrentUserProvider currentUserProvider;
    private SearchController controller;
    private UUID viewerId;

    @BeforeEach
    void setUp() {
        searchService = mock(SearchService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        controller = new SearchController(searchService, currentUserProvider);
        viewerId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(viewerId);
    }

    @Test
    @DisplayName("Search delegates with correct parameters and defaults to ALL")
    void searchDelegatesToService() {
        Pageable pageable = PageRequest.of(0, 20);
        SearchResultResponse mockResult = new SearchResultResponse(
            "enat", "ALL", List.of(), List.of(), List.of(), List.of(), 0, 0, 0, 0
        );
        when(searchService.search(eq("enat"), eq(SearchCategory.ALL), eq(pageable), eq(viewerId)))
            .thenReturn(mockResult);

        ResponseEntity<ApiResponse<SearchResultResponse>> response = controller.search(
            "enat", null, null, null, pageable
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().query()).isEqualTo("enat");
        verify(searchService).search("enat", SearchCategory.ALL, pageable, viewerId);
    }

    @Test
    @DisplayName("Search with explicit type PEOPLE delegates to service")
    void searchWithTypePeople() {
        Pageable pageable = PageRequest.of(0, 20);
        SearchResultResponse mockResult = new SearchResultResponse(
            "enat", "PEOPLE", List.of(), List.of(), List.of(), List.of(), 0, 0, 0, 0
        );
        when(searchService.search(eq("enat"), eq(SearchCategory.PEOPLE), eq(pageable), eq(viewerId)))
            .thenReturn(mockResult);

        ResponseEntity<ApiResponse<SearchResultResponse>> response = controller.search(
            "enat", null, "PEOPLE", null, pageable
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().category()).isEqualTo("PEOPLE");
    }

    @Test
    @DisplayName("Search people endpoint delegates to searchPeople")
    void searchPeopleEndpoint() {
        Pageable pageable = PageRequest.of(0, 20);
        UserSearchResponse user = new UserSearchResponse(
            UUID.randomUUID(), "enat", "Sokny Enat", null, "AUB", "Dev", WorkStatus.STUDENT, 3, 500
        );
        when(searchService.searchPeople(eq("enat"), eq(pageable)))
            .thenReturn(new PageResponse<>(List.of(user), 0, 20, 1, 1, false));

        ResponseEntity<ApiResponse<PageResponse<UserSearchResponse>>> response = controller.searchPeople(
            "enat", null, pageable
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().content()).hasSize(1);
        assertThat(response.getBody().getData().content().get(0).username()).isEqualTo("enat");
    }
}

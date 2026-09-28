package kh.edu.acleda.campuslink.modules.community.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.StoryCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.StoryFeedItem;
import kh.edu.acleda.campuslink.modules.community.dto.response.StoryResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.StoryViewerResponse;
import kh.edu.acleda.campuslink.modules.community.enums.StoryMediaType;
import kh.edu.acleda.campuslink.modules.community.enums.StoryReplyPermission;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import kh.edu.acleda.campuslink.modules.community.service.StoryService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class StoryControllerTest {

    private StoryService storyService;
    private StoryController controller;

    @BeforeEach
    void setUp() {
        storyService = mock(StoryService.class);
        controller = new StoryController(storyService);
    }

    @Test
    @DisplayName("POST /stories delegates to service and returns 200 with created story")
    void createStoryDelegatesToService() {
        StoryCreateRequest request = new StoryCreateRequest(
            "https://cdn/photo.jpg",
            StoryMediaType.IMAGE,
            "Friends story",
            Visibility.FRIENDS,
            StoryReplyPermission.FRIENDS,
            false
        );
        StoryResponse mockResponse = new StoryResponse(
            UUID.randomUUID(),
            StoryMediaType.IMAGE,
            "https://cdn/photo.jpg",
            "Friends story",
            true,
            Instant.now(),
            Instant.now().plusSeconds(86400),
            Visibility.FRIENDS,
            StoryReplyPermission.FRIENDS,
            false
        );
        when(storyService.createStory(request)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<StoryResponse>> result = controller.create(request);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getData().visibility()).isEqualTo(Visibility.FRIENDS);
        assertThat(result.getBody().getData().replyPermission()).isEqualTo(StoryReplyPermission.FRIENDS);
        assertThat(result.getBody().getData().allowReactions()).isFalse();
        verify(storyService).createStory(request);
    }

    @Test
    @DisplayName("GET /stories delegates to service for feed")
    void feedDelegatesToService() {
        when(storyService.getStoriesFeed()).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<StoryFeedItem>>> result = controller.feed();

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        verify(storyService).getStoriesFeed();
    }

    @Test
    @DisplayName("GET /stories/{id} delegates to getStoryById")
    void getStoryByIdDelegatesToService() {
        UUID storyId = UUID.randomUUID();
        StoryResponse mockResponse = new StoryResponse(
            storyId,
            StoryMediaType.IMAGE,
            "https://cdn/photo.jpg",
            "Direct story",
            false,
            Instant.now(),
            Instant.now().plusSeconds(86400),
            Visibility.PUBLIC,
            StoryReplyPermission.EVERYONE,
            true
        );
        when(storyService.getStoryById(storyId)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<StoryResponse>> result = controller.getStory(storyId);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getData().storyId()).isEqualTo(storyId);
        verify(storyService).getStoryById(storyId);
    }

    @Test
    @DisplayName("POST /stories/{id}/view delegates to viewStory")
    void viewDelegatesToService() {
        UUID storyId = UUID.randomUUID();

        ResponseEntity<ApiResponse<Void>> result = controller.view(storyId);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        verify(storyService).viewStory(storyId);
    }

    @Test
    @DisplayName("GET /stories/{id}/viewers delegates to storyViewers")
    void viewersDelegatesToService() {
        UUID storyId = UUID.randomUUID();
        when(storyService.storyViewers(storyId)).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<StoryViewerResponse>>> result = controller.viewers(storyId);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        verify(storyService).storyViewers(storyId);
    }

    @Test
    @DisplayName("POST /stories/{id}/like delegates to the idempotent like operation")
    void likeDelegatesToService() {
        UUID storyId = UUID.randomUUID();

        ResponseEntity<ApiResponse<Void>> result = controller.like(storyId);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        verify(storyService).likeStory(storyId);
    }

    @Test
    @DisplayName("DELETE /stories/{id}/like delegates to the idempotent unlike operation")
    void unlikeDelegatesToService() {
        UUID storyId = UUID.randomUUID();

        ResponseEntity<ApiResponse<Void>> result = controller.unlike(storyId);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        verify(storyService).unlikeStory(storyId);
    }

    @Test
    @DisplayName("DELETE /stories/{id} delegates to deleteStory")
    void deleteDelegatesToService() {
        UUID storyId = UUID.randomUUID();

        ResponseEntity<ApiResponse<Void>> result = controller.delete(storyId);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        verify(storyService).deleteStory(storyId);
    }
}

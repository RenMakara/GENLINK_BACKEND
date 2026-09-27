package kh.edu.acleda.campuslink.modules.community.service;

import kh.edu.acleda.campuslink.modules.community.dto.request.StoryCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.StoryFeedItem;
import kh.edu.acleda.campuslink.modules.community.dto.response.StoryResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.StoryViewerResponse;

import java.util.List;
import java.util.UUID;

public interface StoryService {

    /** Posts a story that expires 24h from now. */
    StoryResponse createStory(StoryCreateRequest request);

    /**
     * The story bar: active stories from people the viewer follows plus their own,
     * grouped by author. The viewer's own group comes first, then unseen groups.
     */
    List<StoryFeedItem> getStoriesFeed();

    /** Marks a story seen for the caller. Idempotent; viewing your own story records nothing. */
    void viewStory(UUID storyId);

    /** The owner's viewer list for a story. Only the owner may read it; others get not-found. */
    List<StoryViewerResponse> storyViewers(UUID storyId);

    /** Soft-deletes the caller's own story. Another user's story reads as not-found. */
    void deleteStory(UUID storyId);

    /** Retrieves a single active story if the caller is authorized to view it. */
    StoryResponse getStoryById(UUID storyId);

    /** Toggles the caller's reaction on an active story and notifies its author on add. */
    void likeStory(UUID storyId);
}

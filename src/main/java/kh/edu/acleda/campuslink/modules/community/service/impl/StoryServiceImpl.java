package kh.edu.acleda.campuslink.modules.community.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kh.edu.acleda.campuslink.modules.community.dto.request.StoryCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.StoryFeedItem;
import kh.edu.acleda.campuslink.modules.community.dto.response.StoryResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.StoryViewerResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Story;
import kh.edu.acleda.campuslink.modules.community.entity.StoryView;
import kh.edu.acleda.campuslink.modules.community.repository.FollowRepository;
import kh.edu.acleda.campuslink.modules.community.repository.StoryRepository;
import kh.edu.acleda.campuslink.modules.community.repository.StoryViewRepository;
import kh.edu.acleda.campuslink.modules.community.service.StoryService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class StoryServiceImpl implements StoryService {

    /** Stories live 24h from creation, then drop out of the feed. */
    private static final Duration STORY_TTL = Duration.ofHours(24);

    private final StoryRepository storyRepository;
    private final StoryViewRepository storyViewRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public StoryServiceImpl(StoryRepository storyRepository,
                            StoryViewRepository storyViewRepository,
                            FollowRepository followRepository,
                            UserRepository userRepository,
                            CurrentUserProvider currentUserProvider) {
        this.storyRepository = storyRepository;
        this.storyViewRepository = storyViewRepository;
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public StoryResponse createStory(StoryCreateRequest request) {
        User me = loadCurrentUser();
        Instant now = Instant.now();
        Story story = storyRepository.save(Story.builder()
            .user(me)
            .mediaUrl(request.mediaUrl())
            .mediaType(request.mediaType())
            .caption(request.caption())
            .expiresAt(now.plus(STORY_TTL))
            .build());
        log.info("Story created: id={} by user={} expires={}", story.getId(), me.getId(), story.getExpiresAt());
        // The author's own story always reads as seen.
        return toStoryResponse(story, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoryFeedItem> getStoriesFeed() {
        UUID meId = currentUserProvider.getCurrentUserId();
        Set<UUID> authorIds = new HashSet<>(followRepository.findFollowingIds(meId));
        authorIds.add(meId);

        List<Story> stories = storyRepository.findActiveByAuthors(authorIds, Instant.now());
        if (stories.isEmpty()) {
            return List.of();
        }
        Set<UUID> storyIds = stories.stream().map(Story::getId).collect(Collectors.toSet());
        Set<UUID> seen = new HashSet<>(storyViewRepository.findViewedStoryIds(meId, storyIds));

        // Group by author, preserving the oldest-first order the query returned.
        Map<UUID, List<Story>> byAuthor = new LinkedHashMap<>();
        for (Story story : stories) {
            byAuthor.computeIfAbsent(story.getUser().getId(), key -> new ArrayList<>()).add(story);
        }

        StoryFeedItem ownItem = null;
        List<StoryFeedItem> others = new ArrayList<>();
        for (List<Story> authorStories : byAuthor.values()) {
            User author = authorStories.get(0).getUser();
            boolean mine = author.getId().equals(meId);

            boolean hasUnseen = false;
            List<StoryResponse> responses = new ArrayList<>(authorStories.size());
            for (Story story : authorStories) {
                boolean storySeen = mine || seen.contains(story.getId());
                if (!storySeen) {
                    hasUnseen = true;
                }
                responses.add(toStoryResponse(story, storySeen));
            }
            StoryFeedItem item = new StoryFeedItem(
                new StoryFeedItem.Author(author.getId(), author.getFullName(),
                    author.getUsername(), author.getAvatarUrl()),
                hasUnseen, responses);
            if (mine) {
                ownItem = item;
            } else {
                others.add(item);
            }
        }
        // Own group first, then groups with unseen stories ahead of fully-seen ones.
        others.sort((a, b) -> Boolean.compare(b.hasUnseen(), a.hasUnseen()));

        List<StoryFeedItem> feed = new ArrayList<>();
        if (ownItem != null) {
            feed.add(ownItem);
        }
        feed.addAll(others);
        return feed;
    }

    @Override
    @Transactional
    public void viewStory(UUID storyId) {
        Story story = loadActiveStory(storyId);
        UUID meId = currentUserProvider.getCurrentUserId();
        // Viewing your own story does not create a view row, so the viewer list stays honest.
        if (story.getUser().getId().equals(meId)) {
            return;
        }
        // The unique (story, viewer) backs this; skipping the insert keeps it a no-op on repeat views.
        if (storyViewRepository.existsByStoryIdAndViewerId(storyId, meId)) {
            return;
        }
        storyViewRepository.save(StoryView.builder()
            .story(story)
            .viewer(loadCurrentUser())
            .viewedAt(Instant.now())
            .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoryViewerResponse> storyViewers(UUID storyId) {
        Story story = storyRepository.findByIdAndDeletedAtIsNull(storyId)
            .orElseThrow(() -> new NotFoundException("Story not found"));
        // Only the owner may see who viewed; anyone else gets not-found, never a leak.
        if (!story.getUser().getId().equals(currentUserProvider.getCurrentUserId())) {
            throw new NotFoundException("Story not found");
        }
        return storyViewRepository.findViewersOfStory(storyId).stream()
            .map(view -> new StoryViewerResponse(
                view.getViewer().getId(),
                view.getViewer().getFullName(),
                view.getViewer().getUsername(),
                view.getViewer().getAvatarUrl(),
                view.getViewedAt()))
            .toList();
    }

    @Override
    @Transactional
    public void deleteStory(UUID storyId) {
        Story story = storyRepository.findByIdAndDeletedAtIsNull(storyId)
            .orElseThrow(() -> new NotFoundException("Story not found"));
        // Owner-scoped: another user's story reads as not found, never as forbidden.
        if (!story.getUser().getId().equals(currentUserProvider.getCurrentUserId())) {
            throw new NotFoundException("Story not found");
        }
        story.setDeletedAt(Instant.now());
        storyRepository.save(story);
    }

    /** A story that is deleted or expired is gone for everyone, so both read as not-found. */
    private Story loadActiveStory(UUID storyId) {
        Story story = storyRepository.findByIdAndDeletedAtIsNull(storyId)
            .orElseThrow(() -> new NotFoundException("Story not found"));
        if (story.getExpiresAt().isBefore(Instant.now())) {
            throw new NotFoundException("Story not found");
        }
        return story;
    }

    private StoryResponse toStoryResponse(Story story, boolean seen) {
        return new StoryResponse(
            story.getId(),
            story.getMediaType(),
            story.getMediaUrl(),
            story.getCaption(),
            seen,
            story.getCreatedAt(),
            story.getExpiresAt());
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }
}

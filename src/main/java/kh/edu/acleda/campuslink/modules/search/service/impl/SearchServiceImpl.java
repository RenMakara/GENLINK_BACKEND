package kh.edu.acleda.campuslink.modules.search.service.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kh.edu.acleda.campuslink.modules.community.dto.response.DiscussionResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.EventResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.PollResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Event;
import kh.edu.acleda.campuslink.modules.community.entity.ForumDiscussion;
import kh.edu.acleda.campuslink.modules.community.entity.Poll;
import kh.edu.acleda.campuslink.modules.community.entity.PollOption;
import kh.edu.acleda.campuslink.modules.community.entity.Post;
import kh.edu.acleda.campuslink.modules.community.enums.RsvpStatus;
import kh.edu.acleda.campuslink.modules.community.repository.EventRepository;
import kh.edu.acleda.campuslink.modules.community.repository.EventRsvpRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ForumDiscussionRepository;
import kh.edu.acleda.campuslink.modules.community.repository.ForumReactionRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PollOptionRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PollRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PollVoteRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PostReactionRepository;
import kh.edu.acleda.campuslink.modules.community.repository.PostRepository;
import kh.edu.acleda.campuslink.modules.community.service.HashtagService;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;
import kh.edu.acleda.campuslink.modules.profile.repository.SavedItemRepository;
import kh.edu.acleda.campuslink.modules.search.dto.SearchCategory;
import kh.edu.acleda.campuslink.modules.search.dto.response.SearchResultResponse;
import kh.edu.acleda.campuslink.modules.search.dto.response.UserSearchResponse;
import kh.edu.acleda.campuslink.modules.search.service.SearchService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class SearchServiceImpl implements SearchService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final EventRepository eventRepository;
    private final ForumDiscussionRepository forumDiscussionRepository;
    private final PostReactionRepository postReactionRepository;
    private final SavedItemRepository savedItemRepository;
    private final HashtagService hashtagService;
    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final EventRsvpRepository eventRsvpRepository;
    private final ForumReactionRepository forumReactionRepository;

    public SearchServiceImpl(UserRepository userRepository,
                             PostRepository postRepository,
                             EventRepository eventRepository,
                             ForumDiscussionRepository forumDiscussionRepository,
                             PostReactionRepository postReactionRepository,
                             SavedItemRepository savedItemRepository,
                             HashtagService hashtagService,
                             PollRepository pollRepository,
                             PollOptionRepository pollOptionRepository,
                             PollVoteRepository pollVoteRepository,
                             EventRsvpRepository eventRsvpRepository,
                             ForumReactionRepository forumReactionRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.eventRepository = eventRepository;
        this.forumDiscussionRepository = forumDiscussionRepository;
        this.postReactionRepository = postReactionRepository;
        this.savedItemRepository = savedItemRepository;
        this.hashtagService = hashtagService;
        this.pollRepository = pollRepository;
        this.pollOptionRepository = pollOptionRepository;
        this.pollVoteRepository = pollVoteRepository;
        this.eventRsvpRepository = eventRsvpRepository;
        this.forumReactionRepository = forumReactionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public SearchResultResponse search(String query, SearchCategory category, Pageable pageable, UUID viewerId) {
        if (query == null || query.isBlank()) {
            return new SearchResultResponse(
                "",
                category != null ? category.name() : SearchCategory.ALL.name(),
                List.of(), List.of(), List.of(), List.of(),
                0, 0, 0, 0
            );
        }

        String cleanedQuery = query.trim();
        String userQuery = extractUserQuery(cleanedQuery);
        SearchCategory cat = category != null ? category : SearchCategory.ALL;

        switch (cat) {
            case PEOPLE -> {
                Page<User> page = userRepository.searchUsers(userQuery, pageable);
                List<UserSearchResponse> people = page.getContent().stream()
                    .map(UserSearchResponse::from)
                    .toList();
                return new SearchResultResponse(
                    cleanedQuery, SearchCategory.PEOPLE.name(),
                    people, List.of(), List.of(), List.of(),
                    page.getTotalElements(), 0, 0, 0
                );
            }
            case POSTS -> {
                Page<Post> page = postRepository.searchPublicPosts(cleanedQuery, pageable);
                List<PostResponse> posts = hydratePosts(page.getContent(), viewerId);
                return new SearchResultResponse(
                    cleanedQuery, SearchCategory.POSTS.name(),
                    List.of(), posts, List.of(), List.of(),
                    0, page.getTotalElements(), 0, 0
                );
            }
            case EVENTS -> {
                Page<Event> page = eventRepository.searchEvents(cleanedQuery, pageable);
                List<EventResponse> events = hydrateEvents(page.getContent(), viewerId);
                return new SearchResultResponse(
                    cleanedQuery, SearchCategory.EVENTS.name(),
                    List.of(), List.of(), events, List.of(),
                    0, 0, page.getTotalElements(), 0
                );
            }
            case FORUM -> {
                Page<ForumDiscussion> page = forumDiscussionRepository.searchDiscussions(cleanedQuery, pageable);
                List<DiscussionResponse> discussions = hydrateDiscussions(page.getContent(), viewerId);
                return new SearchResultResponse(
                    cleanedQuery, SearchCategory.FORUM.name(),
                    List.of(), List.of(), List.of(), discussions,
                    0, 0, 0, page.getTotalElements()
                );
            }
            case MEDIA -> {
                // Media search is not supported by current backend tables.
                return new SearchResultResponse(
                    cleanedQuery, SearchCategory.MEDIA.name(),
                    List.of(), List.of(), List.of(), List.of(),
                    0, 0, 0, 0
                );
            }
            default -> {
                // ALL: retrieve a balanced preview of top matches for each category
                int previewLimit = Math.min(pageable.getPageSize(), 5);
                Pageable previewPageable = PageRequest.of(0, previewLimit);

                Page<User> usersPage = userRepository.searchUsers(userQuery, previewPageable);
                Page<Post> postsPage = postRepository.searchPublicPosts(cleanedQuery, previewPageable);
                Page<Event> eventsPage = eventRepository.searchEvents(cleanedQuery, previewPageable);
                Page<ForumDiscussion> discussionsPage = forumDiscussionRepository.searchDiscussions(cleanedQuery, previewPageable);

                List<UserSearchResponse> people = usersPage.getContent().stream()
                    .map(UserSearchResponse::from)
                    .toList();
                List<PostResponse> posts = hydratePosts(postsPage.getContent(), viewerId);
                List<EventResponse> events = hydrateEvents(eventsPage.getContent(), viewerId);
                List<DiscussionResponse> discussions = hydrateDiscussions(discussionsPage.getContent(), viewerId);

                return new SearchResultResponse(
                    cleanedQuery,
                    SearchCategory.ALL.name(),
                    people,
                    posts,
                    events,
                    discussions,
                    usersPage.getTotalElements(),
                    postsPage.getTotalElements(),
                    eventsPage.getTotalElements(),
                    discussionsPage.getTotalElements()
                );
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserSearchResponse> searchPeople(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, false);
        }
        Page<User> page = userRepository.searchUsers(extractUserQuery(query), pageable);
        return PageResponse.from(page, UserSearchResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PostResponse> searchPosts(String query, UUID viewerId, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, false);
        }
        Page<Post> page = postRepository.searchPublicPosts(query.trim(), pageable);
        List<PostResponse> content = hydratePosts(page.getContent(), viewerId);
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> searchEvents(String query, UUID viewerId, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, false);
        }
        Page<Event> page = eventRepository.searchEvents(query.trim(), pageable);
        List<EventResponse> content = hydrateEvents(page.getContent(), viewerId);
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DiscussionResponse> searchDiscussions(String query, UUID viewerId, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return new PageResponse<>(List.of(), 0, pageable.getPageSize(), 0, 0, false);
        }
        Page<ForumDiscussion> page = forumDiscussionRepository.searchDiscussions(query.trim(), pageable);
        List<DiscussionResponse> content = hydrateDiscussions(page.getContent(), viewerId);
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    // ── Helper hydration methods ─────────────────────────────────────────────

    private List<PostResponse> hydratePosts(List<Post> posts, UUID viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Set<UUID> postIds = posts.stream().map(Post::getId).collect(Collectors.toSet());
        Set<UUID> liked = viewerId != null
            ? new HashSet<>(postReactionRepository.findLikedPostIds(viewerId, postIds))
            : Collections.emptySet();
        Set<UUID> saved = viewerId != null
            ? new HashSet<>(savedItemRepository.findSavedItemIds(viewerId, SavedItemType.POST, postIds))
            : Collections.emptySet();
        Map<UUID, List<String>> tags = hashtagService.tagsForPosts(postIds);
        Map<UUID, PollResponse> polls = pollsForPosts(postIds, viewerId);

        return posts.stream().map(post -> new PostResponse(
            post.getId(),
            summary(post.getUser()),
            post.getContentText(),
            post.getImageUrl(),
            post.getPostType(),
            post.getVisibility(),
            post.getLanguage(),
            post.getLikeCount(),
            post.getCommentCount(),
            liked.contains(post.getId()),
            saved.contains(post.getId()),
            tags.getOrDefault(post.getId(), List.of()),
            polls.get(post.getId()),
            post.getCreatedAt()
        )).toList();
    }

    private List<EventResponse> hydrateEvents(List<Event> events, UUID viewerId) {
        if (events.isEmpty()) {
            return List.of();
        }
        Set<UUID> eventIds = events.stream().map(Event::getId).collect(Collectors.toSet());
        Map<UUID, RsvpStatus> myRsvp = new HashMap<>();
        if (viewerId != null) {
            eventRsvpRepository.findByUserIdAndEventIdIn(viewerId, eventIds)
                .forEach(rsvp -> myRsvp.put(rsvp.getEvent().getId(), rsvp.getStatus()));
        }

        return events.stream().map(event -> new EventResponse(
            event.getId(),
            summary(event.getOrganizer()),
            event.getTitle(),
            event.getDescription(),
            event.getCategory(),
            event.getCoverImageUrl(),
            event.getDateTime(),
            event.getEndDateTime(),
            event.getMode(),
            event.getMeetingUrl(),
            event.getLocationName(),
            event.getLatitude(),
            event.getLongitude(),
            event.getAttendeeLimit(),
            event.getAttendeeCount(),
            event.getStatus(),
            event.getLanguage(),
            Boolean.TRUE.equals(event.getIsPaid()),
            event.getPrice(),
            event.getCurrency(),
            myRsvp.get(event.getId()),
            event.getAttendeeLimit() != null && event.getAttendeeCount() >= event.getAttendeeLimit(),
            event.getCreatedAt()
        )).toList();
    }

    private List<DiscussionResponse> hydrateDiscussions(List<ForumDiscussion> discussions, UUID viewerId) {
        if (discussions.isEmpty()) {
            return List.of();
        }
        Set<UUID> discussionIds = discussions.stream().map(ForumDiscussion::getId).collect(Collectors.toSet());
        Set<UUID> reacted = viewerId != null
            ? new HashSet<>(forumReactionRepository.findReactedDiscussionIds(viewerId, discussionIds))
            : Collections.emptySet();

        return discussions.stream().map(d -> {
            boolean anonymous = Boolean.TRUE.equals(d.getIsAnonymous());
            boolean mine = viewerId != null && d.getUser() != null && viewerId.equals(d.getUser().getId());
            return new DiscussionResponse(
                d.getId(),
                anonymous ? null : summary(d.getUser()),
                d.getTitle(),
                d.getContent(),
                d.getCategory(),
                anonymous,
                d.getLanguage(),
                d.getReactionCount(),
                d.getCommentCount(),
                reacted.contains(d.getId()),
                mine,
                d.getCreatedAt()
            );
        }).toList();
    }

    private Map<UUID, PollResponse> pollsForPosts(Set<UUID> postIds, UUID viewerId) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        List<Poll> polls = pollRepository.findByPostIdIn(postIds);
        if (polls.isEmpty()) {
            return Map.of();
        }
        Set<UUID> pollIds = polls.stream().map(Poll::getId).collect(Collectors.toSet());
        Map<UUID, List<PollOption>> optionsByPoll = pollOptionRepository
            .findByPollIdInOrderByCreatedAtAsc(pollIds).stream()
            .collect(Collectors.groupingBy(option -> option.getPoll().getId()));
        Map<UUID, UUID> myOptionByPoll = viewerId != null
            ? pollVoteRepository.findByPollIdInAndUserId(pollIds, viewerId).stream()
                .collect(Collectors.toMap(vote -> vote.getPoll().getId(), vote -> vote.getOption().getId()))
            : Map.of();

        Map<UUID, PollResponse> byPost = new HashMap<>();
        for (Poll poll : polls) {
            List<PollOption> options = optionsByPoll.getOrDefault(poll.getId(), List.of());
            int totalVotes = options.stream().mapToInt(opt -> opt.getVoteCount() == null ? 0 : opt.getVoteCount()).sum();
            UUID myOption = myOptionByPoll.get(poll.getId());
            List<PollResponse.Option> optionDtos = options.stream()
                .map(option -> new PollResponse.Option(option.getId(), option.getOptionText(), option.getVoteCount() == null ? 0 : option.getVoteCount()))
                .toList();
            byPost.put(poll.getPost().getId(), new PollResponse(
                poll.getId(), poll.getQuestion(), totalVotes, myOption != null, myOption, optionDtos));
        }
        return byPost;
    }

    private PostResponse.AuthorSummary summary(User user) {
        return user == null ? null : new PostResponse.AuthorSummary(
            user.getId(), user.getFullName(), user.getUsername(), user.getAvatarUrl());
    }

    private String extractUserQuery(String query) {
        if (query == null) {
            return "";
        }
        String trimmed = query.trim();
        if (trimmed.startsWith("@") && trimmed.length() > 1) {
            return trimmed.substring(1).trim();
        }
        return trimmed;
    }
}

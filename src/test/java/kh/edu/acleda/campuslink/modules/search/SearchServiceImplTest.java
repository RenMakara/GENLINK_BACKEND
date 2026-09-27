package kh.edu.acleda.campuslink.modules.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.Event;
import kh.edu.acleda.campuslink.modules.community.entity.ForumDiscussion;
import kh.edu.acleda.campuslink.modules.community.entity.Post;
import kh.edu.acleda.campuslink.modules.community.enums.EventMode;
import kh.edu.acleda.campuslink.modules.community.enums.EventStatus;
import kh.edu.acleda.campuslink.modules.community.enums.PostType;
import kh.edu.acleda.campuslink.modules.community.enums.RsvpStatus;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import kh.edu.acleda.campuslink.modules.community.entity.EventRsvp;
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
import kh.edu.acleda.campuslink.modules.media.MediaService;
import kh.edu.acleda.campuslink.modules.media.entity.MediaAttachmentRole;
import kh.edu.acleda.campuslink.modules.media.entity.MediaResourceType;
import kh.edu.acleda.campuslink.modules.profile.repository.SavedItemRepository;
import kh.edu.acleda.campuslink.modules.search.dto.SearchCategory;
import kh.edu.acleda.campuslink.modules.search.dto.response.SearchResultResponse;
import kh.edu.acleda.campuslink.modules.search.dto.response.UserSearchResponse;
import kh.edu.acleda.campuslink.modules.search.service.impl.SearchServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class SearchServiceImplTest {

    private UserRepository userRepository;
    private PostRepository postRepository;
    private EventRepository eventRepository;
    private ForumDiscussionRepository forumDiscussionRepository;
    private PostReactionRepository postReactionRepository;
    private SavedItemRepository savedItemRepository;
    private HashtagService hashtagService;
    private PollRepository pollRepository;
    private PollOptionRepository pollOptionRepository;
    private PollVoteRepository pollVoteRepository;
    private EventRsvpRepository eventRsvpRepository;
    private ForumReactionRepository forumReactionRepository;
    private MediaService mediaService;

    private SearchServiceImpl searchService;

    private User userEnat;
    private User userTester;
    private UUID viewerId;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        postRepository = mock(PostRepository.class);
        eventRepository = mock(EventRepository.class);
        forumDiscussionRepository = mock(ForumDiscussionRepository.class);
        postReactionRepository = mock(PostReactionRepository.class);
        savedItemRepository = mock(SavedItemRepository.class);
        hashtagService = mock(HashtagService.class);
        pollRepository = mock(PollRepository.class);
        pollOptionRepository = mock(PollOptionRepository.class);
        pollVoteRepository = mock(PollVoteRepository.class);
        eventRsvpRepository = mock(EventRsvpRepository.class);
        forumReactionRepository = mock(ForumReactionRepository.class);
        mediaService = mock(MediaService.class);

        searchService = new SearchServiceImpl(
            userRepository,
            postRepository,
            eventRepository,
            forumDiscussionRepository,
            postReactionRepository,
            savedItemRepository,
            hashtagService,
            pollRepository,
            pollOptionRepository,
            pollVoteRepository,
            eventRsvpRepository,
            forumReactionRepository,
            mediaService
        );
        when(mediaService.resolveAttachedMedia(any(), any())).thenReturn(List.of());

        viewerId = UUID.randomUUID();

        userEnat = new User();
        ReflectionTestUtils.setField(userEnat, "id", UUID.randomUUID());
        userEnat.setUsername("enat");
        userEnat.setFullName("Sokny Enat");
        userEnat.setEmail("enat@campuslink.edu.kh");
        userEnat.setUniversityName("ACLEDA University of Business");
        userEnat.setBio("Flutter & Spring developer");
        userEnat.setAccountStatus(AccountStatus.ACTIVE);
        userEnat.setWorkStatus(WorkStatus.STUDENT);
        userEnat.setLevel(5);
        userEnat.setTotalXp(1200);

        userTester = new User();
        ReflectionTestUtils.setField(userTester, "id", UUID.randomUUID());
        userTester.setUsername("tester1");
        userTester.setFullName("Tester One");
        userTester.setEmail("tester1@campuslink.edu.kh");
        userTester.setAccountStatus(AccountStatus.ACTIVE);
        userTester.setWorkStatus(WorkStatus.STUDENT);
        userTester.setLevel(1);
        userTester.setTotalXp(50);
    }

    @Test
    @DisplayName("Empty or blank query returns empty search results without DB queries")
    void emptyQueryReturnsEmpty() {
        SearchResultResponse result = searchService.search("", SearchCategory.ALL, PageRequest.of(0, 20), viewerId);
        assertThat(result.people()).isEmpty();
        assertThat(result.posts()).isEmpty();
        assertThat(result.events()).isEmpty();
        assertThat(result.discussions()).isEmpty();
        assertThat(result.totalPeople()).isEqualTo(0);
    }

    @Test
    @DisplayName("Search people finds exact user 'enat'")
    void searchPeopleFindsEnat() {
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.searchUsers(eq("enat"), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(userEnat), pageable, 1));

        PageResponse<UserSearchResponse> response = searchService.searchPeople("enat", pageable);

        assertThat(response.content()).hasSize(1);
        UserSearchResponse found = response.content().get(0);
        assertThat(found.username()).isEqualTo("enat");
        assertThat(found.fullName()).isEqualTo("Sokny Enat");
        assertThat(found.userId()).isEqualTo(userEnat.getId());
    }

    @Test
    @DisplayName("Search people finds partial username 'nat' with multiple users")
    void searchPeoplePartialUsername() {
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.searchUsers(eq("nat"), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(userEnat), pageable, 1));

        PageResponse<UserSearchResponse> response = searchService.searchPeople("nat", pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).username()).isEqualTo("enat");
    }

    @Test
    @DisplayName("Search people with nonexistent keyword returns empty list")
    void searchPeopleNonexistent() {
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.searchUsers(eq("nonexistentuser123"), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<UserSearchResponse> response = searchService.searchPeople("nonexistentuser123", pageable);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("Unified search with ALL returns results from users, posts, events, and discussions")
    void unifiedSearchAllCategories() {
        String q = "flutter";
        Pageable pageable = PageRequest.of(0, 10);

        when(userRepository.searchUsers(eq(q), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(userEnat), pageable, 1));

        Post post = Post.builder()
            .contentText("Learning Flutter at ACLEDA")
            .visibility(Visibility.PUBLIC)
            .postType(PostType.TEXT)
            .likeCount(5)
            .commentCount(2)
            .user(userEnat)
            .build();
        ReflectionTestUtils.setField(post, "id", UUID.randomUUID());
        when(postRepository.searchPublicPosts(eq(q), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(post), pageable, 1));

        Event event = Event.builder()
            .title("Flutter Workshop 2026")
            .description("Learn Flutter hands-on")
            .status(EventStatus.PUBLISHED)
            .mode(EventMode.IN_PERSON)
            .locationName("Phnom Penh")
            .dateTime(LocalDateTime.now().plusDays(3))
            .endDateTime(LocalDateTime.now().plusDays(3).plusHours(2))
            .attendeeCount(10)
            .organizer(userEnat)
            .build();
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        when(eventRepository.searchEvents(eq(q), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(event), pageable, 1));

        ForumDiscussion discussion = ForumDiscussion.builder()
            .title("Flutter vs React Native in 2026")
            .content("What is your preference?")
            .category("TECH")
            .reactionCount(8)
            .commentCount(4)
            .user(userEnat)
            .build();
        ReflectionTestUtils.setField(discussion, "id", UUID.randomUUID());
        when(forumDiscussionRepository.searchDiscussions(eq(q), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(discussion), pageable, 1));

        SearchResultResponse result = searchService.search(q, SearchCategory.ALL, pageable, viewerId);

        assertThat(result.people()).hasSize(1);
        assertThat(result.people().get(0).username()).isEqualTo("enat");
        assertThat(result.posts()).hasSize(1);
        assertThat(result.posts().get(0).contentText()).contains("Flutter");
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).title()).isEqualTo("Flutter Workshop 2026");
        assertThat(result.discussions()).hasSize(1);
        assertThat(result.discussions().get(0).title()).isEqualTo("Flutter vs React Native in 2026");
        assertThat(result.totalPeople()).isEqualTo(1);
        assertThat(result.totalPosts()).isEqualTo(1);
        assertThat(result.totalEvents()).isEqualTo(1);
        assertThat(result.totalDiscussions()).isEqualTo(1);
    }

    @Test
    void publicSearchResolvesAttachedImageMedia() {
        Pageable pageable = PageRequest.of(0, 10);
        Post post = Post.builder().contentText("Flutter photo")
            .visibility(Visibility.PUBLIC).postType(PostType.IMAGE).user(userEnat).build();
        UUID postId = UUID.randomUUID();
        ReflectionTestUtils.setField(post, "id", postId);
        when(postRepository.searchPublicPosts(eq("photo"), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(post), pageable, 1));
        when(mediaService.resolveAttachedMedia(MediaResourceType.POST, postId)).thenReturn(List.of(
            new MediaService.AttachedMedia(MediaAttachmentRole.IMAGE, 0, "https://cdn.example/photo.jpg")));

        PageResponse<kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse> response =
            searchService.searchPosts("photo", viewerId, pageable);

        assertThat(response.content()).singleElement().extracting(
            kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse::imageUrl)
            .isEqualTo("https://cdn.example/photo.jpg");
    }

    @Test
    @DisplayName("Public event search hides the meeting URL")
    void publicEventSearchHidesMeetingUrl() {
        Pageable pageable = PageRequest.of(0, 20);
        Event event = eventWithMeetingUrl(userEnat);
        when(eventRepository.searchEvents(eq("meet"), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(event), pageable, 1));

        assertThat(searchService.searchEvents("meet", null, pageable).content().get(0).meetingUrl())
            .isNull();
    }

    @Test
    @DisplayName("Authenticated non-attendee event search hides the meeting URL")
    void nonAttendeeEventSearchHidesMeetingUrl() {
        Pageable pageable = PageRequest.of(0, 20);
        Event event = eventWithMeetingUrl(userEnat);
        when(eventRepository.searchEvents(eq("meet"), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(event), pageable, 1));
        when(eventRsvpRepository.findByUserIdAndEventIdIn(eq(viewerId), any()))
            .thenReturn(List.of());

        assertThat(searchService.searchEvents("meet", viewerId, pageable).content().get(0).meetingUrl())
            .isNull();
    }

    @Test
    @DisplayName("RSVP attendee event search shows the meeting URL")
    void attendeeEventSearchShowsMeetingUrl() {
        Pageable pageable = PageRequest.of(0, 20);
        Event event = eventWithMeetingUrl(userEnat);
        when(eventRepository.searchEvents(eq("meet"), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(event), pageable, 1));
        when(eventRsvpRepository.findByUserIdAndEventIdIn(eq(viewerId), any()))
            .thenReturn(List.of(EventRsvp.builder().event(event).status(RsvpStatus.JOINED).build()));

        assertThat(searchService.searchEvents("meet", viewerId, pageable).content().get(0).meetingUrl())
            .isEqualTo("https://meet.example/event");
    }

    @Test
    @DisplayName("Event owner event search shows the meeting URL")
    void ownerEventSearchShowsMeetingUrl() {
        Pageable pageable = PageRequest.of(0, 20);
        ReflectionTestUtils.setField(userTester, "id", viewerId);
        Event event = eventWithMeetingUrl(userTester);
        when(eventRepository.searchEvents(eq("meet"), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(event), pageable, 1));
        when(eventRsvpRepository.findByUserIdAndEventIdIn(eq(viewerId), any()))
            .thenReturn(List.of());

        assertThat(searchService.searchEvents("meet", viewerId, pageable).content().get(0).meetingUrl())
            .isEqualTo("https://meet.example/event");
    }

    @Test
    @DisplayName("Event search returns the published, non-deleted repository scope")
    void eventSearchUsesPublishedVisibilityScope() {
        Pageable pageable = PageRequest.of(0, 20);
        Event event = eventWithMeetingUrl(userEnat);
        when(eventRepository.searchEvents(eq("meet"), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(event), pageable, 1));

        PageResponse<kh.edu.acleda.campuslink.modules.community.dto.response.EventResponse> response =
            searchService.searchEvents("meet", viewerId, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).status()).isEqualTo(EventStatus.PUBLISHED);
    }

    private Event eventWithMeetingUrl(User organizer) {
        Event event = Event.builder()
            .title("Meeting Event")
            .description("Event with a protected URL")
            .status(EventStatus.PUBLISHED)
            .mode(EventMode.ONLINE)
            .meetingUrl("https://meet.example/event")
            .dateTime(LocalDateTime.now().plusDays(2))
            .endDateTime(LocalDateTime.now().plusDays(2).plusHours(1))
            .attendeeCount(0)
            .organizer(organizer)
            .build();
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        return event;
    }
}

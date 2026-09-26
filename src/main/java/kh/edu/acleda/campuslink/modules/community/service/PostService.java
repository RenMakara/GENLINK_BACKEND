package kh.edu.acleda.campuslink.modules.community.service;

import kh.edu.acleda.campuslink.modules.community.dto.request.PollCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.PollVoteRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.PostCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.PostReportRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.RepostRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.PollResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostLikerResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.ReportedPostResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PostService {

    PostResponse create(PostCreateRequest request);

    /** Creates a POLL post: a post carrying a question and its options. */
    PostResponse createPollPost(PollCreateRequest request);

    /**
     * Casts or moves the caller's single vote. Re-voting a different option moves the vote
     * (adjusting both tallies); re-voting the same option is a no-op. @return the poll afterwards.
     */
    PollResponse vote(UUID postId, PollVoteRequest request);

    /**
     * The personalised feed, newest first: public posts, the viewer's own, friends-only
     * posts from mutual follows, and community posts from communities they are in.
     */
    PageResponse<PostResponse> getFeed(Pageable pageable);

    PageResponse<PostResponse> getCommunityFeed(UUID communityId, Pageable pageable);

    PageResponse<PostResponse> getMine(Pageable pageable);

    /** Returns the caller's reposted posts without adding them to the main feed. */
    PageResponse<PostResponse> getReposts(Pageable pageable);

    PostResponse getById(UUID id);

    /** Creates or returns the caller's existing repost of the post. */
    PostResponse repost(UUID postId, RepostRequest request);

    /** Removes the caller's repost if one exists. */
    PostResponse unrepost(UUID postId);

    PostResponse update(UUID id, PostCreateRequest request);

    void delete(UUID id);

    /**
     * Removes a post as a community moderator. Allowed to OWNER / ADMIN / MODERATOR of the
     * post's community; distinct from {@link #delete} which is author-only. Soft delete.
     */
    void removeAsModerator(UUID postId);

    /** Flags a post for moderator review. Idempotent per user - one report per person. */
    void report(UUID postId, PostReportRequest request);

    /** A community moderator's queue of reported posts in that community, most-reported first. */
    PageResponse<ReportedPostResponse> reportedPosts(UUID communityId, Pageable pageable);

    /** Clears a post's reports without removing it. OWNER / ADMIN / MODERATOR of its community. */
    void dismissReports(UUID postId);

    /** Idempotent: backs a UI toggle. @return the post's like count afterwards. */
    int like(UUID postId);

    int unlike(UUID postId);

    /** List of users who liked the post. */
    PageResponse<PostLikerResponse> getLikers(UUID postId, Pageable pageable);
}

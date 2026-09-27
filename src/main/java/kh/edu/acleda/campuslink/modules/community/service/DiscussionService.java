package kh.edu.acleda.campuslink.modules.community.service;

import kh.edu.acleda.campuslink.modules.community.dto.request.CommentCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.DiscussionCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.DiscussionReportRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.RepostRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommentResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.DiscussionResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.ReportedDiscussionResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface DiscussionService {

    DiscussionResponse create(DiscussionCreateRequest request);

    /** The forum list, newest first. Optionally filtered by category. */
    PageResponse<DiscussionResponse> browse(String category, Pageable pageable);

    /** The caller's own discussions, including ones they posted anonymously. */
    PageResponse<DiscussionResponse> getMine(Pageable pageable);

    /**
     * Discussions reposted by the requested profile. When no profile id is supplied,
     * this returns the caller's own reposts.
     */
    PageResponse<DiscussionResponse> getReposts(UUID userId, Pageable pageable);

    DiscussionResponse getById(UUID discussionId);

    DiscussionResponse update(UUID discussionId, DiscussionCreateRequest request);

    void delete(UUID discussionId);

    /** Idempotent: backs a toggle. @return the reaction count afterwards. */
    int react(UUID discussionId);

    int unreact(UUID discussionId);

    /** Creates or returns the caller's existing discussion repost. */
    void repost(UUID discussionId, RepostRequest request);

    /** Removes the caller's discussion repost if one exists. */
    void unrepost(UUID discussionId);

    /** One report per user per discussion, so the count means distinct people. */
    void report(UUID discussionId, DiscussionReportRequest request);

    /** Adds a comment, or a reply when the request carries a parentCommentId (one level deep). */
    CommentResponse addComment(UUID discussionId, CommentCreateRequest request);

    /** Top-level comments only; replies come from {@link #replies}. */
    PageResponse<CommentResponse> comments(UUID discussionId, Pageable pageable);

    /** Replies to one comment. */
    PageResponse<CommentResponse> replies(UUID parentCommentId, Pageable pageable);

    /** Idempotent: backs a toggle. @return the comment's like count afterwards. */
    int likeComment(UUID commentId);

    int unlikeComment(UUID commentId);

    void deleteComment(UUID commentId);

    // ---- Admin moderation (ROLE_ADMIN, enforced at the controller) ----

    /**
     * The moderation queue: reported discussions, most-reported first. Reveals the author
     * even for anonymous discussions, because moderation needs accountability.
     */
    PageResponse<ReportedDiscussionResponse> reportedDiscussions(Pageable pageable);

    /** Removes any discussion (soft delete), regardless of author. */
    void removeAsAdmin(UUID discussionId);

    /** Clears a discussion's reports without removing it. */
    void dismissReports(UUID discussionId);
}

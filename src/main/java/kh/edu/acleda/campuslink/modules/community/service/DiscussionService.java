package kh.edu.acleda.campuslink.modules.community.service;

import kh.edu.acleda.campuslink.modules.community.dto.request.CommentCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.DiscussionCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.DiscussionReportRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommentResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.DiscussionResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface DiscussionService {

    DiscussionResponse create(DiscussionCreateRequest request);

    /** The forum list, newest first. Optionally filtered by category. */
    PageResponse<DiscussionResponse> browse(String category, Pageable pageable);

    /** The caller's own discussions, including ones they posted anonymously. */
    PageResponse<DiscussionResponse> getMine(Pageable pageable);

    DiscussionResponse getById(UUID discussionId);

    DiscussionResponse update(UUID discussionId, DiscussionCreateRequest request);

    void delete(UUID discussionId);

    /** Idempotent: backs a toggle. @return the reaction count afterwards. */
    int react(UUID discussionId);

    int unreact(UUID discussionId);

    /** One report per user per discussion, so the count means distinct people. */
    void report(UUID discussionId, DiscussionReportRequest request);

    CommentResponse addComment(UUID discussionId, CommentCreateRequest request);

    PageResponse<CommentResponse> comments(UUID discussionId, Pageable pageable);

    void deleteComment(UUID commentId);
}

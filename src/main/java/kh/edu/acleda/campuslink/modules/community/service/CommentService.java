package kh.edu.acleda.campuslink.modules.community.service;

import kh.edu.acleda.campuslink.modules.community.dto.request.CommentCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommentResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CommentService {

    CommentResponse add(UUID postId, CommentCreateRequest request);

    /** Top-level comments, newest first. Replies are fetched per parent. */
    PageResponse<CommentResponse> list(UUID postId, Pageable pageable);

    PageResponse<CommentResponse> replies(UUID parentCommentId, Pageable pageable);

    void delete(UUID commentId);
}

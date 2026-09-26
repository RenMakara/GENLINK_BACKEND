package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.Size;

/** Optional text attached to a repost. */
public record RepostRequest(
    @Size(max = 2000) String caption
) {
}

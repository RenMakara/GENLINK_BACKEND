package kh.edu.acleda.campuslink.modules.profile.dto.response;

import java.util.UUID;

/** @param name already resolved to the caller's language; the client never picks */
public record InterestResponse(
    UUID interestId,
    String code,
    String name,
    String category
) {
}

package kh.edu.acleda.campuslink.modules.community.dto.response;

import kh.edu.acleda.campuslink.modules.community.enums.ChatRole;

/** A room member with their role. */
public record ChatMemberResponse(
    ChatParticipant user,
    ChatRole role
) {
}

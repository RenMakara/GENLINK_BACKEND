package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.util.UUID;

/** Public-safe identity of a chat participant — no email or phone. */
public record ChatParticipant(
    UUID userId,
    String fullName,
    String username,
    String avatarUrl
) {
}

package kh.edu.acleda.campuslink.modules.admin.user.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Staff-only note projection; never used by a mobile controller. */
public record AdminUserNoteResponse(
    UUID noteId,
    UUID userId,
    UUID adminUserId,
    String note,
    Instant createdAt
) {
}

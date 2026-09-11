package kh.edu.acleda.campuslink.modules.admin.notification.dto.response;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.request.AdminBroadcastAudience;

/** Result of accepting a broadcast for after-commit notification fan-out. */
public record AdminBroadcastResponse(
    UUID batchId,
    String templateKey,
    AdminBroadcastAudience audience,
    int recipientCount,
    int maxRecipients
) {
}

package kh.edu.acleda.campuslink.modules.admin.user.dto.response;

import java.util.List;
import java.util.UUID;

/** Fixed C-3 response shared by every Admin bulk endpoint. */
public record AdminBulkActionResponse(
    UUID batchId,
    int requested,
    int succeeded,
    List<FailedUser> failed
) {

    public AdminBulkActionResponse {
        failed = failed == null ? List.of() : List.copyOf(failed);
    }

    public record FailedUser(UUID id, String errorCode) {
    }
}

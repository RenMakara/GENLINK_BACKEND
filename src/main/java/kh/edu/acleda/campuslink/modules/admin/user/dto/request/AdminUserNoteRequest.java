package kh.edu.acleda.campuslink.modules.admin.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Internal staff note text; notes are append-only and have no separate reason field. */
public record AdminUserNoteRequest(
    @NotBlank
    @Size(max = 4_000)
    String note
) {
}

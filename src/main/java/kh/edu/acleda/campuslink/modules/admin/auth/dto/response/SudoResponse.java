package kh.edu.acleda.campuslink.modules.admin.auth.dto.response;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The window that was just opened.
 *
 * @param expiresInSeconds always 300. Returned so the confirm dialog can show a countdown
 *     rather than discovering the window closed only when the action is refused.
 * @param expiresAt UTC, like every timestamp the API emits (SSOT §9.5).
 */
public record SudoResponse(
    @Schema(description = "Sudo window length. Always 300.", example = "300")
    long expiresInSeconds,
    @Schema(description = "UTC instant the window closes", example = "2026-08-08T02:11:00Z")
    Instant expiresAt
) {
}

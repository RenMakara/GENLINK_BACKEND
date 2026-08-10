package kh.edu.acleda.campuslink.modules.admin.auth.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Who the caller is and what they may do - the single call the portal's permission model rests
 * on (SSOT §8.1).
 *
 * <p>The sidebar, every {@code usePermission} check and every disabled button read from this.
 * That makes it the one response where being wrong is worse than being absent: a permission
 * listed here but not actually held shows an admin a button that fails when they press it,
 * which reads as a broken tool rather than a boundary.
 *
 * @param permissions codes such as {@code user:read}, without the {@code PERM_} prefix that
 *     Spring Security uses internally. Resolved from the caller's own granted authorities, so
 *     it cannot drift from what authorization enforces.
 * @param lastLoginAt UTC, like every timestamp the API emits (SSOT §9.5). The portal renders it
 *     in Asia/Phnom_Penh.
 */
public record AdminMeResponse(
    @Schema(description = "Admin profile id, not the user id", example = "2c9f077e-6e35-49ee-944b-235168f07372")
    UUID adminUserId,
    @Schema(description = "Sign-in address", example = "admin@genlink.kh")
    String email,
    @Schema(description = "Display name", example = "Makara Ren")
    String fullName,
    @Schema(description = "SUPER_ADMIN | ADMIN | MODERATOR | SUPPORT | CONTENT_MANAGER | FINANCE_ADMIN | ANALYTICS_VIEWER", example = "MODERATOR")
    String roleCode,
    @Schema(description = "Human-readable role name", example = "Moderator")
    String roleName,
    @Schema(description = "Resolved permission codes, PERM_ prefix stripped", example = "[\"user:read\", \"user:ban\"]")
    List<String> permissions,
    @Schema(description = "UTC. The portal renders it in Asia/Phnom_Penh.", example = "2026-08-07T20:06:39Z")
    Instant lastLoginAt,
    @Schema(description = "Whether two-factor is active on this account", example = "true")
    boolean totpEnabled
) {
}

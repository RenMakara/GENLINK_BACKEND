package kh.edu.acleda.campuslink.modules.admin.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Permission-gated HTTP boundary for account-recovery actions (ADM-080). */
@Tag(name = "Admin - User Recovery", description = "Audited user account-recovery actions")
@RestController
@RequestMapping("/api/v1/admin/users/{userId}")
@RequiredArgsConstructor
public class AdminUserRecoveryController {

    private final AdminUserRecoveryService adminUserRecoveryService;

    @Operation(
        summary = "Verify a user's email",
        description = "Marks the user's email verified and activates a PENDING account.")
    @PostMapping("/verify-email")
    @PreAuthorize("hasAuthority('PERM_user:verify_email')")
    public ApiResponse<Void> verifyEmail(@PathVariable UUID userId) {
        adminUserRecoveryService.verifyEmail(userId);
        return ApiResponse.success("User email verified", null);
    }

    @Operation(
        summary = "Request a user's password reset",
        description = "Starts the existing password-reset email flow; the admin never sees a password.")
    @PostMapping("/reset-password")
    @PreAuthorize("hasAuthority('PERM_user:reset_password')")
    public ApiResponse<Void> resetPassword(@PathVariable UUID userId) {
        adminUserRecoveryService.resetPassword(userId);
        return ApiResponse.success("Password reset email requested", null);
    }

    @Operation(
        summary = "Force a user logout",
        description = "Revokes all refresh sessions and invalidates existing access tokens.")
    @PostMapping("/force-logout")
    @PreAuthorize("hasAuthority('PERM_user:force_logout')")
    public ApiResponse<Void> forceLogout(@PathVariable UUID userId) {
        adminUserRecoveryService.forceLogout(userId);
        return ApiResponse.success("User sessions revoked", null);
    }
}

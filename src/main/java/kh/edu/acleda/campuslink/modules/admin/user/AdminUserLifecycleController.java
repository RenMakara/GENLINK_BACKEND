package kh.edu.acleda.campuslink.modules.admin.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.auth.RequiresSudo;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserLifecycleRequest;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Permission- and sudo-gated HTTP boundary for account lifecycle actions (ADM-081). */
@Tag(name = "Admin - User Lifecycle", description = "Audited user deletion and restoration")
@RestController
@RequestMapping("/api/v1/admin/users/{userId}")
@RequiredArgsConstructor
@RequiresSudo
public class AdminUserLifecycleController {

    private final AdminUserLifecycleService adminUserLifecycleService;

    @Operation(
        summary = "Soft-delete a user",
        description = "Marks the account DELETED, preserves the row, revokes sessions and starts the anonymisation grace period.")
    @DeleteMapping
    @PreAuthorize("hasAuthority('PERM_user:delete')")
    public ApiResponse<Void> delete(
        @PathVariable UUID userId,
        @Valid @RequestBody AdminUserLifecycleRequest request) {
        adminUserLifecycleService.delete(userId, request);
        return ApiResponse.success("User account deleted", null);
    }

    @Operation(
        summary = "Restore a soft-deleted user",
        description = "Clears deletion metadata and restores the status captured before deletion. Anonymised accounts cannot be restored.")
    @PostMapping("/restore")
    @PreAuthorize("hasAuthority('PERM_user:delete')")
    public ApiResponse<Void> restore(
        @PathVariable UUID userId,
        @Valid @RequestBody AdminUserLifecycleRequest request) {
        adminUserLifecycleService.restore(userId, request);
        return ApiResponse.success("User account restored", null);
    }
}

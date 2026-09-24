package kh.edu.acleda.campuslink.modules.admin.sanction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.request.AdminUserSanctionRequest;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.request.AdminUserSuspendRequest;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Permission-gated HTTP boundary for Ban, Unban and Suspend actions (ADM-078). */
@Tag(name = "Admin - Users", description = "Audited user-enforcement actions")
@RestController
@RequestMapping("/api/v1/admin/users/{userId}")
@RequiredArgsConstructor
public class AdminUserSanctionController {

    private final AdminSanctionService adminSanctionService;

    @Operation(
        summary = "Ban a user",
        description = "Permanently bans the account, revokes active sessions and records the reason.")
    @PostMapping("/ban")
    @PreAuthorize("hasAuthority('PERM_user:ban')")
    public ApiResponse<Void> ban(
        @PathVariable UUID userId,
        @Valid @RequestBody AdminUserSanctionRequest request) {
        adminSanctionService.ban(userId, request);
        return ApiResponse.success("User banned", null);
    }

    @Operation(
        summary = "Unban a user",
        description = "Lifts the open ban and restores the account status captured before it.")
    @PostMapping("/unban")
    @PreAuthorize("hasAuthority('PERM_user:unban')")
    public ApiResponse<Void> unban(
        @PathVariable UUID userId,
        @Valid @RequestBody AdminUserSanctionRequest request) {
        adminSanctionService.unban(userId, request);
        return ApiResponse.success("User unbanned", null);
    }

    @Operation(
        summary = "Suspend a user",
        description = "Suspends the account for 24 hours, 7 days or 30 days and revokes active sessions.")
    @PostMapping("/suspend")
    @PreAuthorize("hasAuthority('PERM_user:suspend')")
    public ApiResponse<Void> suspend(
        @PathVariable UUID userId,
        @Valid @RequestBody AdminUserSuspendRequest request) {
        adminSanctionService.suspend(userId, request);
        return ApiResponse.success("User suspended", null);
    }
}

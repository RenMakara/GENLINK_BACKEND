package kh.edu.acleda.campuslink.modules.admin.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.auth.RequiresSudo;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminBulkBadgeRequest;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminBulkBanRequest;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminBulkNotifyRequest;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminBulkActionResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Permission- and sudo-gated partial-success actions over selected users (ADM-086). */
@Tag(name = "Admin - User Bulk Actions", description = "Audited bounded bulk user actions")
@RestController
@RequestMapping("/api/v1/admin/users/bulk")
@RequiredArgsConstructor
public class AdminUserBulkController {

    private final AdminUserBulkService adminUserBulkService;

    @Operation(
        summary = "Ban selected users",
        description = "Applies the ban independently to up to 500 users and returns per-user failures. "
            + "Requires a fresh sudo window.")
    @PostMapping("/ban")
    @RequiresSudo
    @PreAuthorize("hasAuthority('PERM_user:ban')")
    public ApiResponse<AdminBulkActionResponse> ban(
        @Valid @RequestBody AdminBulkBanRequest request) {
        return ApiResponse.success("Bulk ban completed", adminUserBulkService.ban(request));
    }

    @Operation(
        summary = "Notify selected users",
        description = "Queues one in-app notification per eligible user and reports failures individually.")
    @PostMapping("/notify")
    @PreAuthorize("hasAuthority('PERM_notification:send')")
    public ApiResponse<AdminBulkActionResponse> notify(
        @Valid @RequestBody AdminBulkNotifyRequest request) {
        return ApiResponse.success("Bulk notification completed", adminUserBulkService.notify(request));
    }

    @Operation(
        summary = "Assign a badge to selected users",
        description = "Assigns one active catalogue badge independently to up to 500 users.")
    @PostMapping("/badges")
    @PreAuthorize("hasAuthority('PERM_badge:assign')")
    public ApiResponse<AdminBulkActionResponse> assignBadge(
        @Valid @RequestBody AdminBulkBadgeRequest request) {
        return ApiResponse.success(
            "Bulk badge assignment completed", adminUserBulkService.assignBadge(request));
    }
}

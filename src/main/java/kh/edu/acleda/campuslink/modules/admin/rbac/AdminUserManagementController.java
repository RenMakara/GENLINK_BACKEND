package kh.edu.acleda.campuslink.modules.admin.rbac;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.auth.RequiresSudo;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminDeactivationRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminPromotionRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminRoleChangeRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.response.AdminStaffResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin staff directory and lifecycle boundary for ADM-103. */
@Tag(name = "Admin - Staff", description = "Promote, list, re-role and deactivate staff")
@RestController
@RequestMapping("/api/v1/admin/admins")
@RequiredArgsConstructor
public class AdminUserManagementController {

    private final AdminUserManagementService adminUserManagementService;

    @Operation(
        summary = "List admin users",
        description = "Returns safe staff projections, including active and revoked profiles. "
            + "Credential material is never returned.")
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_rbac:read')")
    public ApiResponse<PageResponse<AdminStaffResponse>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size) {
        return ApiResponse.success(
            "Admin users retrieved", adminUserManagementService.list(page, size));
    }

    @Operation(
        summary = "Promote a user to admin",
        description = "Promotes an existing active, email-verified user and starts with TOTP "
            + "unconfirmed. Requires sudo step-up authentication.")
    @PostMapping
    @RequiresSudo
    @PreAuthorize("hasAuthority('PERM_rbac:manage')")
    public ApiResponse<AdminStaffResponse> promote(
        @Valid @RequestBody AdminPromotionRequest request) {
        return ApiResponse.success(
            "User promoted to admin", adminUserManagementService.promote(request));
    }

    @Operation(
        summary = "Change an admin's role",
        description = "Changes the target role and immediately revokes the target's sessions. "
            + "Self-change and last-SUPER_ADMIN guards apply.")
    @PutMapping("/{adminId}/role")
    @RequiresSudo
    @PreAuthorize("hasAuthority('PERM_rbac:manage')")
    public ApiResponse<AdminStaffResponse> changeRole(
        @PathVariable UUID adminId,
        @Valid @RequestBody AdminRoleChangeRequest request) {
        return ApiResponse.success(
            "Admin role changed", adminUserManagementService.changeRole(adminId, request));
    }

    @Operation(
        summary = "Deactivate an admin",
        description = "Revokes admin access, restores the linked user's coarse USER role, and "
            + "immediately revokes all target sessions. Requires sudo step-up authentication.")
    @PostMapping("/{adminId}/deactivate")
    @RequiresSudo
    @PreAuthorize("hasAuthority('PERM_rbac:manage')")
    public ApiResponse<AdminStaffResponse> deactivate(
        @PathVariable UUID adminId,
        @Valid @RequestBody AdminDeactivationRequest request) {
        return ApiResponse.success(
            "Admin deactivated", adminUserManagementService.deactivate(adminId, request));
    }
}

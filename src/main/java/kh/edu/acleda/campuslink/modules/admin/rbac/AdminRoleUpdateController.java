package kh.edu.acleda.campuslink.modules.admin.rbac;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.auth.RequiresSudo;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminRolePermissionUpdateRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminRoleUpdateRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.response.AdminRoleDetailResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Permission- and sudo-gated role mutation boundary for the Admin portal (ADM-102). */
@Tag(name = "Admin - Roles", description = "Role metadata and permission updates")
@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
@RequiresSudo
public class AdminRoleUpdateController {

    private final AdminRoleUpdateService adminRoleUpdateService;

    @Operation(
        summary = "Replace a role's permissions",
        description = "Replaces the complete permission set in one audited, idempotent operation. "
            + "SUPER_ADMIN permissions are implicit and cannot be edited.")
    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('PERM_rbac:manage')")
    public ApiResponse<AdminRoleDetailResponse> replacePermissions(
        @PathVariable UUID roleId,
        @Valid @RequestBody AdminRolePermissionUpdateRequest request) {
        return ApiResponse.success(
            "Admin role permissions updated",
            adminRoleUpdateService.replacePermissions(roleId, request));
    }

    @Operation(
        summary = "Update role metadata",
        description = "Renames or re-describes a role while preserving its stable code and system flag.")
    @PutMapping("/{roleId}")
    @PreAuthorize("hasAuthority('PERM_rbac:manage')")
    public ApiResponse<AdminRoleDetailResponse> updateMetadata(
        @PathVariable UUID roleId,
        @Valid @RequestBody AdminRoleUpdateRequest request) {
        return ApiResponse.success(
            "Admin role updated", adminRoleUpdateService.updateMetadata(roleId, request));
    }
}

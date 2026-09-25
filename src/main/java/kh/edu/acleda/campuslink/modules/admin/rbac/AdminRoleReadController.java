package kh.edu.acleda.campuslink.modules.admin.rbac;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.response.AdminPermissionGroupResponse;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.response.AdminRoleDetailResponse;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.response.AdminRoleSummaryResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Permission-gated read boundary for roles and the enum-backed permission catalogue. */
@Tag(name = "Admin - Roles", description = "Roles and permission catalogue")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminRoleReadController {

    private final AdminRoleReadService adminRoleReadService;

    @Operation(
        summary = "List admin roles",
        description = "Returns role metadata and active admin counts. Secret admin credentials "
            + "and authentication material are never included.")
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('PERM_rbac:read')")
    public ApiResponse<List<AdminRoleSummaryResponse>> listRoles() {
        return ApiResponse.success("Admin roles retrieved", adminRoleReadService.listRoles());
    }

    @Operation(
        summary = "Get an admin role",
        description = "Returns one role and its effective permission codes. SUPER_ADMIN is "
            + "resolved from the Permission enum because it has no stored grants.")
    @GetMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('PERM_rbac:read')")
    public ApiResponse<AdminRoleDetailResponse> getRole(@PathVariable UUID roleId) {
        return ApiResponse.success("Admin role retrieved", adminRoleReadService.getRole(roleId));
    }

    @Operation(
        summary = "List the permission catalogue",
        description = "Returns the Permission enum grouped by resource. There is no permissions "
            + "database table.")
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('PERM_rbac:read')")
    public ApiResponse<List<AdminPermissionGroupResponse>> listPermissions() {
        return ApiResponse.success(
            "Admin permission catalogue retrieved", adminRoleReadService.listPermissions());
    }
}

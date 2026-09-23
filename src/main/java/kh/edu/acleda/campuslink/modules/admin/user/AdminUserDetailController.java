package kh.edu.acleda.campuslink.modules.admin.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserDetailResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Permission-gated detail boundary for the admin user directory. */
@Tag(name = "Admin - Users", description = "User profile and account details")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserDetailController {

    private final AdminUserDetailService adminUserDetailService;

    @Operation(
        summary = "Get an admin user detail",
        description = "Returns the profile and account projection needed by the admin detail page. "
            + "Credentials and provider identifiers are never returned.")
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('PERM_user:read')")
    public ApiResponse<AdminUserDetailResponse> get(@PathVariable UUID userId) {
        return ApiResponse.success("Admin user retrieved", adminUserDetailService.get(userId));
    }
}

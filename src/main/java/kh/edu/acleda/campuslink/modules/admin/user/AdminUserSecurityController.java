package kh.edu.acleda.campuslink.modules.admin.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserLoginHistoryResponse;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserSessionResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Permission-gated read boundary for one user's sessions and security history. */
@Tag(name = "Admin - User Security", description = "User sessions and login history")
@RestController
@RequestMapping("/api/v1/admin/users/{userId}")
@RequiredArgsConstructor
public class AdminUserSecurityController {

    private final AdminUserSecurityService adminUserSecurityService;

    @Operation(
        summary = "List a user's active sessions",
        description = "Returns active, non-revoked and non-expired sessions. Token hashes are never returned.")
    @GetMapping("/sessions")
    @PreAuthorize("hasAuthority('PERM_user:read')")
    public ApiResponse<PageResponse<AdminUserSessionResponse>> sessions(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size) {
        return ApiResponse.success(
            "User sessions retrieved", adminUserSecurityService.sessions(userId, page, size));
    }

    @Operation(
        summary = "List a user's login history",
        description = "Returns newest-first security audit events scoped to the target user.")
    @GetMapping("/login-history")
    @PreAuthorize("hasAuthority('PERM_user:read')")
    public ApiResponse<PageResponse<AdminUserLoginHistoryResponse>> loginHistory(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size) {
        return ApiResponse.success(
            "User login history retrieved", adminUserSecurityService.loginHistory(userId, page, size));
    }
}

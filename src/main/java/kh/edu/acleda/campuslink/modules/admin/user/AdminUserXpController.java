package kh.edu.acleda.campuslink.modules.admin.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserXpResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Permission-gated read boundary for one user's XP and earned badges. */
@Tag(name = "Admin - User XP", description = "User XP, level, ledger and badges")
@RestController
@RequestMapping("/api/v1/admin/users/{userId}")
@RequiredArgsConstructor
public class AdminUserXpController {

    private final AdminUserXpService adminUserXpService;

    @Operation(
        summary = "Get a user's XP detail",
        description = "Returns the current level, source breakdown, paginated newest-first ledger "
            + "and earned badges. Signed XP entries are preserved.")
    @GetMapping("/xp")
    @PreAuthorize("hasAuthority('PERM_user:read')")
    public ApiResponse<AdminUserXpResponse> get(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size) {
        return ApiResponse.success("User XP detail retrieved", adminUserXpService.get(userId, page, size));
    }
}

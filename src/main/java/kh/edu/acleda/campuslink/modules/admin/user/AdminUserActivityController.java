package kh.edu.acleda.campuslink.modules.admin.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserActivityResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Permission-gated read boundary for one user's cross-domain activity counts. */
@Tag(name = "Admin - User Activity", description = "Cross-domain user activity counts")
@RestController
@RequestMapping("/api/v1/admin/users/{userId}")
@RequiredArgsConstructor
public class AdminUserActivityController {

    private final AdminUserActivityService adminUserActivityService;

    @Operation(
        summary = "Get a user's activity counts",
        description = "Returns posts, comments, active community memberships, live-event RSVPs, "
            + "and filed/received report counts without exposing domain entities.")
    @GetMapping("/activity")
    @PreAuthorize("hasAuthority('PERM_user:read')")
    public ApiResponse<AdminUserActivityResponse> get(@PathVariable UUID userId) {
        return ApiResponse.success(
            "User activity counts retrieved", adminUserActivityService.get(userId));
    }
}

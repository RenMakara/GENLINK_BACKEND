package kh.edu.acleda.campuslink.modules.admin.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserXpAdjustmentRequest;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserXpAdjustmentResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Permission-gated HTTP boundary for signed XP adjustments (ADM-082). */
@Tag(name = "Admin - User XP", description = "Audited XP grants and revocations")
@RestController
@RequestMapping("/api/v1/admin/users/{userId}")
@RequiredArgsConstructor
public class AdminUserXpAdjustmentController {

    private final AdminUserXpAdjustmentService adminUserXpAdjustmentService;

    @Operation(
        summary = "Adjust a user's XP",
        description = "Positive amounts grant XP and negative amounts revoke XP. The shared reward "
            + "pipeline recomputes level, writes a signed ledger entry and evaluates badges.")
    @PostMapping("/xp")
    @PreAuthorize("hasAnyAuthority('PERM_xp:grant', 'PERM_xp:revoke')")
    public ApiResponse<AdminUserXpAdjustmentResponse> adjust(
        @PathVariable UUID userId,
        @Valid @RequestBody AdminUserXpAdjustmentRequest request) {
        return ApiResponse.success("User XP adjusted", adminUserXpAdjustmentService.adjust(userId, request));
    }
}

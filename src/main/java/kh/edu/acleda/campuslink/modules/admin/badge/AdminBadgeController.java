package kh.edu.acleda.campuslink.modules.admin.badge;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kh.edu.acleda.campuslink.modules.admin.badge.dto.response.AdminBadgeResponse;
import kh.edu.acleda.campuslink.modules.entertainment.repository.BadgeRepository;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Safe badge catalogue projection for permission-gated Admin actions (ADM-098). */
@Tag(name = "Admin - Badges", description = "Badge catalogue projections for staff actions")
@RestController
@RequestMapping("/api/v1/admin/badges")
@RequiredArgsConstructor
public class AdminBadgeController {

    private final BadgeRepository badgeRepository;

    @Operation(
        summary = "List assignable badges",
        description = "Returns active badge metadata only; earned state is not exposed to Admin.")
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_badge:assign')")
    public ApiResponse<List<AdminBadgeResponse>> list() {
        List<AdminBadgeResponse> badges = badgeRepository
            .findByDeletedAtIsNullOrderByCreatedAtAsc()
            .stream()
            .map(badge -> new AdminBadgeResponse(
                badge.getId(),
                badge.getNameEn(),
                badge.getNameKm(),
                badge.getDescriptionEn(),
                badge.getDescriptionKm(),
                badge.getRequirement(),
                badge.getIconUrl()))
            .toList();
        return ApiResponse.success("Admin badge catalogue retrieved", badges);
    }
}

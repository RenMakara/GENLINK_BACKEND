package kh.edu.acleda.campuslink.modules.admin.sanction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.request.AdminSanctionListCriteria;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.response.AdminSanctionResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only per-user enforcement timeline (ADM-084). */
@Tag(name = "Admin - Users", description = "User enforcement history")
@RestController
@RequestMapping("/api/v1/admin/users/{userId}/sanctions")
@RequiredArgsConstructor
public class AdminUserSanctionReadController {

    private final AdminSanctionQueryService adminSanctionQueryService;

    @Operation(
        summary = "List a user's sanctions",
        description = "Returns warnings, suspensions and bans in one chronological timeline.")
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_user:read')")
    public ApiResponse<PageResponse<AdminSanctionResponse>> list(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam MultiValueMap<String, String> queryParams) {
        AdminSanctionListCriteria criteria = AdminSanctionListCriteria.fromRequest(
            joinValues(queryParams, "type"),
            null,
            null,
            from,
            to,
            false,
            page,
            size);
        return ApiResponse.success(
            "User sanctions retrieved", adminSanctionQueryService.listForUser(userId, criteria));
    }

    private static String joinValues(MultiValueMap<String, String> queryParams, String name) {
        List<String> values = queryParams.get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        StringJoiner joined = new StringJoiner(",");
        values.forEach(joined::add);
        return joined.toString();
    }
}

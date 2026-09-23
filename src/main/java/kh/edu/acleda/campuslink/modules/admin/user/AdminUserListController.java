package kh.edu.acleda.campuslink.modules.admin.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.StringJoiner;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserListCriteria;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserSummaryResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Permission-gated list boundary for the admin user directory. */
@Tag(name = "Admin - Users", description = "Search, filter and paginate user records")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserListController {

    private final AdminUserListService adminUserListService;

    @Operation(
        summary = "List users for the admin portal",
        description = "Returns a safe, paginated user summary. Optional filters are evaluated in "
            + "the database and soft-deleted users are excluded by default.")
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_user:read')")
    public ApiResponse<PageResponse<AdminUserSummaryResponse>> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Boolean emailVerified,
        @RequestParam(required = false) String university,
        @RequestParam(required = false) Integer levelMin,
        @RequestParam(required = false) Integer levelMax,
        @RequestParam(required = false) Integer xpMin,
        @RequestParam(required = false) Integer xpMax,
        @RequestParam(required = false) LocalDate createdFrom,
        @RequestParam(required = false) LocalDate createdTo,
        @RequestParam(required = false) String lastActive,
        @RequestParam(defaultValue = "false") boolean banned,
        @RequestParam(defaultValue = "false") boolean includeDeleted,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size,
        @RequestParam MultiValueMap<String, String> queryParams) {
        AdminUserListCriteria criteria = AdminUserListCriteria.fromRequest(
            q, joinValues(queryParams, "status"), emailVerified,
            joinValues(queryParams, "workStatus"), university,
            joinValues(queryParams, "gender"), joinValues(queryParams, "language"),
            joinValues(queryParams, "authProvider"),
            levelMin, levelMax, xpMin, xpMax, createdFrom, createdTo, lastActive, banned,
            includeDeleted, page, size, queryParams.get("sort"));
        return ApiResponse.success("Admin users retrieved", adminUserListService.list(criteria));
    }

    /** Preserves both comma-separated and repeated values before Criteria parsing. */
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

package kh.edu.acleda.campuslink.modules.admin.sanction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.request.AdminSanctionListCriteria;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.request.AdminUserSanctionRequest;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.response.AdminSanctionResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Global Enforcement list and id-based sanction lifting (ADM-084). */
@Tag(name = "Admin - Enforcement", description = "Global sanction history and lift actions")
@RestController
@RequestMapping("/api/v1/admin/sanctions")
@RequiredArgsConstructor
public class AdminSanctionController {

    private final AdminSanctionQueryService adminSanctionQueryService;
    private final AdminSanctionService adminSanctionService;

    @Operation(
        summary = "List sanctions globally",
        description = "Returns paginated warnings, suspensions and bans with optional enforcement filters.")
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_user:warn')")
    public ApiResponse<PageResponse<AdminSanctionResponse>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(defaultValue = "false") boolean activeOnly,
        @RequestParam MultiValueMap<String, String> queryParams) {
        AdminSanctionListCriteria criteria = AdminSanctionListCriteria.fromRequest(
            joinValues(queryParams, "type"),
            joinValues(queryParams, "severity"),
            firstValue(queryParams, "adminId", "admin"),
            from,
            to,
            activeOnly,
            page,
            size);
        return ApiResponse.success("Sanctions retrieved", adminSanctionQueryService.listGlobal(criteria));
    }

    @Operation(
        summary = "Lift a sanction",
        description = "Lifts a sanction by id and restores the account status captured before it.")
    @PostMapping("/{sanctionId}/lift")
    @PreAuthorize("hasAuthority('PERM_user:unban')")
    public ApiResponse<Void> lift(
        @PathVariable UUID sanctionId,
        @Valid @RequestBody AdminUserSanctionRequest request) {
        adminSanctionService.lift(sanctionId, request);
        return ApiResponse.success("Sanction lifted", null);
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

    private static String firstValue(
        MultiValueMap<String, String> queryParams,
        String primary,
        String alias) {
        String value = queryParams.getFirst(primary);
        return value == null ? queryParams.getFirst(alias) : value;
    }
}

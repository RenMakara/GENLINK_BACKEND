package kh.edu.acleda.campuslink.modules.admin.systemhealth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kh.edu.acleda.campuslink.modules.admin.systemhealth.dto.response.AdminSystemHealthResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Permission-gated health boundary for the admin portal. */
@Tag(name = "Admin - System Health", description = "Normalized backend subsystem health")
@RestController
@RequestMapping("/api/v1/admin/system")
@RequiredArgsConstructor
public class AdminSystemHealthController {

    private final AdminSystemHealthService healthService;

    @Operation(
        summary = "Get admin system health",
        description = "Returns normalized API, database, storage, mail and scheduled-job state. "
            + "Sensitive infrastructure details are never included.")
    @GetMapping("/health")
    @PreAuthorize("hasAuthority('PERM_dashboard:view')")
    public ApiResponse<AdminSystemHealthResponse> health() {
        return ApiResponse.success("Admin system health retrieved", healthService.getHealth());
    }
}

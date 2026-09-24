package kh.edu.acleda.campuslink.modules.admin.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.auth.RequiresSudo;
import kh.edu.acleda.campuslink.modules.admin.rbac.Permission;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserListCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** High-tier, permission-gated CSV download for the filtered Admin user directory (ADM-087). */
@Tag(name = "Admin - Users", description = "Export filtered user records")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserExportController {

    private static final MediaType CSV_UTF_8 = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final AdminUserExportService adminUserExportService;

    @Operation(
        summary = "Export the filtered user list as CSV",
        description = "Streams the same filtered directory view, capped at 50,000 rows. "
            + "Phone and date of birth are masked without user:export:pii.")
    @GetMapping(value = "/export", produces = "text/csv")
    @RequiresSudo
    @org.springframework.security.access.prepost.PreAuthorize(
        "hasAuthority('PERM_user:export')")
    public ResponseEntity<StreamingResponseBody> export(
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
        @RequestParam String reason,
        @RequestParam MultiValueMap<String, String> queryParams,
        Authentication authentication) {
        AdminUserListCriteria criteria = AdminUserListCriteria.fromRequest(
            q, joinValues(queryParams, "status"), emailVerified,
            joinValues(queryParams, "workStatus"), university,
            joinValues(queryParams, "gender"), joinValues(queryParams, "language"),
            joinValues(queryParams, "authProvider"),
            levelMin, levelMax, xpMin, xpMax, createdFrom, createdTo, lastActive, banned,
            includeDeleted, 0, 25, queryParams.get("sort"));
        boolean piiIncluded = hasAuthority(authentication, Permission.USER_EXPORT_PII);
        AdminUserExportPlan plan = adminUserExportService.prepare(criteria, piiIncluded, reason);

        StreamingResponseBody body = output -> adminUserExportService.writeCsv(plan, output);
        return ResponseEntity.ok()
            .contentType(CSV_UTF_8)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"genlink-users.csv\"")
            .body(body);
    }

    private static boolean hasAuthority(Authentication authentication, Permission permission) {
        return authentication != null
            && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(
                    permission.asAuthority().getAuthority()));
    }

    /** Preserves both comma-separated and repeated values before Criteria parsing. */
    private static String joinValues(MultiValueMap<String, String> queryParams, String name) {
        List<String> values = queryParams.get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(",", values);
    }
}
